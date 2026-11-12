package com.mealwise.ai.assist;

import com.mealwise.food.entity.Food;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 口语数量 → 克数（纯函数，无 IO）。
 *
 * <p>规则：显式克数直接用；"碗/份/个/杯…"乘以食物库的 {@code unit_weight_g}；
 * 信息不足（没说数量、或这个食物没有单位重量）时返回 {@code ambiguous}，
 * <b>绝不瞎猜</b>——让上层生成追问。
 */
public final class PortionResolver {

    /** 语法单位：克/毫升 直接当克算（水/奶密度近似 1）。 */
    private static final Pattern GRAM_UNIT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?|[一二两三四五六七八九十]+|半)\\s*(克|g|G|毫升|ml|ML)");

    /** 口语单位：需要乘食物的单位重量。 */
    private static final Pattern SERVING_UNIT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?|[一二两三四五六七八九十]+|半)\\s*(碗|份|个|杯|根|片|把|罐|盒|袋|包|勺)");

    /**
     * @param grams     换算后的克数；ambiguity=true 时可能为 null
     * @param ambiguous 是否需要追问
     * @param hint      追问时给用户看的提示
     */
    public record Portion(BigDecimal grams, boolean ambiguous, String hint) {
        static Portion ok(BigDecimal grams) {
            return new Portion(grams.setScale(1, RoundingMode.HALF_UP), false, null);
        }

        static Portion ask(String hint) {
            return new Portion(null, true, hint);
        }
    }

    private PortionResolver() {
    }

    public static Portion resolve(String quantityText, Food food) {
        if (quantityText == null || quantityText.isBlank()) {
            return Portion.ask("没有说吃多少，请补充克数或份数（例如「200 克」「一碗」）");
        }
        String text = quantityText.trim();

        Matcher gram = GRAM_UNIT.matcher(text);
        if (gram.find()) {
            BigDecimal amount = MealTextParser.parseChineseNumber(gram.group(1));
            if (amount != null && amount.signum() > 0) {
                return Portion.ok(amount);
            }
        }

        Matcher serving = SERVING_UNIT.matcher(text);
        if (serving.find()) {
            BigDecimal amount = MealTextParser.parseChineseNumber(serving.group(1));
            if (amount == null || amount.signum() <= 0) {
                return Portion.ask("份数没看懂，请补充克数");
            }
            // "一碗半" → 1.5
            if (text.contains("半") && !"半".equals(serving.group(1))) {
                amount = amount.add(new BigDecimal("0.5"));
            }
            BigDecimal unitWeight = food == null ? null : food.getUnitWeightG();
            if (unitWeight == null || unitWeight.signum() <= 0) {
                return Portion.ask("「" + serving.group(2) + "」这个食物没有标准重量，请补充克数");
            }
            return Portion.ok(amount.multiply(unitWeight));
        }

        // 只有数字没有单位（"两个"会命中上面的"个"，这里是 "米饭 2" 这类）
        if (text.matches("\\d+(?:\\.\\d+)?") || text.matches("[一二两三四五六七八九十]+")) {
            return Portion.ask("数量缺少单位，请补充克数（如「200 克」）或份数（如「一碗」）");
        }

        return Portion.ask("没看懂「" + text + "」，请补充克数或份数");
    }
}
