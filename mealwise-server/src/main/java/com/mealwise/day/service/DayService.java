package com.mealwise.day.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mealwise.common.util.NutritionCalculator;
import com.mealwise.day.vo.DayComparisonVO;
import com.mealwise.day.vo.DayRecordVO;
import com.mealwise.day.vo.MealSummaryVO;
import com.mealwise.day.vo.MetricComparisonVO;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import com.mealwise.meal.entity.Meal;
import com.mealwise.meal.entity.MealItem;
import com.mealwise.meal.mapper.MealItemMapper;
import com.mealwise.meal.mapper.MealMapper;
import com.mealwise.meal.vo.MealItemVO;
import com.mealwise.meal.vo.NutritionVO;
import com.mealwise.nutrition.service.NutritionTargetService;
import com.mealwise.nutrition.vo.NutritionTargetVO;
import com.mealwise.user.entity.SysUser;
import com.mealwise.user.service.UserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 按天聚合：把当天的所有餐次拼成一份"日记录"，并对比建议摄入。
 *
 * <p>只读服务：不改任何数据，餐次本身在创建时就已持久化。
 */
@Service
public class DayService {

    /** 达成率在 [85%, 115%] 之间算"达标"，避免 99% 也报"未达标"的噪音。 */
    private static final BigDecimal ON_TRACK_LOW = new BigDecimal("85");
    private static final BigDecimal ON_TRACK_HIGH = new BigDecimal("115");

    private final MealMapper mealMapper;
    private final MealItemMapper itemMapper;
    private final FoodMapper foodMapper;
    private final UserService userService;
    private final NutritionTargetService nutritionTargetService;

    public DayService(MealMapper mealMapper,
                      MealItemMapper itemMapper,
                      FoodMapper foodMapper,
                      UserService userService,
                      NutritionTargetService nutritionTargetService) {
        this.mealMapper = mealMapper;
        this.itemMapper = itemMapper;
        this.foodMapper = foodMapper;
        this.userService = userService;
        this.nutritionTargetService = nutritionTargetService;
    }

    public DayRecordVO getDay(long userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        List<Meal> meals = mealMapper.selectList(new LambdaQueryWrapper<Meal>()
                .eq(Meal::getUserId, userId)
                .ge(Meal::getEatenAt, start)
                .lt(Meal::getEatenAt, end)
                .orderByAsc(Meal::getEatenAt)
                .orderByAsc(Meal::getId));

        List<MealSummaryVO> summaries = new ArrayList<>(meals.size());
        BigDecimal calories = NutritionCalculator.zero();
        BigDecimal protein = NutritionCalculator.zero();
        BigDecimal fat = NutritionCalculator.zero();
        BigDecimal carb = NutritionCalculator.zero();

        for (Meal meal : meals) {
            MealSummaryVO summary = summarize(meal);
            summaries.add(summary);
            calories = NutritionCalculator.plus(calories, summary.total().calories());
            protein = NutritionCalculator.plus(protein, summary.total().protein());
            fat = NutritionCalculator.plus(fat, summary.total().fat());
            carb = NutritionCalculator.plus(carb, summary.total().carb());
        }

        // 按早/午/晚/加餐固定顺序展示（同类型内保持时间顺序）
        summaries.sort(Comparator
                .comparingInt((MealSummaryVO m) -> mealTypeOrder(m.mealType()))
                .thenComparing(MealSummaryVO::eatenAt));

        NutritionVO totals = new NutritionVO(calories, protein, fat, carb);

        SysUser user = userService.requireById(userId);
        NutritionTargetVO targets = nutritionTargetService.compute(user);
        DayComparisonVO comparison = targets == null ? null : compare(totals, targets);

        return new DayRecordVO(date, summaries, totals, targets, comparison);
    }

    private MealSummaryVO summarize(Meal meal) {
        List<MealItem> items = itemMapper.selectList(new LambdaQueryWrapper<MealItem>()
                .eq(MealItem::getMealId, meal.getId())
                .orderByAsc(MealItem::getId));

        Map<Long, Food> foods = items.isEmpty()
                ? Map.of()
                : foodMapper.selectBatchIds(items.stream().map(MealItem::getFoodId).distinct().toList())
                        .stream().collect(Collectors.toMap(Food::getId, Function.identity()));

        List<MealItemVO> itemVOs = new ArrayList<>(items.size());
        BigDecimal calories = NutritionCalculator.zero();
        BigDecimal protein = NutritionCalculator.zero();
        BigDecimal fat = NutritionCalculator.zero();
        BigDecimal carb = NutritionCalculator.zero();

        for (MealItem item : items) {
            Food food = foods.get(item.getFoodId());
            boolean known = food != null;
            MealItemVO vo = new MealItemVO(
                    item.getId(),
                    item.getFoodId(),
                    known ? food.getName() : "未知食物",
                    item.getQuantityG(),
                    known ? NutritionCalculator.scale(food.getCaloriesPer100g(), item.getQuantityG()) : NutritionCalculator.zero(),
                    known ? NutritionCalculator.scale(food.getProteinPer100g(), item.getQuantityG()) : NutritionCalculator.zero(),
                    known ? NutritionCalculator.scale(food.getFatPer100g(), item.getQuantityG()) : NutritionCalculator.zero(),
                    known ? NutritionCalculator.scale(food.getCarbPer100g(), item.getQuantityG()) : NutritionCalculator.zero(),
                    item.getVersion());
            itemVOs.add(vo);
            calories = NutritionCalculator.plus(calories, vo.calories());
            protein = NutritionCalculator.plus(protein, vo.protein());
            fat = NutritionCalculator.plus(fat, vo.fat());
            carb = NutritionCalculator.plus(carb, vo.carb());
        }

        return new MealSummaryVO(
                meal.getId(), meal.getMealType(), meal.getEatenAt(),
                itemVOs, new NutritionVO(calories, protein, fat, carb));
    }

    private DayComparisonVO compare(NutritionVO totals, NutritionTargetVO targets) {
        return new DayComparisonVO(
                metric(totals.calories(), targets.calories()),
                metric(totals.protein(), targets.protein()),
                metric(totals.fat(), targets.fat()),
                metric(totals.carb(), targets.carb()));
    }

    private MetricComparisonVO metric(BigDecimal intake, BigDecimal target) {
        BigDecimal safeTarget = target == null ? BigDecimal.ZERO : target;
        BigDecimal diff = intake.subtract(safeTarget).setScale(1, RoundingMode.HALF_UP);
        BigDecimal percent = safeTarget.signum() == 0
                ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : intake.multiply(new BigDecimal("100"))
                        .divide(safeTarget, 4, RoundingMode.HALF_UP)
                        .setScale(1, RoundingMode.HALF_UP);
        return new MetricComparisonVO(intake, safeTarget, diff, percent, status(percent));
    }

    private String status(BigDecimal percent) {
        if (percent.compareTo(ON_TRACK_LOW) < 0) {
            return "UNDER";
        }
        if (percent.compareTo(ON_TRACK_HIGH) > 0) {
            return "OVER";
        }
        return "ON_TRACK";
    }

    private int mealTypeOrder(String mealType) {
        return switch (mealType == null ? "" : mealType) {
            case "BREAKFAST" -> 0;
            case "LUNCH" -> 1;
            case "DINNER" -> 2;
            default -> 3;
        };
    }
}
