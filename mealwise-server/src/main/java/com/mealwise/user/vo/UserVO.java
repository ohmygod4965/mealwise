package com.mealwise.user.vo;

import com.mealwise.user.entity.SysUser;
import java.math.BigDecimal;
import java.util.List;

/**
 * 用户视图对象（绝不直接返回 Entity：Entity 含 passwordHash）。
 *
 * @param profileComplete 性别/身高/体重是否齐全，前端据此决定要不要引导填写
 */
public record UserVO(
        Long id,
        String username,
        List<String> allergens,
        String gender,
        BigDecimal heightCm,
        BigDecimal weightKg,
        Integer age,
        boolean profileComplete,
        /** ADMIN / USER：前端据此决定是否显示「用户管理」入口 */
        String role
) {
}
