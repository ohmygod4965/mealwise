package com.mealwise.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置密码。
 *
 * <p>规则与注册保持一致（8-32 位且同时含字母和数字），避免重置出弱密码。
 */
public record ResetPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 32, message = "密码需 8-32 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字")
        String password
) {
}
