package com.mealwise.ai.assist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealwise.ai.assist.vo.ExplainResponseVO;
import com.mealwise.ai.assist.vo.ExplainResponseVO.ItemExplainVO;
import com.mealwise.ai.assist.vo.ExplainResponseVO.SuggestionExplainVO;
import com.mealwise.recommendation.service.ReplacementService;
import com.mealwise.recommendation.vo.ItemReplacementVO;
import com.mealwise.recommendation.vo.ReplacementResponse;
import com.mealwise.recommendation.vo.SuggestionVO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 给规则引擎的 Top3 建议配一句"人话"。
 *
 * <p>边界很清楚：<b>选谁、排序、delta 都是 {@link ReplacementService} 算好的</b>，
 * 这里只把结构化结果丢给模型润色措辞；模型不可用/返回不合法时，直接回退到规则自带的
 * {@code reason}，保证接口永远 200 且解释永远存在。
 */
@Service
public class RecommendationExplainer {

    private static final Logger log = LoggerFactory.getLogger(RecommendationExplainer.class);

    private static final String SYSTEM_PROMPT = """
            你是营养师助手。用户会给你一组"替换建议"，每条都含：原食物、建议换成、策略、热量差。
            请为每条写一句中文解释（30 字以内），说明为什么值得换、能多/少多少热量。
            只输出 JSON：{"explanations":[{"key":"原食物->建议食物","text":"一句话"}]}
            不要新增建议、不要改变热量差、不要用 markdown。
            """;

    private final ReplacementService replacementService;
    private final LlmClient llm;
    private final ObjectMapper mapper;

    public RecommendationExplainer(ReplacementService replacementService, LlmClient llm, ObjectMapper mapper) {
        this.replacementService = replacementService;
        this.llm = llm;
        this.mapper = mapper;
    }

    public ExplainResponseVO explain(long userId, long mealId, String strategy) {
        ReplacementResponse rules = replacementService.recommend(userId, mealId, strategy);

        Map<String, String> polished = Map.of();
        boolean llmUsed = false;
        if (llm.usable() && !rules.items().isEmpty()) {
            try {
                polished = askModel(rules);
                llmUsed = !polished.isEmpty();
            } catch (Exception ex) {
                log.warn("explain via llm failed, fallback to rule reasons: {}", ex.toString());
            }
        }

        List<ItemExplainVO> items = new ArrayList<>();
        for (ItemReplacementVO item : rules.items()) {
            List<SuggestionExplainVO> suggestions = new ArrayList<>();
            for (SuggestionVO s : item.suggestions()) {
                String key = key(item.originalFood().name(), s.name());
                String text = polished.get(key);
                suggestions.add(new SuggestionExplainVO(
                        s.foodId(),
                        s.name(),
                        s.deltaCalories(),
                        s.reason(),
                        text == null || text.isBlank() ? s.reason() : text,
                        text != null && !text.isBlank()));
            }
            items.add(new ItemExplainVO(item.itemId(), item.originalFood().name(), item.quantityG(), suggestions));
        }

        return new ExplainResponseVO(mealId, rules.strategy(), llmUsed ? "llm" : "rule", items);
    }

    private Map<String, String> askModel(ReplacementResponse rules) throws Exception {
        List<Map<String, Object>> payloadItems = new ArrayList<>();
        for (ItemReplacementVO item : rules.items()) {
            for (SuggestionVO s : item.suggestions()) {
                Map<String, Object> one = new HashMap<>();
                one.put("key", key(item.originalFood().name(), s.name()));
                one.put("原食物", item.originalFood().name());
                one.put("建议食物", s.name());
                one.put("策略", rules.strategy());
                one.put("热量差", s.deltaCalories());
                one.put("每100g热量", s.caloriesPer100g());
                payloadItems.add(one);
            }
        }
        String user = mapper.writeValueAsString(Map.of("suggestions", payloadItems));
        String content = llm.json(SYSTEM_PROMPT, user, 0.3, 800);

        JsonNode root = mapper.readTree(stripFence(content));
        Map<String, String> out = new HashMap<>();
        for (JsonNode node : root.path("explanations")) {
            String key = node.path("key").asText("");
            String text = node.path("text").asText("");
            if (!key.isBlank() && !text.isBlank()) {
                out.put(key, text.trim());
            }
        }
        return out;
    }

    private static String key(String original, String suggestion) {
        return original + "->" + suggestion;
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
