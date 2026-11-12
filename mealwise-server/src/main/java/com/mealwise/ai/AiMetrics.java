package com.mealwise.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * AI 调用的指标出口（Micrometer）。
 *
 * <p>暴露在 {@code /actuator/metrics} 与 {@code /actuator/prometheus}：
 * <ul>
 *   <li>{@code mealwise_ai_calls_total{feature,source,result}} —— 调用量与成败（source=llm/rule/disabled）</li>
 *   <li>{@code mealwise_ai_latency_seconds{feature}} —— P95/P99 延迟</li>
 *   <li>{@code mealwise_ai_tokens_total{feature,kind}} —— prompt / completion token 消耗</li>
 * </ul>
 */
@Component
public class AiMetrics {

    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 记录一次成功调用。 */
    public void success(String feature, String source, long elapsedMs) {
        Counter.builder("mealwise.ai.calls")
                .tag("feature", feature)
                .tag("source", source == null ? "unknown" : source)
                .tag("result", "success")
                .register(registry)
                .increment();
        Timer.builder("mealwise.ai.latency")
                .tag("feature", feature)
                .register(registry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    /** 记录一次失败调用（已降级也算 success，只有真正抛错才走这里）。 */
    public void failure(String feature, String reason, long elapsedMs) {
        Counter.builder("mealwise.ai.calls")
                .tag("feature", feature)
                .tag("source", "unknown")
                .tag("result", "failure")
                .tag("reason", reason == null ? "unknown" : reason)
                .register(registry)
                .increment();
        Timer.builder("mealwise.ai.latency")
                .tag("feature", feature)
                .register(registry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    /** 记录 token 消耗（模型返回 usage 时调用）。 */
    public void tokens(String feature, long promptTokens, long completionTokens) {
        if (promptTokens > 0) {
            Counter.builder("mealwise.ai.tokens")
                    .tag("feature", feature)
                    .tag("kind", "prompt")
                    .register(registry)
                    .increment(promptTokens);
        }
        if (completionTokens > 0) {
            Counter.builder("mealwise.ai.tokens")
                    .tag("feature", feature)
                    .tag("kind", "completion")
                    .register(registry)
                    .increment(completionTokens);
        }
    }
}
