package com.mealwise.recommendation.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 某个食物项的替换建议（每项最多 3 条）。
 */
public record ItemReplacementVO(
        Long itemId,
        FoodBriefVO originalFood,
        BigDecimal quantityG,
        BigDecimal originalCalories,
        List<SuggestionVO> suggestions
) {
}
