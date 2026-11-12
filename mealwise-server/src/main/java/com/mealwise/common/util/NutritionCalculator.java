package com.mealwise.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 营养换算。
 *
 * <p>统一口径：{@code 值 = per100g × 克数 / 100}，最后一步才 {@code setScale(1, HALF_UP)}。
 * 中间不截断，避免"每项先四舍五入再加总"与"先加总再四舍五入"出现 0.1 的差。
 */
public final class NutritionCalculator {

    public static final BigDecimal HUNDRED = new BigDecimal("100");
    public static final int SCALE = 1;

    private NutritionCalculator() {
    }

    /** 按每 100g 的值换算实际克数下的值，保留 1 位小数。 */
    public static BigDecimal scale(BigDecimal per100g, BigDecimal quantityG) {
        if (per100g == null || quantityG == null) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return per100g.multiply(quantityG)
                .divide(HUNDRED, SCALE + 4, RoundingMode.HALF_UP)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 加法并统一保留 1 位小数。 */
    public static BigDecimal plus(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return left.add(right).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
