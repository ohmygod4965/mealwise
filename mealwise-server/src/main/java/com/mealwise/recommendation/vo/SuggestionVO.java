package com.mealwise.recommendation.vo;

import java.math.BigDecimal;

/**
 * 一条替换建议。
 *
 * @param replacedCalories 换成果该食物后，这一项的摄入热量
 * @param deltaCalories    差值（正=增加，负=减少）
 * @param reason           给用户看的一句话理由，必须包含"同类 + 热量差 + 实际增量"
 */
public record SuggestionVO(
        Long foodId,
        String name,
        String category,
        BigDecimal caloriesPer100g,
        BigDecimal replacedCalories,
        BigDecimal deltaCalories,
        String reason
) {
}
