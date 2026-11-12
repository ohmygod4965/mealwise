package com.mealwise.ai.assist.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealwise.food.entity.Food;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Bm25RetrieverTest {

    private List<NutritionKnowledgeBase.Doc> docs() {
        List<NutritionKnowledgeBase.Doc> docs = new ArrayList<>(NutritionKnowledgeBase.fromFoods(List.of(
                food(1L, "米饭", "主食", "116"),
                food(2L, "鸡胸肉", "肉蛋", "133"),
                food(3L, "西兰花", "蔬菜", "34"))));
        docs.addAll(NutritionKnowledgeBase.knowledgeDocs());
        return docs;
    }

    @Test
    @DisplayName("问『减脂期晚餐怎么吃』能召回晚餐/减脂相关常识")
    void retrievesDinnerKnowledge() {
        List<Bm25Retriever.Scored> hits = Bm25Retriever.search("减脂期晚餐怎么吃", docs(), 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits).anySatisfy(hit ->
                assertThat(hit.doc().title()).containsAnyOf("晚餐", "减脂"));
    }

    @Test
    @DisplayName("问具体食物时，食物库条目排在前面")
    void retrievesFoodDoc() {
        List<Bm25Retriever.Scored> hits = Bm25Retriever.search("米饭热量", docs(), 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).doc().title()).isEqualTo("米饭");
        assertThat(hits.get(0).doc().kind()).isEqualTo("food");
    }

    @Test
    @DisplayName("完全无关的问题 → 不硬凑，返回空")
    void noMatchReturnsEmpty() {
        List<Bm25Retriever.Scored> hits = Bm25Retriever.search("量子计算机的原理", docs(), 5);

        assertThat(hits).isEmpty();
    }

    private Food food(Long id, String name, String category, String kcal) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setCategory(category);
        food.setCaloriesPer100g(new BigDecimal(kcal));
        food.setProteinPer100g(BigDecimal.ONE);
        food.setFatPer100g(BigDecimal.ONE);
        food.setCarbPer100g(BigDecimal.ONE);
        food.setEnabled(true);
        return food;
    }
}
