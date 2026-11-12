package com.mealwise.ai.assist.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealwise.food.entity.Food;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RAG 检索评测：问题 → 期望命中的资料 id，统计 Top-5 命中率。
 *
 * <p>语料 = 食物库（3 条示例）+ 7 条营养常识；只测检索，不调模型。
 */
class RagEvalTest {

    private static final List<Question> QUESTIONS = List.of(
            new Question("减脂期晚餐怎么吃", "kb-3"),
            new Question("蛋白质吃多少合适", "kb-2"),
            new Question("米饭的热量是多少", "food-1"),
            new Question("西兰花营养怎么样", "food-3"),
            new Question("主食应该怎么选", "kb-4"),
            new Question("两餐之间饿了吃什么", "kb-7"));

    record Question(String text, String expectedId) {
    }

    @Test
    @DisplayName("Top-5 检索命中率 ≥ 5/6，且每条期望资料都排在前 5")
    void retrievalHitRate() {
        List<NutritionKnowledgeBase.Doc> docs = docs();
        int hits = 0;
        for (Question question : QUESTIONS) {
            List<String> topIds = Bm25Retriever.search(question.text(), docs, 5).stream()
                    .map(hit -> hit.doc().id())
                    .toList();
            boolean hit = topIds.contains(question.expectedId());
            if (hit) {
                hits++;
            } else {
                System.out.println("[miss] " + question.text() + " expected=" + question.expectedId() + " got=" + topIds);
            }
        }
        assertThat(hits).as("命中数（共 %d 题）", QUESTIONS.size()).isGreaterThanOrEqualTo(5);
    }

    private List<NutritionKnowledgeBase.Doc> docs() {
        List<NutritionKnowledgeBase.Doc> docs = new ArrayList<>(NutritionKnowledgeBase.fromFoods(List.of(
                food(1L, "米饭", "主食", "116"),
                food(2L, "鸡胸肉", "肉蛋", "133"),
                food(3L, "西兰花", "蔬菜", "34"))));
        docs.addAll(NutritionKnowledgeBase.knowledgeDocs());
        return docs;
    }

    private Food food(Long id, String name, String category, String kcal) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setCategory(category);
        food.setCaloriesPer100g(new BigDecimal(kcal));
        food.setProteinPer100g(new BigDecimal("10"));
        food.setFatPer100g(new BigDecimal("2"));
        food.setCarbPer100g(new BigDecimal("20"));
        food.setEnabled(true);
        return food;
    }
}
