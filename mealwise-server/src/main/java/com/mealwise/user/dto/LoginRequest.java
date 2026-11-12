package com.mealwise.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * <p>密码不做格式校验：登录只判断"对不对"，格式错误自然也是 401，
 * 返回 400 会泄露"这个账号的密码规则"这类无关信息。
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password
) {
}
