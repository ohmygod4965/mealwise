package com.mealwise.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/**
 * 极简熔断器：连续失败 N 次后打开一段时间，期间直接走降级，不再等模型超时。
 *
 * <p>状态按 feature 隔离（parse / explain / ask / agent / llm），
 * 打开后到期自动半开（下一次调用放行，成功即复位、失败继续打开）。
 */
@Component
public class AiCircuitBreaker {

    static final int THRESHOLD = 3;
    static final long OPEN_MS = 60_000L;

    private static final class State {
        int failures;
        long openUntil;
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public AiCircuitBreaker() {
        this(System::currentTimeMillis);
    }

    AiCircuitBreaker(LongSupplier clock) {
        this.clock = clock;
    }

    /** 是否放行这次调用。 */
    public boolean allow(String feature) {
        State state = states.computeIfAbsent(feature, k -> new State());
        synchronized (state) {
            return clock.getAsLong() >= state.openUntil;
        }
    }

    public void onSuccess(String feature) {
        State state = states.computeIfAbsent(feature, k -> new State());
        synchronized (state) {
            state.failures = 0;
            state.openUntil = 0;
        }
    }

    public void onFailure(String feature) {
        State state = states.computeIfAbsent(feature, k -> new State());
        synchronized (state) {
            state.failures++;
            if (state.failures >= THRESHOLD) {
                state.openUntil = clock.getAsLong() + OPEN_MS;
            }
        }
    }

    /** 仅测试/诊断：当前是否处于打开状态。 */
    public boolean isOpen(String feature) {
        State state = states.get(feature);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return clock.getAsLong() < state.openUntil;
        }
    }
}
