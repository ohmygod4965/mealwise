package com.mealwise.ai.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealwise.ai.assist.vo.ParseMealRequest;
import com.mealwise.ai.assist.vo.ParsedItemVO;
import com.mealwise.ai.assist.vo.ParsedMealVO;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 口语录餐的**评测集回归**：22 条真实说法，断言「匹配到哪个食物 + 换算成多少克 + 是否需要追问」。
 *
 * <p>这条链路是纯规则的（模型只负责抽 name/quantity），所以可以完全离线、确定性地回归；
 * 换 prompt / 改别名 / 调单位权重时跑一遍，立刻知道有没有退化。
 */
@ExtendWith(MockitoExtension.class)
class NlParseEvalTest {

    @Mock
    private LlmClient llm;
    @Mock
    private FoodMapper foodMapper;

    private MealParseService service;

    static List<EvalCase> cases() throws Exception {
        try (InputStream in = NlParseEvalTest.class.getResourceAsStream("/eval/parse-cases.json")) {
            assertThat(in).as("eval/parse-cases.json 必须存在").isNotNull();
            return new ObjectMapper().readValue(in, new TypeReference<List<EvalCase>>() {
            });
        }
    }

    static Stream<EvalCase> caseStream() throws Exception {
        return cases().stream();
    }

    @BeforeEach
    void setUp() {
        service = new MealParseService(llm, foodMapper, new ObjectMapper());
        when(foodMapper.selectList(any())).thenReturn(fixtures());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("caseStream")
    @DisplayName("评测集：状态 / 食物 / 克数 / 追问类型")
    void evalCase(EvalCase evalCase) {
        ParsedMealVO result = service.parse(1L, new ParseMealRequest(evalCase.text, null));

        assertThat(result.status()).isEqualTo(evalCase.status);
        if (evalCase.questionTypes != null && !evalCase.questionTypes.isEmpty()) {
            assertThat(result.questions()).extracting(q -> q.type())
                    .containsAll(evalCase.questionTypes);
        }
        if (evalCase.items != null) {
            assertThat(result.items()).hasSameSizeAs(evalCase.items);
            for (ExpectedItem expected : evalCase.items) {
                ParsedItemVO actual = result.items().stream()
                        .filter(i -> i.name().equals(expected.name))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("没有匹配到 " + expected.name + "，实际="
                                + result.items().stream().map(ParsedItemVO::name).toList()));
                assertThat(actual.grams()).as("克数：" + expected.name)
                        .isEqualByComparingTo(expected.grams);
            }
        }
    }

    /** 与 docs/init.sql 一致的 19 个食物（名字/单位/单位重量）。 */
    private static List<Food> fixtures() {
        return List.of(
                food(1L, "米饭", "主食", "116", "碗", "200"),
                food(2L, "鸡胸肉", "肉蛋", "133", "份", "150"),
                food(3L, "西兰花", "蔬菜", "34", "份", "100"),
                food(4L, "鸡蛋", "肉蛋", "144", "个", "50"),
                food(5L, "纯牛奶", "乳制品", "54", "杯", "250"),
                food(6L, "牛里脊", "肉蛋", "106", "份", "150"),
                food(7L, "苹果", "水果", "52", "个", "200"),
                food(8L, "三文鱼", "肉蛋", "139", "份", "120"),
                food(9L, "基围虾", "肉蛋", "93", "份", "120"),
                food(10L, "无糖豆浆", "饮料", "31", "杯", "250"),
                food(11L, "煮玉米", "主食", "112", "根", "200"),
                food(12L, "原味酸奶", "乳制品", "72", "杯", "150"),
                food(13L, "意大利面(煮)", "主食", "158", "份", "200"),
                food(14L, "可乐", "饮料", "43", "罐", "330"),
                food(15L, "核桃", "坚果", "646", "把", "30"),
                food(16L, "猪里脊", "肉蛋", "155", "份", "150"),
                food(17L, "菠菜", "蔬菜", "24", "份", "100"),
                food(18L, "西瓜", "水果", "25", "份", "300"),
                food(19L, "小米粥", "主食", "46", "碗", "300"));
    }

    private static Food food(Long id, String name, String category, String kcal, String unit, String unitWeight) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setCategory(category);
        food.setCaloriesPer100g(new BigDecimal(kcal));
        food.setProteinPer100g(new BigDecimal("10"));
        food.setFatPer100g(new BigDecimal("5"));
        food.setCarbPer100g(new BigDecimal("20"));
        food.setUnitName(unit);
        food.setUnitWeightG(new BigDecimal(unitWeight));
        food.setEnabled(true);
        return food;
    }

    public static class EvalCase {
        public String text;
        public String status;
        public List<ExpectedItem> items;
        public List<String> questionTypes;

        @Override
        public String toString() {
            return text;
        }
    }

    public static class ExpectedItem {
        public String name;
        public BigDecimal grams;
    }
}
