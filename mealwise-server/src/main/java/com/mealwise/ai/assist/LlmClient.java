package com.mealwise.ai.assist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealwise.ai.AiCircuitBreaker;
import com.mealwise.ai.AiMetrics;
import com.mealwise.ai.AiProperties;
import com.mealwise.ai.AiQuotaService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 运行时大模型调用的最小封装（OpenAI 兼容 {@code /chat/completions}）。
 *
 * <p>只做三件事：拼请求、发请求、取出 {@code choices[0].message.content}。
 * <b>不含任何业务语义</b>——解析、匹配、热量计算都在 Java 侧，模型只负责"把话变成结构化文本"。
 * 没有配置密钥时 {@link #usable()} 为 false，调用方必须自己降级。
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final AiProperties properties;
    private final ObjectMapper mapper;
    private final AiMetrics metrics;
    private final AiQuotaService quota;
    private final AiCircuitBreaker breaker;
    private final HttpClient http;

    public LlmClient(AiProperties properties, ObjectMapper mapper, AiMetrics metrics,
                     AiQuotaService quota, AiCircuitBreaker breaker) {
        this.properties = properties;
        this.mapper = mapper;
        this.metrics = metrics;
        this.quota = quota;
        this.breaker = breaker;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    /** 有密钥且启用时才值得调用；否则调用方应走本地规则。 */
    public boolean usable() {
        return properties.usable();
    }

    public String model() {
        return properties.model();
    }

    /**
     * 要求模型返回 JSON 对象（DeepSeek / OpenAI 的 {@code response_format=json_object}）。
     *
     * @throws LlmException 网络失败、非 200、空内容
     */
    public String json(String system, String user, double temperature, int maxTokens) {
        return chat(system, user, temperature, maxTokens, true);
    }

    /**
     * 普通文本补全（用于"润色一句话"这类不需要 JSON 的场景）。
     */
    public String text(String system, String user, double temperature, int maxTokens) {
        return chat(system, user, temperature, maxTokens, false);
    }

    private String chat(String system, String user, double temperature, int maxTokens, boolean jsonMode) {
        if (!usable()) {
            throw new LlmException("未配置大模型密钥");
        }
        if (breaker != null && !breaker.allow("llm")) {
            throw new LlmException("模型暂时不可用（熔断中），已自动降级");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.model());
            payload.put("temperature", temperature);
            payload.put("max_tokens", maxTokens);
            if (jsonMode) {
                payload.put("response_format", Map.of("type", "json_object"));
            }
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", user)));

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(trimSlash(properties.baseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("模型返回 HTTP " + response.statusCode());
            }
            JsonNode body = mapper.readTree(response.body());
            String content = body.path("choices").path(0).path("message").path("content").asText("");
            if (content == null || content.isBlank()) {
                throw new LlmException("模型返回空内容");
            }
            // 成本可观测：tokens 进指标 + 全局日预算
            long promptTokens = body.path("usage").path("prompt_tokens").asLong(0);
            long completionTokens = body.path("usage").path("completion_tokens").asLong(0);
            if (metrics != null) {
                metrics.tokens("llm", promptTokens, completionTokens);
            }
            if (quota != null) {
                quota.recordTokens(promptTokens + completionTokens);
            }
            if (breaker != null) {
                breaker.onSuccess("llm");
            }
            return content;
        } catch (LlmException ex) {
            if (breaker != null) {
                breaker.onFailure("llm");
            }
            throw ex;
        } catch (Exception ex) {
            log.warn("llm call failed: {}", ex.toString());
            if (breaker != null) {
                breaker.onFailure("llm");
            }
            throw new LlmException("调用大模型失败: " + ex.getMessage());
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 调用失败统一异常，调用方只需 catch 这一个。 */
    public static class LlmException extends RuntimeException {
        public LlmException(String message) {
            super(message);
        }
    }
}
