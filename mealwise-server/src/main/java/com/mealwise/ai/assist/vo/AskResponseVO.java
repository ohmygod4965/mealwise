package com.mealwise.ai.assist.vo;

import java.util.List;

/**
 * 知识问答响应。
 *
 * @param answer    回答正文；模型不可用时是"根据检索到的资料拼出来的"确定性回答
 * @param source    llm / retrieval（纯检索兜底）
 * @param citations 引用的资料（食物库条目 + 营养常识片段）
 */
public record AskResponseVO(String answer, String source, List<CitationVO> citations) {

    /**
     * @param kind food（食物库）/ knowledge（营养常识）
     * @param score BM25 分数，仅用于排序展示
     */
    public record CitationVO(String id, String title, String kind, double score, String text) {
    }
}
