package com.mealwise.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mealwise.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiQuotaServiceTest {

    private AiQuotaService quota;

    @BeforeEach
    void setUp() {
        quota = new AiQuotaService();
    }

    @Test
    @DisplayName("每分钟 20 次以内放行")
    void allowsWithinLimit() {
        for (int i = 0; i < 20; i++) {
            quota.check(1L);
        }
        assertThat(quota.tokensToday()).isZero();
    }

    @Test
    @DisplayName("第 21 次触发 429")
    void blocksOverLimit() {
        for (int i = 0; i < 20; i++) {
            quota.check(1L);
        }

        assertThatThrownBy(() -> quota.check(1L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(429));
    }

    @Test
    @DisplayName("限流按用户隔离：A 打满不影响 B")
    void limitsArePerUser() {
        for (int i = 0; i < 20; i++) {
            quota.check(1L);
        }

        quota.check(2L);
        assertThatThrownBy(() -> quota.check(1L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("token 消耗按全局累计")
    void recordsTokens() {
        quota.recordTokens(1200);
        quota.recordTokens(800);

        assertThat(quota.tokensToday()).isEqualTo(2000);
    }
}
