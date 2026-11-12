package com.mealwise.ai.assist.vo;

/**
 * 营养知识问答请求。
 *
 * @param question 用户问题，例如"减脂期晚餐怎么吃"
 */
public record AskRequest(String question) {
}
