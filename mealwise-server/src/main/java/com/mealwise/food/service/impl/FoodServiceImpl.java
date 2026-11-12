package com.mealwise.food.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mealwise.common.api.PageResult;
import com.mealwise.common.exception.BizException;
import com.mealwise.common.util.AllergenCodec;
import com.mealwise.food.dto.FoodSaveRequest;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import com.mealwise.food.service.FoodService;
import com.mealwise.food.vo.FoodVO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 食物库实现。
 *
 * <p>删除采用软删：食物一旦被历史 meal_item 引用，物理删除会被外键 RESTRICT 挡住；
 * 软删既保住历史数据，也能让推荐候选自然排除（enabled=1）。
 */
@Service
public class FoodServiceImpl implements FoodService {

    private static final int MAX_PAGE_SIZE = 100;

    private final FoodMapper foodMapper;

    public FoodServiceImpl(FoodMapper foodMapper) {
        this.foodMapper = foodMapper;
    }

    @Override
    public PageResult<FoodVO> page(String keyword, String category, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Food> query = new LambdaQueryWrapper<Food>()
                .eq(Food::getEnabled, true)
                .like(keyword != null && !keyword.isBlank(), Food::getName, keyword == null ? null : keyword.trim())
                .eq(category != null && !category.isBlank(), Food::getCategory, category == null ? null : category.trim())
                .orderByAsc(Food::getCategory)
                .orderByAsc(Food::getCaloriesPer100g)
                .orderByAsc(Food::getId);

        Page<Food> result = foodMapper.selectPage(Page.of(safePage, safeSize), query);
        List<FoodVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional
    public FoodVO create(FoodSaveRequest request) {
        Food food = new Food();
        apply(food, request);
        food.setEnabled(request.enabled() == null || request.enabled());
        foodMapper.insert(food);
        return toVO(food);
    }

    @Override
    @Transactional
    public FoodVO update(long id, FoodSaveRequest request) {
        Food food = requireById(id);
        apply(food, request);
        if (request.enabled() != null) {
            food.setEnabled(request.enabled());
        }
        foodMapper.updateById(food);
        return toVO(food);
    }

    @Override
    @Transactional
    public void disable(long id) {
        Food food = requireById(id);
        if (Boolean.FALSE.equals(food.getEnabled())) {
            throw BizException.conflict("该食物已处于禁用状态");
        }
        food.setEnabled(false);
        foodMapper.updateById(food);
    }

    @Override
    public Food requireById(long id) {
        Food food = foodMapper.selectById(id);
        if (food == null) {
            throw BizException.notFound("食物不存在");
        }
        return food;
    }

    @Override
    public Food requireEnabled(long id) {
        Food food = requireById(id);
        if (!Boolean.TRUE.equals(food.getEnabled())) {
            throw BizException.badRequest("该食物已下架，不能加入餐次");
        }
        return food;
    }

    private void apply(Food food, FoodSaveRequest request) {
        food.setName(request.name().trim());
        food.setCategory(request.category().trim());
        food.setCaloriesPer100g(request.caloriesPer100g());
        food.setProteinPer100g(request.proteinPer100g());
        food.setFatPer100g(request.fatPer100g());
        food.setCarbPer100g(request.carbPer100g());
        food.setUnitName(request.unitName() == null ? null : request.unitName().trim());
        food.setUnitWeightG(request.unitWeightG());
        food.setAllergens(AllergenCodec.encode(request.allergens()));
    }

    private FoodVO toVO(Food food) {
        return new FoodVO(
                food.getId(),
                food.getName(),
                food.getCategory(),
                food.getCaloriesPer100g(),
                food.getProteinPer100g(),
                food.getFatPer100g(),
                food.getCarbPer100g(),
                food.getUnitName(),
                food.getUnitWeightG(),
                AllergenCodec.decode(food.getAllergens()),
                Boolean.TRUE.equals(food.getEnabled()));
    }
}
