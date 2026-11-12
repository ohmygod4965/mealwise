package com.mealwise.ai.assist.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 「替换建议 + 人话解释」响应。
 *
 * <p>注意：<b>建议本身（Top3、排序、delta）完全来自规则引擎</b>，
 * LLM 只把 {@code reason} 润色成更自然的一句话；模型失败时 {@code explanation} 就是规则原文。
 *
 * @param source llm（至少一条解释来自模型）/ rule（全部是规则原文）
 */
public record ExplainResponseVO(
        Long mealId,
        String strategy,
        String source,
        List<ItemExplainVO> items
) {

    /** 某个原食物项的 Top3 解释。 */
    public record ItemExplainVO(
            Long itemId,
            String originalName,
            BigDecimal quantityG,
            List<SuggestionExplainVO> suggestions
    ) {
    }

    /**
     * @param ruleReason  规则引擎给的原文（始终保留，便于对照/测试）
     * @param explanation 最终展示文本：模型润色成功=模型句，否则=ruleReason
     * @param fromLlm     这条是不是模型写的
     */
    public record SuggestionExplainVO(
            Long foodId,
            String name,
            BigDecimal deltaCalories,
            String ruleReason,
            String explanation,
            boolean fromLlm
    ) {
    }
}
