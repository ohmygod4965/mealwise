package com.mealwise.meal.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 餐次基本信息（创建餐次、以及热量接口的公共外壳）。
 */
public record MealVO(Long id, String mealType, LocalDateTime eatenAt, List<MealItemVO> items) {
}
