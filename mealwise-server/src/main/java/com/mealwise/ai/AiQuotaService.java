package com.mealwise.ai;

import com.mealwise.common.exception.BizException;
import com.mealwise.common.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * AI 调用的护栏：按用户限流 + 全局日 token 预算。
 *
 * <p>为什么放内存：单实例演示项目，不引入 Redis；多实例部署时换成 Redis 计数即可，
 * 接口（{@link #check(long)} / {@link #recordTokens(long)}）不用改。
 */
@Component
public class AiQuotaService {

    /** 每用户每分钟最多 20 次 AI 请求（解析/解释/问答/Agent 共用）。 */
    static final int PER_MINUTE = 20;
    /** 全局每日 token 上限，防止一个账号把 key 刷爆。 */
    static final long DAILY_TOKEN_LIMIT = 500_000;

    private static final long WINDOW_MS = 60_000L;

    private final Map<Long, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final Map<Long, long[]> dailyTokens = new ConcurrentHashMap<>();

    /** 请求前置检查：超限抛 429。 */
    public void check(long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> window = windows.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= PER_MINUTE) {
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "AI 调用太频繁（每分钟最多 " + PER_MINUTE + " 次），请稍后再试");
            }
            window.addLast(now);
        }
        if (tokensToday() >= DAILY_TOKEN_LIMIT) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "今日 AI 额度已用完，明天再来或联系管理员提高上限");
        }
    }

    /** 调用结束后累加 token 消耗（全局）。 */
    public void recordTokens(long tokens) {
        if (tokens <= 0) {
            return;
        }
        long today = LocalDate.now().toEpochDay();
        long[] slot = dailyTokens.computeIfAbsent(0L, k -> new long[]{today, 0});
        synchronized (slot) {
            if (slot[0] != today) {
                slot[0] = today;
                slot[1] = 0;
            }
            slot[1] += tokens;
        }
    }

    /** 今日已消耗 token（全局）。 */
    public long tokensToday() {
        long[] slot = dailyTokens.get(0L);
        if (slot == null) {
            return 0;
        }
        synchronized (slot) {
            return slot[0] == LocalDate.now().toEpochDay() ? slot[1] : 0;
        }
    }

    /** 仅测试用：清空所有计数。 */
    void reset() {
        windows.clear();
        dailyTokens.clear();
    }
}
