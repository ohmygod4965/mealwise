package com.mealwise.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI 饮食推荐请求。
 *
 * @param goal            FAT_LOSS / MAINTAIN / MUSCLE_GAIN
 * @param targetCalories  可选；不给则用用户资料算出的 TDEE
 * @param mealTypes       可选；默认 早/午/晚
 * @param preferences     自由文本偏好，如「想吃中餐、少油」
 * @param excludeAllergens 额外排除的过敏原（会与用户资料里的合并）
 */
public record AiMealPlanRequest(
        @Pattern(regexp = "^(FAT_LOSS|MAINTAIN|MUSCLE_GAIN)$", message = "goal 只能是 FAT_LOSS / MAINTAIN / MUSCLE_GAIN")
        String goal,

        @DecimalMin(value = "800", message = "目标热量需在 800-5000 kcal")
        @DecimalMax(value = "5000", message = "目标热量需在 800-5000 kcal")
        BigDecimal targetCalories,

        List<@Pattern(regexp = "^(BREAKFAST|LUNCH|DINNER|SNACK)$") String> mealTypes,

        @Size(max = 200, message = "偏好描述最长 200 字")
        String preferences,

        List<@Size(max = 24) String> excludeAllergens
) {
    public String goalOrDefault() {
        return goal == null || goal.isBlank() ? "MAINTAIN" : goal;
    }

    public List<String> mealTypesOrDefault() {
        return (mealTypes == null || mealTypes.isEmpty())
                ? List.of("BREAKFAST", "LUNCH", "DINNER")
                : mealTypes;
    }
}
