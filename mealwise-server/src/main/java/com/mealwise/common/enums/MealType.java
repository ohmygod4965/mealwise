package com.mealwise.common.enums;

import com.mealwise.common.exception.BizException;

/**
 * 餐次类型。存字符串而非 DB ENUM，便于后续加"夜宵"等类型而不改表。
 */
public enum MealType {

    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK;

    /**
     * 宽松解析：大小写不敏感，非法值抛 400。
     */
    public static MealType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.badRequest("餐次类型不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("餐次类型非法，仅支持 BREAKFAST/LUNCH/DINNER/SNACK");
        }
    }
}
