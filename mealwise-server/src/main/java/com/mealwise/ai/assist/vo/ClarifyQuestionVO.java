package com.mealwise.ai.assist.vo;

import java.util.List;

/**
 * 需要用户补充信息时的一条追问。
 *
 * @param type       UNKNOWN_FOOD / AMBIGUOUS_QUANTITY
 * @param field      对应哪个口语食物（"白米饭"）
 * @param message    给用户看的一句话
 * @param candidates 可选候选（unknown food 时是库里相近的名字）
 */
public record ClarifyQuestionVO(String type, String field, String message, List<String> candidates) {

    public static ClarifyQuestionVO unknownFood(String name, List<String> candidates) {
        String message = candidates.isEmpty()
                ? "食物库里没有找到「" + name + "」，换个说法或先把它加进食物库"
                : "食物库里没有「" + name + "」，你是不是指：" + String.join(" / ", candidates);
        return new ClarifyQuestionVO("UNKNOWN_FOOD", name, message, candidates);
    }

    public static ClarifyQuestionVO ambiguousQuantity(String name, String hint) {
        return new ClarifyQuestionVO("AMBIGUOUS_QUANTITY", name, "「" + name + "」" + hint, List.of());
    }
}
