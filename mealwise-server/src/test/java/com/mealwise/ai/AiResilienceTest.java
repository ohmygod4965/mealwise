package com.mealwise.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiResilienceTest {

    @Test
    @DisplayName("缓存：命中 / 过期 / 清理")
    void cacheBehaviour() throws Exception {
        AiCache cache = new AiCache();
        assertThat(cache.get("k")).isEmpty();

        cache.put("k", "v", 50);
        assertThat(cache.get("k")).contains("v");

        Thread.sleep(80);
        assertThat(cache.get("k")).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("熔断：连续 3 次失败后打开，成功后复位")
    void breakerOpensAndResets() {
        AtomicLong now = new AtomicLong(1_000);
        AiCircuitBreaker breaker = new AiCircuitBreaker(now::get);

        assertThat(breaker.allow("llm")).isTrue();
        breaker.onFailure("llm");
        breaker.onFailure("llm");
        assertThat(breaker.isOpen("llm")).isFalse();
        breaker.onFailure("llm");
        assertThat(breaker.isOpen("llm")).isTrue();
        assertThat(breaker.allow("llm")).isFalse();

        // 60 秒后自动半开
        now.addAndGet(61_000);
        assertThat(breaker.allow("llm")).isTrue();

        breaker.onSuccess("llm");
        assertThat(breaker.isOpen("llm")).isFalse();
    }

    @Test
    @DisplayName("熔断按 feature 隔离：parse 挂了不影响 ask")
    void breakerIsolatedPerFeature() {
        AtomicLong now = new AtomicLong(1_000);
        AiCircuitBreaker breaker = new AiCircuitBreaker(now::get);

        for (int i = 0; i < 3; i++) {
            breaker.onFailure("parse");
        }

        assertThat(breaker.isOpen("parse")).isTrue();
        assertThat(breaker.isOpen("ask")).isFalse();
    }
}
