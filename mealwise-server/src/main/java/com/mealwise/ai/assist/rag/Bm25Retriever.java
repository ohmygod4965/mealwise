package com.mealwise.ai.assist.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 极简 BM25 检索（中文按字符二元组切词，英文/数字按词）。
 *
 * <p>不引入向量库的理由：语料只有几十条（食物库 + 几条常识），
 * 关键词检索足够召回，且<b>可单测、零外部依赖、零额外费用</b>。
 * 需要更强语义时再换 embedding 也不影响上层接口。
 */
public final class Bm25Retriever {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /** 一条检索结果。 */
    public record Scored(NutritionKnowledgeBase.Doc doc, double score) {
    }

    private Bm25Retriever() {
    }

    public static List<Scored> search(String query, List<NutritionKnowledgeBase.Doc> docs, int topK) {
        List<Scored> results = new ArrayList<>();
        if (query == null || query.isBlank() || docs == null || docs.isEmpty()) {
            return results;
        }
        List<Set<String>> docTokens = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        int totalLen = 0;
        for (NutritionKnowledgeBase.Doc doc : docs) {
            Set<String> tokens = tokenize(doc.title() + " " + doc.text());
            docTokens.add(tokens);
            totalLen += tokens.size();
            for (String token : tokens) {
                df.merge(token, 1, Integer::sum);
            }
        }
        double avgLen = docs.isEmpty() ? 0 : (double) totalLen / docs.size();
        Set<String> queryTokens = tokenize(query);
        int n = docs.size();

        for (int i = 0; i < docs.size(); i++) {
            Set<String> tokens = docTokens.get(i);
            double score = 0;
            for (String q : queryTokens) {
                Integer freq = df.get(q);
                if (freq == null) {
                    continue;
                }
                int tf = 0;
                for (String token : tokens) {
                    if (token.equals(q)) {
                        tf++;
                    }
                }
                if (tf == 0) {
                    continue;
                }
                double idf = Math.log(1 + (n - freq + 0.5) / (freq + 0.5));
                double norm = tf * (K1 + 1) / (tf + K1 * (1 - B + B * tokens.size() / (avgLen == 0 ? 1 : avgLen)));
                score += idf * norm;
            }
            if (score > 0) {
                results.add(new Scored(docs.get(i), score));
            }
        }
        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    /** 中文二元组 + 英文/数字整词；单字段落补一个字，避免"米"这种完全召回不到。 */
    static Set<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null) {
            return tokens;
        }
        String normalized = text.toLowerCase().replaceAll("[\\s，。,.!！?？、；;:：()（）\\[\\]]", " ").trim();
        for (String chunk : normalized.split("\\s+")) {
            if (chunk.isEmpty()) {
                continue;
            }
            if (chunk.matches("[\\x00-\\x7f]+")) {
                tokens.add(chunk);
                continue;
            }
            tokens.add(chunk);
            for (int i = 0; i + 1 < chunk.length(); i++) {
                tokens.add(chunk.substring(i, i + 2));
            }
            if (chunk.length() == 1) {
                tokens.add(chunk);
            }
        }
        return tokens;
    }
}
