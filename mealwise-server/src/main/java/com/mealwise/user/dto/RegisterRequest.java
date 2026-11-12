package com.mealwise.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 注册请求。
 *
 * @param username 4-50 位字母/数字/下划线
 * @param password 8-32 位，必须同时含字母与数字（演示级强度要求）
 * @param allergens 可选，逗号分隔 token 的数组形式，如 ["peanut","seafood"]
 */
public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,50}$", message = "用户名需为 4-50 位字母、数字或下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$", message = "密码需 8-32 位且同时包含字母和数字")
        String password,

        List<@Pattern(regexp = "^[a-z0-9_-]{1,24}$", message = "过敏原需为小写字母、数字、下划线或连字符") String> allergens
) {
}
