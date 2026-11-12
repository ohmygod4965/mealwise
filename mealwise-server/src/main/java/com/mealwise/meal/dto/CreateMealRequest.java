package com.mealwise.meal.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * 创建餐次。
 *
 * <p>注意：请求体里**没有** userId —— 归属只从 token 取，
 * 否则前端传一个别人的 userId 就能越权建餐。
 */
public record CreateMealRequest(
        @NotBlank(message = "餐次类型不能为空") String mealType,
        LocalDateTime eatenAt
) {
}
