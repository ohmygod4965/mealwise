package com.mealwise.ai.assist;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文口语"吃了什么"的<b>规则解析</b>（不依赖模型）。
 *
 * <p>两种输入：
 * <ul>
 *   <li>{@link #ruleParse(String)}：模型不可用时的兜底，纯正则/分词；</li>
 *   <li>{@link #fromLlmJson(String)}：模型返回的结构化 JSON → {@link RawItem}。</li>
 * </ul>
 * 产出只有"食物名 + 数量描述"，<b>不猜克数、不查库</b>——那是 {@link PortionResolver} / {@link FoodMatcher} 的事。
 */
public final class MealTextParser {

    /** 一段口语里提到的一样本东西。quantity 可能为空（"还有鸡胸肉"）。 */
    public record RawItem(String name, String quantity) {
    }

    /** 中文数量词 + 单位，如 "两碗"、"一份"、"150g"、"半碗"、"一碗半"。 */
    private static final Pattern QUANTITY = Pattern.compile(
            "(半|\\d+(?:\\.\\d+)?|[一二两三四五六七八九十]+)\\s*(碗|份|个|杯|根|片|把|罐|盒|袋|包|克|g|G|毫升|ml|ML)");

    /** 出现在句子开头、需要剥掉的时间/动作词。 */
    private static final String[] NOISE_PREFIX = {
            "我今天", "我", "今天", "早上", "早晨", "上午", "中午", "下午", "晚上", "夜里",
            "早餐", "午饭", "午餐", "晚饭", "晚餐", "夜宵", "加餐", "刚", "刚刚", "吃了", "吃", "喝了", "喝"
    };

    /** 连接词：把一句话切成多段。 */
    private static final Pattern SPLITTER = Pattern.compile("[，,、；;和跟与及还有另外另加加配]");

    private MealTextParser() {
    }

    /**
     * 规则解析：按连接词切段，每段剥掉时间/动作前缀，抽出数量与食物名。
     *
     * <p>例："中午吃了两碗米饭和一份鸡胸肉" → [{米饭, 两碗}, {鸡胸肉, 一份}]
     */
    public static List<RawItem> ruleParse(String text) {
        List<RawItem> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String rawSegment : SPLITTER.split(text)) {
            String segment = rawSegment.trim();
            if (segment.isEmpty()) {
                continue;
            }
            segment = stripNoise(segment);

            String quantity = null;
            Matcher m = QUANTITY.matcher(segment);
            if (m.find()) {
                quantity = m.group(1) + m.group(2);
                String rest = (segment.substring(0, m.start()) + segment.substring(m.end())).trim();
                // "一碗半米饭"：半在数量词后面，属于数量而不是食物名
                if (rest.startsWith("半")) {
                    quantity = quantity + "半";
                    rest = rest.substring(1).trim();
                }
                segment = rest;
            }

            String name = cleanupName(segment);
            if (name.isEmpty()) {
                continue;
            }
            out.add(new RawItem(name, quantity));
        }
        return out;
    }

    /**
     * 解析模型返回的 JSON：{@code {"items":[{"name":"米饭","quantity":"两碗"}]}}。
     * quantity 允许缺失或 null。
     */
    public static List<RawItem> fromLlmJson(com.fasterxml.jackson.databind.JsonNode root) {
        List<RawItem> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        for (com.fasterxml.jackson.databind.JsonNode node : root.path("items")) {
            String name = cleanupName(node.path("name").asText(""));
            String quantity = node.path("quantity").asText("");
            if (!name.isEmpty()) {
                out.add(new RawItem(name, quantity.isBlank() ? null : quantity.trim()));
            }
        }
        return out;
    }

    /** 去掉开头的"我今天/中午/吃了"等词（可能叠多层）。 */
    static String stripNoise(String segment) {
        String result = segment;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : NOISE_PREFIX) {
                if (result.startsWith(prefix)) {
                    result = result.substring(prefix.length()).trim();
                    changed = true;
                }
            }
        }
        // "中午吃了一份鸡胸肉" 里剥完"中午""吃了"后可能剩 "一份鸡胸肉"
        return result;
    }

    /** 食物名只保留中文/字母/数字/括号，去掉"了""的""还有"等残留。 */
    static String cleanupName(String name) {
        String cleaned = name
                .replaceAll("[了的]+$", "")
                .replaceAll("^(还有|另外|另加|还有一?个)", "")
                .replaceAll("[\\s，,。.!！?？、；;:：]+", "")
                .trim();
        return cleaned;
    }

    /** 解析中文数字数量词：一二两三四五六七八九十、半、阿拉伯数字。 */
    public static BigDecimal parseChineseNumber(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String t = token.trim();
        if ("半".equals(t)) {
            return new BigDecimal("0.5");
        }
        if (t.matches("\\d+(?:\\.\\d+)?")) {
            return new BigDecimal(t);
        }
        if ("两".equals(t)) {
            return BigDecimal.valueOf(2);
        }
        if ("十".equals(t)) {
            return BigDecimal.TEN;
        }
        if (t.length() == 1) {
            return BigDecimal.valueOf("一二三四五六七八九十".indexOf(t) + 1L);
        }
        // 十一 / 二十 / 二十三 这类
        if (t.startsWith("十")) {
            BigDecimal rest = parseChineseNumber(t.substring(1));
            return BigDecimal.TEN.add(rest == null ? BigDecimal.ZERO : rest);
        }
        if (t.contains("十")) {
            String[] parts = t.split("十", -1);
            BigDecimal tens = parts[0].isEmpty() ? BigDecimal.ONE : parseChineseNumber(parts[0]);
            BigDecimal ones = parts.length > 1 && !parts[1].isEmpty() ? parseChineseNumber(parts[1]) : BigDecimal.ZERO;
            if (tens == null || ones == null) {
                return null;
            }
            return tens.multiply(BigDecimal.TEN).add(ones);
        }
        return null;
    }

    /** 解析结果里出现的食物名去重（保持顺序），用于生成候选。 */
    public static Set<String> names(List<RawItem> items) {
        Set<String> names = new LinkedHashSet<>();
        for (RawItem item : items) {
            names.add(item.name());
        }
        return names;
    }
}
