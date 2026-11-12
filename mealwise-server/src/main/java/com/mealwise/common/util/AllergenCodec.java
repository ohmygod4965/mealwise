package com.mealwise.common.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 过敏原编解码：库里存逗号分隔的规范化 token（如 {@code peanut,seafood}）。
 *
 * <p>规范化规则：去空白 → 转小写 → 去重 → 丢弃空串。读写两侧共用同一套规则，
 * 避免「库里存 Peanut、请求里传 peanut」匹配不上。
 */
public final class AllergenCodec {

    private static final String SEPARATOR = ",";

    private AllergenCodec() {
    }

    public static List<String> decode(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : csv.split(SEPARATOR)) {
            String token = normalize(part);
            if (token != null) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    public static String encode(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String token : tokens) {
            String value = normalize(token);
            if (value != null) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? null : String.join(SEPARATOR, normalized);
    }

    /** 转成便于集合求交的 List（保持顺序、已去重）。 */
    public static List<String> toList(String csv) {
        return new ArrayList<>(decode(csv));
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        return token.isEmpty() ? null : token;
    }
}
