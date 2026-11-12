package com.mealwise.day.vo;

import com.mealwise.meal.vo.MealItemVO;
import com.mealwise.meal.vo.NutritionVO;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一天里的一餐（含明细与小计）。
 */
public record MealSummaryVO(
        Long mealId,
        String mealType,
        LocalDateTime eatenAt,
        List<MealItemVO> items,
        NutritionVO total
) {
}
