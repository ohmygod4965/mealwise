package com.mealwise.meal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 往餐次里添加食物项。
 */
public record AddItemRequest(
        @NotNull(message = "食物 id 不能为空") Long foodId,
        @NotNull(message = "克数不能为空")
        @Positive(message = "克数必须大于 0") BigDecimal quantityG
) {
}
