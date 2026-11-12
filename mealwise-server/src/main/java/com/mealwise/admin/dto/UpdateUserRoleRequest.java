package com.mealwise.admin.dto;

import jakarta.validation.constraints.Pattern;

/** 修改角色。只允许 ADMIN / USER。 */
public record UpdateUserRoleRequest(
        @Pattern(regexp = "^(ADMIN|USER)$", message = "role 只能是 ADMIN 或 USER")
        String role
) {
}
