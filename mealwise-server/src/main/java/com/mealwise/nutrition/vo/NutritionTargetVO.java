package com.mealwise.nutrition.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 每日推荐摄入。
 *
 * @param bmr         基础代谢率（Mifflin-St Jeor）
 * @param tdee        总能量消耗（BMR × 活动系数）
 * @param calories    建议摄入热量（演示取 TDEE，不设减重目标）
 * @param formula     计算口径，展示给用户看，避免"黑箱数字"
 * @param assumptions 假设条件（年龄缺省、活动系数等），必须如实告知
 */
public record NutritionTargetVO(
        BigDecimal bmr,
        BigDecimal tdee,
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal fat,
        BigDecimal carb,
        String formula,
        List<String> assumptions
) {
}
