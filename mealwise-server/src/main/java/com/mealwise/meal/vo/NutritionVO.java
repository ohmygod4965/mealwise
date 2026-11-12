package com.mealwise.meal.vo;

import java.math.BigDecimal;

/**
 * 一餐的营养合计。
 */
public record NutritionVO(BigDecimal calories, BigDecimal protein, BigDecimal fat, BigDecimal carb) {
}
