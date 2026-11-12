package com.mealwise.day.vo;

/**
 * 四项指标的对比集合。
 */
public record DayComparisonVO(
        MetricComparisonVO calories,
        MetricComparisonVO protein,
        MetricComparisonVO fat,
        MetricComparisonVO carb
) {
}
