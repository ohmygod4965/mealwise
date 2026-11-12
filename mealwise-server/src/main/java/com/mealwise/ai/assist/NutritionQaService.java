package com.mealwise.ai.assist;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mealwise.ai.assist.rag.Bm25Retriever;
import com.mealwise.ai.assist.rag.NutritionKnowledgeBase;
import com.mealwise.ai.assist.vo.AskResponseVO;
import com.mealwise.ai.assist.vo.AskResponseVO.CitationVO;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 营养知识问答（小型 RAG，可选功能）。
 *
 * <p>检索在本地做（{@link Bm25Retriever}），命中的资料交给模型"只依据资料回答"；
 * 模型不可用时退化为纯检索回答——<b>没有密钥也能用</b>，只是措辞不像人话。
 */
@Service
public class NutritionQaService {

    private static final Logger log = LoggerFactory.getLogger(NutritionQaService.class);
    private static final int TOP_K = 5;

    private static final String SYSTEM_PROMPT = """
            你是营养问答助手。只能依据"资料"回答，资料没写的不要编。
            回答 120 字以内，中文，末尾用 [编号] 标注引用了哪几条资料。
            如果资料不足，直接说"资料里没有足够信息"。
            """;

    private final LlmClient llm;
    private final FoodMapper foodMapper;

    public NutritionQaService(LlmClient llm, FoodMapper foodMapper) {
        this.llm = llm;
        this.foodMapper = foodMapper;
    }

    public AskResponseVO ask(String question) {
        if (question == null || question.isBlank()) {
            return new AskResponseVO("先告诉我你想问什么吧，例如「减脂期晚餐怎么吃」", "retrieval", List.of());
        }

        List<NutritionKnowledgeBase.Doc> docs = new ArrayList<>(NutritionKnowledgeBase.fromFoods(enabledFoods()));
        docs.addAll(NutritionKnowledgeBase.knowledgeDocs());

        List<Bm25Retriever.Scored> hits = Bm25Retriever.search(question, docs, TOP_K);
        List<CitationVO> citations = new ArrayList<>();
        for (Bm25Retriever.Scored hit : hits) {
            citations.add(new CitationVO(
                    hit.doc().id(), hit.doc().title(), hit.doc().kind(), round(hit.score()), hit.doc().text()));
        }

        if (hits.isEmpty()) {
            return new AskResponseVO("食物库和营养常识里都没检索到相关内容，换个说法试试（例如「晚餐怎么搭配」）",
                    "retrieval", List.of());
        }

        if (llm.usable()) {
            try {
                String answer = llm.text(SYSTEM_PROMPT, prompt(question, hits), 0.3, 600);
                return new AskResponseVO(answer.trim(), "llm", citations);
            } catch (Exception ex) {
                log.warn("rag answer via llm failed, fallback to retrieval: {}", ex.toString());
            }
        }
        return new AskResponseVO(fallbackAnswer(question, hits), "retrieval", citations);
    }

    private String prompt(String question, List<Bm25Retriever.Scored> hits) {
        StringBuilder sb = new StringBuilder("资料：\n");
        int index = 1;
        for (Bm25Retriever.Scored hit : hits) {
            sb.append('[').append(index++).append("] ").append(hit.doc().title())
                    .append("：").append(hit.doc().text()).append('\n');
        }
        sb.append("\n问题：").append(question).append('\n');
        return sb.toString();
    }

    /** 无模型时的确定性回答：列命中资料的标题 + 第一句。 */
    private String fallbackAnswer(String question, List<Bm25Retriever.Scored> hits) {
        StringBuilder sb = new StringBuilder("（未配置大模型，以下是从资料里检索到的要点）\n");
        int index = 1;
        for (Bm25Retriever.Scored hit : hits.subList(0, Math.min(3, hits.size()))) {
            sb.append('[').append(index++).append("] ").append(hit.doc().title()).append("：")
                    .append(firstSentence(hit.doc().text())).append('\n');
        }
        return sb.toString().trim();
    }

    private static String firstSentence(String text) {
        int idx = text.indexOf('；');
        if (idx < 0) {
            idx = text.indexOf('。');
        }
        String sentence = idx > 0 ? text.substring(0, idx) : text;
        return sentence.length() > 80 ? sentence.substring(0, 80) + "…" : sentence;
    }

    private List<Food> enabledFoods() {
        return foodMapper.selectList(new LambdaQueryWrapper<Food>().eq(Food::getEnabled, true));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
