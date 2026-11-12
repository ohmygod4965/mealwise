package com.mealwise.admin.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户列表项。
 *
 * <p>不返回 passwordHash；过敏原转成数组方便前端直接展示。
 */
public record AdminUserVO(
        Long id,
        String username,
        String role,
        int status,
        List<String> allergens,
        String gender,
        boolean profileComplete,
        LocalDateTime createdAt
) {
}
