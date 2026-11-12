package com.mealwise.meal.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一餐的热量计算结果：明细 + 合计。
 *
 * @param items       每个食物项的贡献明细
 * @param total       合计（四项营养）
 * @param itemCount   食物项数量
 * @param totalGrams  总克数
 */
public record MealCaloriesVO(
        Long mealId,
        String mealType,
        LocalDateTime eatenAt,
        List<MealItemVO> items,
        NutritionVO total,
        int itemCount,
        BigDecimal totalGrams
) {
}
