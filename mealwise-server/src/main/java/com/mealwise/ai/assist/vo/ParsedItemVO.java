package com.mealwise.ai.assist.vo;

import java.math.BigDecimal;

/**
 * 解析出的一样食物（已匹配食物库、已换算克数）。
 *
 * @param foodId     食物库 id，可用于直接 POST /api/meals/{id}/items
 * @param spokenName 用户原话里的名字（"白米饭"）
 * @param name       库里标准名（"米饭"）
 * @param grams      换算后的克数
 * @param calories   按克数算出的热量（Java 计算，非模型生成）
 * @param confidence 匹配置信度 1.0/0.9/0.7/0.6
 */
public record ParsedItemVO(
        Long foodId,
        String spokenName,
        String name,
        String category,
        String quantityText,
        BigDecimal grams,
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal fat,
        BigDecimal carb,
        double confidence
) {
}
