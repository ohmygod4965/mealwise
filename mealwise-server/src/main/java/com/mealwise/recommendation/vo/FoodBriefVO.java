package com.mealwise.recommendation.vo;

import java.math.BigDecimal;

/**
 * 推荐里用到的食物精简信息，避免把整张 FoodVO 塞进响应。
 */
public record FoodBriefVO(Long id, String name, String category, BigDecimal caloriesPer100g) {
}
