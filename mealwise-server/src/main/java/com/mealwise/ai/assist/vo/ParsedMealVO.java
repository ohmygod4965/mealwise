package com.mealwise.ai.assist.vo;

import java.util.List;

/**
 * 自然语言录餐的解析结果。
 *
 * @param status    PARSED（可直接入账）/ NEED_CLARIFICATION（需要追问）
 * @param source    llm（模型解析）/ rule（本地规则兜底）
 * @param items     已匹配、已换算的食物项（可能需要追问的那部分不在这里）
 * @param questions 需要用户补充的信息
 * @param note      给用户看的说明（例如"模型不可用，已用本地规则解析"）
 */
public record ParsedMealVO(
        String status,
        String source,
        List<ParsedItemVO> items,
        List<ClarifyQuestionVO> questions,
        String note
) {
    public static final String PARSED = "PARSED";
    public static final String NEED_CLARIFICATION = "NEED_CLARIFICATION";
}
