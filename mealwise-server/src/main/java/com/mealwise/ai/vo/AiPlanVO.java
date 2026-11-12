package com.mealwise.ai.vo;

import com.mealwise.meal.vo.NutritionVO;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI 饮食推荐结果。
 *
 * @param source       llm = 大模型生成；local = 模型不可用时的本地规则兜底
 * @param model        实际使用的模型名；local 时为 null
 * @param verification 联网核验统计（有多少项用公开营养库校准过）
 * @param notes        给用户看的提示，例如「部分食物未联网核验，采用模型估算」
 */
public record AiPlanVO(
        String goal,
        BigDecimal targetCalories,
        String source,
        String model,
        String summary,
        List<MealPlan> meals,
        NutritionVO totals,
        Verification verification,
        List<String> notes
) {

    /** 一餐的推荐。 */
    public record MealPlan(String mealType, List<PlanItem> items, NutritionVO total) {
    }

    /**
     * 单样食物。
     *
     * @param verifySource 核验来源：web = 联网营养库；food-db = 项目食物库；estimate = 模型估算
     * @param verified     是否经过核验（web 或 food-db）
     * @param sourceUrl    联网核验时的商品页地址
     */
    public record PlanItem(
            String name,
            String nameEn,
            BigDecimal grams,
            BigDecimal kcalPer100g,
            BigDecimal calories,
            BigDecimal protein,
            BigDecimal fat,
            BigDecimal carb,
            boolean verified,
            String verifySource,
            String sourceUrl
    ) {
    }

    /** 联网核验统计。 */
    public record Verification(int checked, int matched) {
    }
}
