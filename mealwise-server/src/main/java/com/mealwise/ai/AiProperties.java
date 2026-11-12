package com.mealwise.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 饮食推荐的配置（{@code ai.*}）。
 *
 * <p>密钥不进代码库：从环境变量或 profile 目录下的 ai-local.yml 读取。
 * 没有密钥时服务不会报错，而是降级为「本地规则搭配」并标记 source=local。
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String model,
        int timeoutMs,
        int webTimeoutMs
) {
    public AiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.deepseek.com";
        }
        if (model == null || model.isBlank()) {
            model = "deepseek-chat";
        }
        if (timeoutMs <= 0) {
            timeoutMs = 60_000;
        }
        if (webTimeoutMs <= 0) {
            webTimeoutMs = 15_000;
        }
    }

    /** 只有 enabled 且拿到密钥时才真正调用大模型。 */
    public boolean usable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
