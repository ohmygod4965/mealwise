package com.mealwise.ai.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealwise.ai.assist.vo.ClarifyQuestionVO;
import com.mealwise.ai.assist.vo.ParseMealRequest;
import com.mealwise.ai.assist.vo.ParsedItemVO;
import com.mealwise.ai.assist.vo.ParsedMealVO;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 自然语言录餐的编排测试：模型被 mock，验证「解析 → 匹配 → 换算 → 追问」这条链路。
 */
@ExtendWith(MockitoExtension.class)
class MealParseServiceTest {

    @Mock
    private LlmClient llm;
    @Mock
    private FoodMapper foodMapper;

    private MealParseService service;

    private final List<Food> foods = List.of(
            food(1L, "米饭", "主食", "116", "200"),
            food(2L, "鸡胸肉", "肉蛋", "133", "150"),
            food(3L, "西兰花", "蔬菜", "34", "100"));

    @BeforeEach
    void setUp() {
        service = new MealParseService(llm, foodMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("两碗米饭 + 一份鸡胸肉 → 两条食物项，热量由 Java 计算")
    void parsesFullSentenceWithRules() {
        when(foodMapper.selectList(any())).thenReturn(foods);

        ParsedMealVO result = service.parse(1L, new ParseMealRequest("中午吃了两碗米饭和一份鸡胸肉", null));

        assertThat(result.status()).isEqualTo(ParsedMealVO.PARSED);
        assertThat(result.source()).isEqualTo("rule");
        assertThat(result.items()).hasSize(2);

        ParsedItemVO rice = result.items().get(0);
        assertThat(rice.name()).isEqualTo("米饭");
        assertThat(rice.grams()).isEqualByComparingTo("400.0");
        assertThat(rice.calories()).isEqualByComparingTo("464.0");

        ParsedItemVO chicken = result.items().get(1);
        assertThat(chicken.name()).isEqualTo("鸡胸肉");
        assertThat(chicken.grams()).isEqualByComparingTo("150.0");
        assertThat(chicken.calories()).isEqualByComparingTo("199.5");
    }

    @Test
    @DisplayName("食物库没有的食物 → 追问 UNKNOWN_FOOD")
    void unknownFoodAsks() {
        when(foodMapper.selectList(any())).thenReturn(foods);

        ParsedMealVO result = service.parse(1L, new ParseMealRequest("吃了一碗海带汤", null));

        assertThat(result.status()).isEqualTo(ParsedMealVO.NEED_CLARIFICATION);
        assertThat(result.items()).isEmpty();
        assertThat(result.questions()).hasSize(1);
        ClarifyQuestionVO question = result.questions().get(0);
        assertThat(question.type()).isEqualTo("UNKNOWN_FOOD");
        assertThat(question.message()).contains("海带汤");
    }

    @Test
    @DisplayName("没说克数 → 追问 AMBIGUOUS_QUANTITY")
    void missingQuantityAsks() {
        when(foodMapper.selectList(any())).thenReturn(foods);

        ParsedMealVO result = service.parse(1L, new ParseMealRequest("晚上吃了西兰花", null));

        assertThat(result.status()).isEqualTo(ParsedMealVO.NEED_CLARIFICATION);
        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().get(0).type()).isEqualTo("AMBIGUOUS_QUANTITY");
    }

    @Test
    @DisplayName("模型可用时优先用模型解析，但克数仍由 Java 换算")
    void usesLlmWhenAvailable() {
        when(foodMapper.selectList(any())).thenReturn(foods);
        when(llm.usable()).thenReturn(true);
        when(llm.json(any(), any(), anyDouble(), anyInt()))
                .thenReturn("{\"items\":[{\"name\":\"米饭\",\"quantity\":\"200克\"}]}");

        ParsedMealVO result = service.parse(1L, new ParseMealRequest("来点饭", null));

        assertThat(result.source()).isEqualTo("llm");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).grams()).isEqualByComparingTo("200.0");
        assertThat(result.items().get(0).calories()).isEqualByComparingTo("232.0");
    }

    @Test
    @DisplayName("模型调用失败 → 自动降级规则解析，接口不报错")
    void fallsBackWhenLlmFails() {
        when(foodMapper.selectList(any())).thenReturn(foods);
        when(llm.usable()).thenReturn(true);
        when(llm.json(any(), any(), anyDouble(), anyInt()))
                .thenThrow(new LlmClient.LlmException("boom"));

        ParsedMealVO result = service.parse(1L, new ParseMealRequest("中午吃了两碗米饭", null));

        assertThat(result.source()).isEqualTo("rule");
        assertThat(result.note()).contains("大模型不可用");
        assertThat(result.items()).hasSize(1);
    }

    private Food food(Long id, String name, String category, String kcal, String unitWeight) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setCategory(category);
        food.setCaloriesPer100g(new BigDecimal(kcal));
        food.setProteinPer100g(new BigDecimal("2"));
        food.setFatPer100g(new BigDecimal("1"));
        food.setCarbPer100g(new BigDecimal("20"));
        food.setUnitName("份");
        food.setUnitWeightG(new BigDecimal(unitWeight));
        food.setEnabled(true);
        return food;
    }
}
