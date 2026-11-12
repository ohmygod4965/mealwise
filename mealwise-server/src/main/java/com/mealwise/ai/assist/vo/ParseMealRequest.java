package com.mealwise.ai.assist.vo;

import java.time.LocalDate;

/**
 * 自然语言录餐请求。
 *
 * @param text 用户口语，例如"中午吃了两碗米饭和一份鸡胸肉"
 * @param date 可选：补充到哪一天（默认今天）
 */
public record ParseMealRequest(String text, LocalDate date) {
}
