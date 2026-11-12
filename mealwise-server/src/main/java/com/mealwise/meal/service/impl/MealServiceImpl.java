package com.mealwise.meal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mealwise.common.enums.MealType;
import com.mealwise.common.exception.BizException;
import com.mealwise.common.util.NutritionCalculator;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import com.mealwise.food.service.FoodService;
import com.mealwise.meal.dto.AddItemRequest;
import com.mealwise.meal.dto.CreateMealRequest;
import com.mealwise.meal.dto.UpdateItemRequest;
import com.mealwise.meal.entity.Meal;
import com.mealwise.meal.entity.MealItem;
import com.mealwise.meal.mapper.MealItemMapper;
import com.mealwise.meal.mapper.MealMapper;
import com.mealwise.meal.service.MealService;
import com.mealwise.meal.vo.MealCaloriesVO;
import com.mealwise.meal.vo.MealItemVO;
import com.mealwise.meal.vo.MealVO;
import com.mealwise.meal.vo.NutritionVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 餐次服务实现。
 *
 * <p>三条不变量：
 * <ol>
 *   <li>所有入口先 {@link #requireOwned}，归属校验只有一处，避免漏判；</li>
 *   <li>item 必须属于路径上的 meal，防止"用我的 mealId 改别人的 itemId"；</li>
 *   <li>热量永远实时算，不落库（理由见 MealItem 注释）。</li>
 * </ol>
 */
@Service
public class MealServiceImpl implements MealService {

    private final MealMapper mealMapper;
    private final MealItemMapper itemMapper;
    private final FoodMapper foodMapper;
    private final FoodService foodService;

    public MealServiceImpl(MealMapper mealMapper,
                           MealItemMapper itemMapper,
                           FoodMapper foodMapper,
                           FoodService foodService) {
        this.mealMapper = mealMapper;
        this.itemMapper = itemMapper;
        this.foodMapper = foodMapper;
        this.foodService = foodService;
    }

    @Override
    @Transactional
    public MealVO create(long userId, CreateMealRequest request) {
        MealType type = MealType.parse(request.mealType());
        Meal meal = new Meal();
        meal.setUserId(userId);
        meal.setMealType(type.name());
        meal.setEatenAt(request.eatenAt() != null ? request.eatenAt() : LocalDateTime.now().withNano(0));
        mealMapper.insert(meal);
        return new MealVO(meal.getId(), meal.getMealType(), meal.getEatenAt(), List.of());
    }

    @Override
    @Transactional
    public MealItemVO addItem(long userId, long mealId, AddItemRequest request) {
        requireOwned(userId, mealId);
        Food food = foodService.requireEnabled(request.foodId());

        MealItem item = new MealItem();
        item.setMealId(mealId);
        item.setFoodId(food.getId());
        item.setQuantityG(request.quantityG());
        itemMapper.insert(item);
        // DB default is 0; mirror it on the entity so the client gets the version to send back
        item.setVersion(0);
        return toItemVO(item, food);
    }

    @Override
    @Transactional
    public MealItemVO updateItem(long userId, long mealId, long itemId, UpdateItemRequest request) {
        requireOwned(userId, mealId);
        MealItem item = requireItem(mealId, itemId);

        // 乐观锁：前端带回读取时的版本号，不一致说明这条记录已经被别人改过
        if (request.version() != null && !request.version().equals(item.getVersion())) {
            throw BizException.conflict("该食物项已被修改，请刷新后重试");
        }

        item.setQuantityG(request.quantityG());
        // MP 乐观锁插件会把 version 作为 where 条件；影响行数为 0 说明版本已被他人推进
        int rows = itemMapper.updateById(item);
        if (rows == 0) {
            throw BizException.conflict("该食物项已被修改，请刷新后重试");
        }

        Food food = foodMapper.selectById(item.getFoodId());
        return toItemVO(item, food);
    }

    @Override
    @Transactional
    public void deleteItem(long userId, long mealId, long itemId) {
        requireOwned(userId, mealId);
        requireItem(mealId, itemId);
        itemMapper.deleteById(itemId);
    }

    @Override
    @Transactional
    public void delete(long userId, long mealId) {
        // 归属校验与其它入口共用；外键 ON DELETE CASCADE 会带走 food items
        requireOwned(userId, mealId);
        mealMapper.deleteById(mealId);
    }

    @Override
    public MealCaloriesVO calories(long userId, long mealId) {
        Meal meal = requireOwned(userId, mealId);
        List<MealItem> items = itemsOf(mealId);

        Map<Long, Food> foods = loadFoods(items);
        List<MealItemVO> itemVOs = new ArrayList<>(items.size());
        BigDecimal calories = NutritionCalculator.zero();
        BigDecimal protein = NutritionCalculator.zero();
        BigDecimal fat = NutritionCalculator.zero();
        BigDecimal carb = NutritionCalculator.zero();
        BigDecimal grams = BigDecimal.ZERO.setScale(1);

        for (MealItem item : items) {
            Food food = foods.get(item.getFoodId());
            MealItemVO vo = toItemVO(item, food);
            itemVOs.add(vo);
            calories = NutritionCalculator.plus(calories, vo.calories());
            protein = NutritionCalculator.plus(protein, vo.protein());
            fat = NutritionCalculator.plus(fat, vo.fat());
            carb = NutritionCalculator.plus(carb, vo.carb());
            grams = grams.add(item.getQuantityG());
        }

        return new MealCaloriesVO(
                meal.getId(),
                meal.getMealType(),
                meal.getEatenAt(),
                itemVOs,
                new NutritionVO(calories, protein, fat, carb),
                itemVOs.size(),
                grams.setScale(1, java.math.RoundingMode.HALF_UP));
    }

    @Override
    public Meal requireOwned(long userId, long mealId) {
        Meal meal = mealMapper.selectById(mealId);
        if (meal == null) {
            throw BizException.notFound("餐次不存在");
        }
        if (!Long.valueOf(userId).equals(meal.getUserId())) {
            // 已登录但不是自己的数据 —— 语义上是 403 而不是 404（契约明确要求）
            throw BizException.forbidden("无权访问该餐次");
        }
        return meal;
    }

    @Override
    public List<MealItem> itemsOf(long mealId) {
        return itemMapper.selectList(new LambdaQueryWrapper<MealItem>()
                .eq(MealItem::getMealId, mealId)
                .orderByAsc(MealItem::getId));
    }

    private MealItem requireItem(long mealId, long itemId) {
        MealItem item = itemMapper.selectById(itemId);
        if (item == null || !Long.valueOf(mealId).equals(item.getMealId())) {
            // 不属于该餐就当作不存在，避免暴露其他餐的 item id
            throw BizException.notFound("食物项不存在");
        }
        return item;
    }

    private Map<Long, Food> loadFoods(List<MealItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = items.stream().map(MealItem::getFoodId).distinct().toList();
        return foodMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Food::getId, Function.identity()));
    }

    private MealItemVO toItemVO(MealItem item, Food food) {
        String name = food == null ? "未知食物" : food.getName();
        BigDecimal qty = item.getQuantityG();
        return new MealItemVO(
                item.getId(),
                item.getFoodId(),
                name,
                qty,
                food == null ? NutritionCalculator.zero() : NutritionCalculator.scale(food.getCaloriesPer100g(), qty),
                food == null ? NutritionCalculator.zero() : NutritionCalculator.scale(food.getProteinPer100g(), qty),
                food == null ? NutritionCalculator.zero() : NutritionCalculator.scale(food.getFatPer100g(), qty),
                food == null ? NutritionCalculator.zero() : NutritionCalculator.scale(food.getCarbPer100g(), qty),
                item.getVersion());
    }
}
