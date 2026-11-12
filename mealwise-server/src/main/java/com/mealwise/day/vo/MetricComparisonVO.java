package com.mealwise.day.vo;

import java.math.BigDecimal;

/**
 * 单个指标的"摄入 vs 建议"。
 *
 * @param diff    摄入 − 目标（负=还差，正=已超）
 * @param percent 达成率，保留 1 位小数
 * @param status  UNDER / ON_TRACK / OVER（85%~115% 视为达标）
 */
public record MetricComparisonVO(
        BigDecimal intake,
        BigDecimal target,
        BigDecimal diff,
        BigDecimal percent,
        String status
) {
}
