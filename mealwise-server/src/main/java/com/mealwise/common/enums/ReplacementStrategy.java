package com.mealwise.common.enums;

import com.mealwise.common.exception.BizException;

/**
 * 替换策略。
 *
 * <ul>
 *   <li>{@code higher}：找同类别中热量更高的食物（默认，用于"想多吃点"）；</li>
 *   <li>{@code lower}：找热量更低的食物（用于"想控制热量"）。</li>
 * </ul>
 *
 * 只允许这两个值，其余一律 400 —— 不做"模糊匹配"，避免策略写错却静默按默认跑。
 */
public enum ReplacementStrategy {

    HIGHER,
    LOWER;

    public static ReplacementStrategy parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return HIGHER;
        }
        String value = raw.trim().toUpperCase();
        if ("HIGHER".equals(value)) {
            return HIGHER;
        }
        if ("LOWER".equals(value)) {
            return LOWER;
        }
        throw BizException.badRequest("替换策略非法，仅支持 higher 或 lower");
    }

    public boolean isHigher() {
        return this == HIGHER;
    }
}
