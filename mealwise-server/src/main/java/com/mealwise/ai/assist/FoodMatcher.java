package com.mealwise.ai.assist;

import com.mealwise.food.entity.Food;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把口语食物名匹配到食物库（纯函数，无 IO，可单测）。
 *
 * <p>匹配顺序：完全一致 → 别名 → 包含关系 → 无匹配（给候选）。
 * 只做"名字 → 食物"这一步，数量换算交给 {@link PortionResolver}。
 */
public final class FoodMatcher {

    /** 常见口语 → 库里标准名。只放高置信、不会误伤的词。 */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("白米饭", "米饭");
        ALIASES.put("大米饭", "米饭");
        ALIASES.put("米饭", "米饭");
        ALIASES.put("鸡胸", "鸡胸肉");
        ALIASES.put("鸡胸脯肉", "鸡胸肉");
        ALIASES.put("西蓝花", "西兰花");
        ALIASES.put("西红柿", "番茄");
        ALIASES.put("马铃薯", "土豆");
        ALIASES.put("土豆", "土豆");
        ALIASES.put("牛奶", "纯牛奶");
        ALIASES.put("酸奶", "原味酸奶");
        ALIASES.put("豆腐", "北豆腐");
        ALIASES.put("鸡蛋", "鸡蛋");
        ALIASES.put("水煮蛋", "鸡蛋");
        ALIASES.put("三文鱼", "三文鱼");
        ALIASES.put("虾", "基围虾");
    }

    /**
     * 匹配结果。
     *
     * @param food       命中的食物；null 表示没匹配上
     * @param confidence 1.0 完全一致 / 0.9 别名 / 0.7 包含 / 0 未命中
     * @param candidates 未命中时给用户的候选名（最多 3 个）
     */
    public record Match(Food food, double confidence, List<String> candidates) {
        public boolean hit() {
            return food != null;
        }
    }

    private FoodMatcher() {
    }

    public static Match match(String rawName, List<Food> foods) {
        String name = normalize(rawName);
        if (name.isEmpty() || foods == null || foods.isEmpty()) {
            return new Match(null, 0, List.of());
        }

        // 1) 完全一致
        for (Food food : foods) {
            if (normalize(food.getName()).equals(name)) {
                return new Match(food, 1.0, List.of());
            }
        }

        // 2) 别名
        String canonical = ALIASES.get(name);
        if (canonical != null) {
            for (Food food : foods) {
                if (normalize(food.getName()).equals(normalize(canonical))) {
                    return new Match(food, 0.9, List.of());
                }
            }
        }

        // 3) 包含关系（至少 2 个字，避免"蛋""肉"这种误命中）
        if (name.length() >= 2) {
            List<Food> contains = new ArrayList<>();
            for (Food food : foods) {
                String foodName = normalize(food.getName());
                if (foodName.contains(name) || (foodName.length() >= 2 && name.contains(foodName))) {
                    contains.add(food);
                }
            }
            if (contains.size() == 1) {
                return new Match(contains.get(0), 0.7, List.of());
            }
            if (contains.size() > 1) {
                // 多个包含：短名字优先（"米饭" 比 "蛋炒饭" 更贴近）
                contains.sort(Comparator.comparingInt(f -> normalize(f.getName()).length()));
                Food best = contains.get(0);
                return new Match(best, 0.6, names(contains.subList(1, Math.min(4, contains.size()))));
            }
        }

        // 4) 未命中：按"最长公共子串"给候选，方便用户改口
        List<Food> ranked = new ArrayList<>(foods);
        ranked.sort(Comparator.comparingInt((Food f) -> lcs(name, normalize(f.getName()))).reversed());
        List<String> candidates = ranked.stream()
                .filter(f -> lcs(name, normalize(f.getName())) >= 1)
                .limit(3)
                .map(Food::getName)
                .toList();
        return new Match(null, 0, candidates);
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，,。.!！?？、；;:：()（）\\[\\]]", "")
                .replaceAll("[了的]+$", "")
                .trim();
    }

    private static List<String> names(List<Food> foods) {
        List<String> out = new ArrayList<>();
        for (Food food : foods) {
            out.add(food.getName());
        }
        return out;
    }

    /** 最长公共子串长度（中文按字符），用于候选排序。 */
    static int lcs(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int[] prev = new int[b.length() + 1];
        int best = 0;
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    cur[j] = prev[j - 1] + 1;
                    best = Math.max(best, cur[j]);
                }
            }
            prev = cur;
        }
        return best;
    }
}
