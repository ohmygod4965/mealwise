package com.mealwise.ai.assist;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealwise.food.entity.Food;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortionResolverTest {

    @Test
    @DisplayName("显式克数：原样使用")
    void explicitGrams() {
        PortionResolver.Portion portion = PortionResolver.resolve("200克", food(200));

        assertThat(portion.ambiguous()).isFalse();
        assertThat(portion.grams()).isEqualByComparingTo("200.0");
    }

    @Test
    @DisplayName("份数 × 单位重量：两碗米饭 = 400g")
    void servingTimesUnitWeight() {
        PortionResolver.Portion portion = PortionResolver.resolve("两碗", food(200));

        assertThat(portion.ambiguous()).isFalse();
        assertThat(portion.grams()).isEqualByComparingTo("400.0");
    }

    @Test
    @DisplayName("一碗半 = 1.5 份")
    void halfServing() {
        PortionResolver.Portion portion = PortionResolver.resolve("一碗半", food(200));

        assertThat(portion.grams()).isEqualByComparingTo("300.0");
    }

    @Test
    @DisplayName("没给数量 → 追问")
    void missingQuantityAsks() {
        PortionResolver.Portion portion = PortionResolver.resolve(null, food(200));

        assertThat(portion.ambiguous()).isTrue();
        assertThat(portion.hint()).contains("多少");
    }

    @Test
    @DisplayName("食物没有单位重量时，份数无法换算 → 追问")
    void servingWithoutUnitWeightAsks() {
        PortionResolver.Portion portion = PortionResolver.resolve("一份", food(null));

        assertThat(portion.ambiguous()).isTrue();
        assertThat(portion.hint()).contains("克数");
    }

    private Food food(Integer unitWeight) {
        Food food = new Food();
        food.setId(1L);
        food.setName("米饭");
        food.setUnitName("碗");
        food.setUnitWeightG(unitWeight == null ? null : new BigDecimal(unitWeight));
        return food;
    }
}
