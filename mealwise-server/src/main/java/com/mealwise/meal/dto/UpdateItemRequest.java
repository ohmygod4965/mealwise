package com.mealwise.meal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 修改食物项克数（只允许改克数：换食物等价于"删旧加新"，语义更清晰）。
 *
 * @param version 可选。前端回传读取时的版本号，用于并发冲突检测；
 *                不传时退化为"读后带版本条件更新"，仍能发现冲突，只是报错更晚
 */
public record UpdateItemRequest(
        @NotNull(message = "克数不能为空")
        @Positive(message = "克数必须大于 0") BigDecimal quantityG,

        Integer version
) {
}
