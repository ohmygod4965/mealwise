package com.mealwise.food.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 食物视图对象。不暴露 Entity，也方便后续加减字段不影响表结构。
 */
public record FoodVO(
        Long id,
        String name,
        String category,
        BigDecimal caloriesPer100g,
        BigDecimal proteinPer100g,
        BigDecimal fatPer100g,
        BigDecimal carbPer100g,
        String unitName,
        BigDecimal unitWeightG,
        List<String> allergens,
        boolean enabled
) {
}
