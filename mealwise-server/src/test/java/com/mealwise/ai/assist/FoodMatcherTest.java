package com.mealwise.ai.assist;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealwise.food.entity.Food;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FoodMatcherTest {

    private final List<Food> foods = List.of(
            food(1L, "米饭", "主食", "116", "200"),
            food(2L, "蛋炒饭", "主食", "180", "250"),
            food(3L, "鸡胸肉", "肉蛋", "133", "150"),
            food(4L, "西兰花", "蔬菜", "34", "100"),
            food(5L, "番茄", "蔬菜", "20", "150"),
            food(6L, "纯牛奶", "乳制品", "54", "250"));

    @Test
    @DisplayName("标准名：完全一致")
    void exactMatch() {
        FoodMatcher.Match match = FoodMatcher.match("米饭", foods);

        assertThat(match.hit()).isTrue();
        assertThat(match.food().getName()).isEqualTo("米饭");
        assertThat(match.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("口语别名：白米饭 → 米饭，鸡胸 → 鸡胸肉")
    void aliasMatch() {
        assertThat(FoodMatcher.match("白米饭", foods).food().getName()).isEqualTo("米饭");
        assertThat(FoodMatcher.match("鸡胸", foods).food().getName()).isEqualTo("鸡胸肉");
    }

    @Test
    @DisplayName("包含关系：一杯纯牛奶里的『纯牛奶』")
    void containsMatch() {
        FoodMatcher.Match match = FoodMatcher.match("纯牛奶", foods);

        assertThat(match.hit()).isTrue();
        assertThat(match.food().getName()).isEqualTo("纯牛奶");
    }

    @Test
    @DisplayName("未命中：不瞎猜，最多给相近候选")
    void unknownGivesCandidates() {
        FoodMatcher.Match match = FoodMatcher.match("海带汤", foods);

        assertThat(match.hit()).isFalse();
        assertThat(match.candidates()).hasSizeLessThanOrEqualTo(3);
    }

    private Food food(Long id, String name, String category, String kcal, String unitWeight) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setCategory(category);
        food.setCaloriesPer100g(new BigDecimal(kcal));
        food.setProteinPer100g(BigDecimal.ONE);
        food.setFatPer100g(BigDecimal.ONE);
        food.setCarbPer100g(BigDecimal.ONE);
        food.setUnitName("份");
        food.setUnitWeightG(new BigDecimal(unitWeight));
        food.setEnabled(true);
        return food;
    }
}
