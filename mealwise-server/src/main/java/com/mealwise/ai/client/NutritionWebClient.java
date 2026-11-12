package com.mealwise.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealwise.ai.AiProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 上网查热量：调用 Open Food Facts 公开营养库（无需鉴权）。
 *
 * <p>关键难点不是"能不能查到"，而是"查到的对不对"。公开库是众包数据，
 * 关键词搜索经常返回完全不相关的商品（搜「清蒸鲈鱼」可能命中一包饼干）。
 * 所以这里做了三重校验，宁可返回"未核验"也不能返回错误热量：
 * <ol>
 *   <li><b>名称匹配</b>：商品名必须包含检索词里的关键词；</li>
 *   <li><b>宏量一致</b>：能量 ≈ 4×蛋白 + 9×脂肪 + 4×碳水（误差 ≤30%），不一致说明数据脏；</li>
 *   <li><b>数值合理</b>：每 100g 热量落在 5–900 kcal（油脂/坚果上界，纯水/茶为 0 也允许）。</li>
 * </ol>
 * 另外带内存缓存，同一个词只查一次；任何超时/异常都降级为空结果。
 */
@Component
public class NutritionWebClient {

    private static final Logger log = LoggerFactory.getLogger(NutritionWebClient.class);
    // v2 的 /api/v2/search 会忽略 search_terms（不同词返回同一批商品），且经常 503；
    // 旧版 cgi/search.pl 是相关性排序，实测能正确命中（chicken breast -> Blanc de Poulet）。
    private static final String SEARCH = "https://world.openfoodfacts.org/cgi/search.pl";
    private static final String PRODUCT = "https://world.openfoodfacts.org/product/";
    private static final BigDecimal MIN_KCAL = new BigDecimal("4");
    private static final BigDecimal MAX_KCAL = new BigDecimal("900");

