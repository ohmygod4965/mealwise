package com.mealwise.ai.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealwise.ai.assist.vo.ExplainResponseVO;
import com.mealwise.recommendation.service.ReplacementService;
import com.mealwise.recommendation.vo.FoodBriefVO;
import com.mealwise.recommendation.vo.ItemReplacementVO;
import com.mealwise.recommendation.vo.ReplacementResponse;
import com.mealwise.recommendation.vo.SuggestionVO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 解释服务测试：规则结果不可变，模型失败必须回退规则原文。
 */
@ExtendWith(MockitoExtension.class)
class RecommendationExplainerTest {

    @Mock
    private ReplacementService replacementService;
    @Mock
    private LlmClient llm;

    private final String ruleReason = "同为「主食」，每 100g 比「米饭」高 42.0 kcal；按 200g 计，替换后多摄入约 84.0 kcal";

    @Test
    @DisplayName("模型不可用 → 解释等于规则原文，source=rule")
    void fallsBackToRuleReason() {
        when(replacementService.recommend(1L, 10L, "higher")).thenReturn(rules());

        ExplainResponseVO result = explainer().explain(1L, 10L, "higher");

        assertThat(result.source()).isEqualTo("rule");
        ExplainResponseVO.SuggestionExplainVO suggestion = result.items().get(0).suggestions().get(0);
        assertThat(suggestion.explanation()).isEqualTo(ruleReason);
        assertThat(suggestion.fromLlm()).isFalse();
        assertThat(suggestion.deltaCalories()).isEqualByComparingTo("84.0");
    }

    @Test
    @DisplayName("模型可用 → 用模型句子替换展示文本，但规则原文仍保留")
    void usesLlmExplanation() {
        when(replacementService.recommend(1L, 10L, "higher")).thenReturn(rules());
        when(llm.usable()).thenReturn(true);
        when(llm.json(any(), any(), anyDouble(), anyInt()))
                .thenReturn("{\"explanations\":[{\"key\":\"米饭->意大利面(煮)\","
                        + "\"text\":\"换成意大利面同样管饱，但这一餐会多吃 84 kcal，减脂期要留意。\"}]}");

        ExplainResponseVO result = explainer().explain(1L, 10L, "higher");

        assertThat(result.source()).isEqualTo("llm");
        ExplainResponseVO.SuggestionExplainVO suggestion = result.items().get(0).suggestions().get(0);
        assertThat(suggestion.fromLlm()).isTrue();
        assertThat(suggestion.explanation()).contains("84");
        assertThat(suggestion.ruleReason()).isEqualTo(ruleReason);
        assertThat(suggestion.deltaCalories()).isEqualByComparingTo("84.0");
    }

    @Test
    @DisplayName("模型抛异常 → 回退规则原文，不把错误抛给前端")
    void fallsBackOnLlmError() {
        when(replacementService.recommend(1L, 10L, "higher")).thenReturn(rules());
        when(llm.usable()).thenReturn(true);
        when(llm.json(any(), any(), anyDouble(), anyInt()))
                .thenThrow(new LlmClient.LlmException("timeout"));

        ExplainResponseVO result = explainer().explain(1L, 10L, "higher");

        assertThat(result.source()).isEqualTo("rule");
        assertThat(result.items().get(0).suggestions().get(0).explanation()).isEqualTo(ruleReason);
    }

    private RecommendationExplainer explainer() {
        return new RecommendationExplainer(replacementService, llm, new ObjectMapper());
    }

    private ReplacementResponse rules() {
        SuggestionVO suggestion = new SuggestionVO(
                9L, "意大利面(煮)", "主食",
                new BigDecimal("158.0"), new BigDecimal("316.0"), new BigDecimal("84.0"), ruleReason);
        ItemReplacementVO item = new ItemReplacementVO(
                1L,
                new FoodBriefVO(1L, "米饭", "主食", new BigDecimal("116.0")),
                new BigDecimal("200.0"),
                new BigDecimal("232.0"),
                List.of(suggestion));
        return new ReplacementResponse(10L, "higher", List.of(item), new BigDecimal("84.0"), null);
    }
}
