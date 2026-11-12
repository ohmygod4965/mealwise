package com.mealwise.day.vo;

import com.mealwise.meal.vo.NutritionVO;
import com.mealwise.nutrition.vo.NutritionTargetVO;
import java.time.LocalDate;
import java.util.List;

/**
 * 一天的饮食记录。
 *
 * @param meals      当日所有餐次（早餐/午餐/晚餐/加餐）
 * @param totals     当日摄入合计
 * @param targets    每日建议摄入；用户没填身体资料时为 null
 * @param comparison 摄入 vs 建议；targets 为 null 时同样为 null
 */
public record DayRecordVO(
        LocalDate date,
        List<MealSummaryVO> meals,
        NutritionVO totals,
        NutritionTargetVO targets,
        DayComparisonVO comparison
) {
}