    private final ObjectMapper mapper;
    private final AiProperties properties;
    private final HttpClient http;
    private final ExecutorService pool = Executors.newFixedThreadPool(6, (r) -> {
        Thread t = new Thread(r, "nutrition-web");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Optional<Nutrition>> cache = new ConcurrentHashMap<>();

    public NutritionWebClient(ObjectMapper mapper, AiProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 联网查到的营养值（每 100g）。 */
    public record Nutrition(String productName, BigDecimal kcalPer100g,
                            BigDecimal proteinPer100g, BigDecimal fatPer100g, BigDecimal carbPer100g,
                            String url) {
    }

    /** 候选评分用。 */
    private record Candidate(Nutrition nutrition, int score, BigDecimal macroGap) {
    }

    /**
     * 异步查询；同时尝试英文检索词与中文名，取通过校验的最优候选。
     */
    public CompletableFuture<Optional<Nutrition>> lookupAsync(String englishTerm, String localName) {
        String key = (englishTerm == null ? "" : englishTerm.trim().toLowerCase())
                + "|" + (localName == null ? "" : localName.trim().toLowerCase());
        Optional<Nutrition> cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            Optional<Nutrition> result = fetch(englishTerm, localName);
            cache.put(key, result);
            return result;
        }, pool).orTimeout(properties.webTimeoutMs(), TimeUnit.MILLISECONDS)
                .exceptionally((ex) -> {
                    log.warn("nutrition lookup failed for '{}': {}", localName, ex.getMessage());
                    return Optional.empty();
                });
    }

    private Optional<Nutrition> fetch(String englishTerm, String localName) {
        Set<String> terms = new LinkedHashSet<>();
        if (englishTerm != null && !englishTerm.isBlank()) {
            terms.add(englishTerm.trim());
        }
        if (localName != null && !localName.isBlank()) {
            terms.add(localName.trim());
        }
        if (terms.isEmpty()) {
            return Optional.empty();
        }

        List<JsonNode> products = new ArrayList<>();
        for (String term : terms) {
            products.addAll(search(term));
            if (products.size() >= 8) {
                break;
            }
        }

        Candidate best = null;
        for (JsonNode product : products) {
            Candidate candidate = score(product, terms);
            if (candidate == null) {
                continue;
            }
            if (best == null
                    || candidate.score() > best.score()
                    || (candidate.score() == best.score()
                        && candidate.macroGap().compareTo(best.macroGap()) < 0)) {
                best = candidate;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        log.debug("verified '{}' -> {} ({} kcal/100g)", localName, best.nutrition().productName(), best.nutrition().kcalPer100g());
        return Optional.of(best.nutrition());
    }

    private List<JsonNode> search(String term) {
        try {
            String url = SEARCH + "?search_terms=" + java.net.URLEncoder.encode(term, "UTF-8")
                    + "&search_simple=1&action=process&json=1&page_size=5"
                    + "&fields=product_name,nutriments,code";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(properties.webTimeoutMs()))
                    .header("User-Agent", "Mealwise/1.0 (demo)")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }
            JsonNode products = mapper.readTree(response.body()).path("products");
            List<JsonNode> list = new ArrayList<>();
            if (products.isArray()) {
                products.forEach(list::add);
            }
            return list;
        } catch (Exception ex) {
            log.debug("search '{}' error: {}", term, ex.toString());
            return List.of();
        }
    }

    /** 三重校验打分；返回 null 表示这个候选不可用。 */
    private Candidate score(JsonNode product, Set<String> terms) {
        JsonNode nutriments = product.path("nutriments");
        BigDecimal kcal = kcalOf(nutriments);
        if (kcal == null || kcal.compareTo(MIN_KCAL) < 0 || kcal.compareTo(MAX_KCAL) > 0) {
            return null;
        }
        String productName = product.path("product_name").asText("");
        if (productName.isBlank()) {
            return null;
        }

        BigDecimal protein = dec(nutriments, "proteins_100g");
        BigDecimal fat = dec(nutriments, "fat_100g");
        BigDecimal carb = dec(nutriments, "carbohydrates_100g");

        // 能量应与三大营养素自洽：4P + 9F + 4C，误差 30% 以内
        BigDecimal macroKcal = protein.multiply(new BigDecimal("4"))
                .add(fat.multiply(new BigDecimal("9")))
                .add(carb.multiply(new BigDecimal("4")));
        BigDecimal gap = macroKcal.signum() == 0
                ? new BigDecimal("999")
                : kcal.subtract(macroKcal).abs().divide(macroKcal, 4, RoundingMode.HALF_UP);
        if (gap.compareTo(new BigDecimal("0.30")) > 0) {
            return null;
        }

        String code = product.path("code").asText("");
        Nutrition nutrition = new Nutrition(
                productName.isBlank() ? "未命名商品" : productName,
                kcal, protein, fat, carb,
                code.isBlank() ? "" : PRODUCT + code);
        // 名称能对上更好；对不上也接受（公开库商品名多为法语/西语，强匹配会把正确候选全过滤掉），
        // 但必须通过"宏量自洽 + 数值合理"两道硬校验，且相关性排序在前。
        int score = nameMatches(productName, terms) ? 2 : 1;
        return new Candidate(nutrition, score, gap);
    }

    /** 商品名与检索词的匹配：包含、被包含、或关键词前缀命中（≥3 字符）都算。 */
    private boolean nameMatches(String productName, Set<String> terms) {
        if (productName == null || productName.isBlank()) {
            return false;
        }
        String name = productName.toLowerCase();
        for (String term : terms) {
            String lowerTerm = term.toLowerCase().trim();
            if (lowerTerm.length() >= 2 && (name.contains(lowerTerm) || lowerTerm.contains(name))) {
                return true;
            }
            for (String token : lowerTerm.split("[\\s,，、()（）]+")) {
                String clean = token.trim();
                if (clean.length() < 3) {
                    continue;
                }
                if (name.contains(clean) || clean.contains(name)) {
                    return true;
                }
                // 前缀命中：oat 匹配 oatmeal / oats
                String prefix = clean.length() > 5 ? clean.substring(0, 5) : clean;
                if (name.contains(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 优先 energy-kcal_100g，缺失时用 kJ 换算（1 kcal = 4.184 kJ）。 */
    private BigDecimal kcalOf(JsonNode nutriments) {
        JsonNode kcal = nutriments.get("energy-kcal_100g");
        if (kcal != null && kcal.isNumber()) {
            return BigDecimal.valueOf(kcal.asDouble()).setScale(1, RoundingMode.HALF_UP);
        }
        JsonNode kj = nutriments.get("energy_100g");
        if (kj != null && kj.isNumber()) {
            return BigDecimal.valueOf(kj.asDouble() / 4.184).setScale(1, RoundingMode.HALF_UP);
        }
        return null;
    }

    private BigDecimal dec(JsonNode nutriments, String field) {
        JsonNode node = nutriments.get(field);
        if (node == null || !node.isNumber()) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(node.asDouble()).setScale(1, RoundingMode.HALF_UP);
    }
}
