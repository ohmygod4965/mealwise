package com.mealwise.ai.assist;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealwise.ai.assist.vo.ClarifyQuestionVO;
import com.mealwise.ai.assist.vo.ParseMealRequest;
import com.mealwise.ai.assist.vo.ParsedItemVO;
import com.mealwise.ai.assist.vo.ParsedMealVO;
import com.mealwise.common.util.NutritionCalculator;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 自然语言录餐：<b>模型只做"话 → 食物名 + 数量描述"的解析</b>。
 *
 * <p>分工是刻意的：
 * <ol>
 *   <li>{@link LlmClient} 抽取 {@code [{"name":"米饭","quantity":"两碗"}]}；模型不允许算克数/热量；</li>
 *   <li>{@link FoodMatcher} 匹配食物库；匹配不到 → 追问（带候选）；</li>
 *   <li>{@link PortionResolver} 用库里的单位重量换算克数；信息不足 → 追问；</li>
 *   <li>热量/营养素一律由 {@link NutritionCalculator} 在 Java 侧算，模型完全不参与。</li>
 * </ol>
 * 模型不可用时自动降级为 {@link MealTextParser#ruleParse(String)}，结果结构完全一致。
 */
@Service
public class MealParseService {

    private static final Logger log = LoggerFactory.getLogger(MealParseService.class);

    private static final String SYSTEM_PROMPT = """
            你是食物抽取器，只输出 JSON，不要解释。
            从用户的一句话里抽取吃了什么、吃了多少，输出：
            {"items":[{"name":"食物名","quantity":"原文里的数量描述"}]}
            规则：
            - name 只写食物本身，不要写"吃了/和/还有"，不要合并两样东西；
            - quantity 原样保留用户说法（如"两碗""一份""150克""半碗"），没有就说 null；
            - 不要换算克数、不要猜热量、不要补充用户没说的食物。
            """;

    private final LlmClient llm;
    private final FoodMapper foodMapper;
    private final ObjectMapper mapper;

    public MealParseService(LlmClient llm, FoodMapper foodMapper, ObjectMapper mapper) {
        this.llm = llm;
        this.foodMapper = foodMapper;
        this.mapper = mapper;
    }

    public ParsedMealVO parse(long userId, ParseMealRequest request) {
        String text = request == null ? null : request.text();
        if (text == null || text.isBlank()) {
            return new ParsedMealVO(ParsedMealVO.NEED_CLARIFICATION, "rule", List.of(),
                    List.of(new ClarifyQuestionVO("EMPTY_INPUT", "text", "先说一句吃了什么吧，例如「中午吃了两碗米饭和一份鸡胸肉」", List.of())),
                    null);
        }

        List<Food> foods = enabledFoods();
        List<MealTextParser.RawItem> raw;
        String source;
        String note = null;

        if (llm.usable()) {
            try {
                String content = llm.json(SYSTEM_PROMPT, text, 0.0, 500);
                JsonNode root = mapper.readTree(stripFence(content));
                raw = MealTextParser.fromLlmJson(root);
                source = "llm";
                if (raw.isEmpty()) {
                    raw = MealTextParser.ruleParse(text);
                    source = "rule";
                    note = "模型没有抽到食物，已用本地规则重试";
                }
            } catch (Exception ex) {
                log.warn("nl meal parse via llm failed, fallback to rules: {}", ex.toString());
                raw = MealTextParser.ruleParse(text);
                source = "rule";
                note = "大模型不可用，已用本地规则解析";
            }
        } else {
            raw = MealTextParser.ruleParse(text);
            source = "rule";
            note = "未配置大模型密钥，已用本地规则解析";
        }

        return assemble(raw, foods, source, note);
    }

    /** 把"名字 + 数量"变成可入账的食物项；哪一步失败就产出一条追问。 */
    ParsedMealVO assemble(List<MealTextParser.RawItem> raw, List<Food> foods, String source, String note) {
        List<ParsedItemVO> items = new ArrayList<>();
        List<ClarifyQuestionVO> questions = new ArrayList<>();

        for (MealTextParser.RawItem item : raw) {
            FoodMatcher.Match match = FoodMatcher.match(item.name(), foods);
            if (!match.hit()) {
                questions.add(ClarifyQuestionVO.unknownFood(item.name(), match.candidates()));
                continue;
            }
            Food food = match.food();
            PortionResolver.Portion portion = PortionResolver.resolve(item.quantity(), food);
            if (portion.ambiguous()) {
                questions.add(ClarifyQuestionVO.ambiguousQuantity(food.getName(), portion.hint()));
                continue;
            }
            items.add(toVO(item.name(), food, item.quantity(), portion.grams(), match.confidence()));
        }

        if (items.isEmpty() && questions.isEmpty()) {
            questions.add(new ClarifyQuestionVO("NOT_UNDERSTOOD", "text",
                    "没听懂吃了什么，换个说法试试（例如「米饭 200 克、鸡胸肉 150 克」）", List.of()));
        }

        String status = questions.isEmpty() ? ParsedMealVO.PARSED : ParsedMealVO.NEED_CLARIFICATION;
        return new ParsedMealVO(status, source, items, questions, note);
    }

    private ParsedItemVO toVO(String spokenName, Food food, String quantityText,
                              BigDecimal grams, double confidence) {
        return new ParsedItemVO(
                food.getId(),
                spokenName,
                food.getName(),
                food.getCategory(),
                quantityText,
                grams,
                NutritionCalculator.scale(food.getCaloriesPer100g(), grams),
                NutritionCalculator.scale(food.getProteinPer100g(), grams),
                NutritionCalculator.scale(food.getFatPer100g(), grams),
                NutritionCalculator.scale(food.getCarbPer100g(), grams),
                confidence);
    }

    private List<Food> enabledFoods() {
        return foodMapper.selectList(new LambdaQueryWrapper<Food>().eq(Food::getEnabled, true));
    }

    private static String stripFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
