package com.mealwise.meal.vo;

import java.math.BigDecimal;

/**
 * 餐次中的单个食物项（已换算成该克数下的营养）。
 *
 * @param version 乐观锁版本号：前端改克数时回传，用于检测并发冲突
 */
public record MealItemVO(
        Long itemId,
        Long foodId,
        String foodName,
        BigDecimal quantityG,
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal fat,
        BigDecimal carb,
        Integer version
) {
}
