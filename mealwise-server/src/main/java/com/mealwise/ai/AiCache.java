package com.mealwise.ai;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 TTL 缓存：用于"解释/问答"这类可复用且允许短暂陈旧的 AI 结果。
 *
 * <p>单实例演示项目不引入 Redis；键里带版本/策略，过期时间短（5–10 分钟），
 * 陈旧窗口可接受。多实例部署时换成 Redis 即可，调用方不变。
 */
@org.springframework.stereotype.Component
public class AiCache {

    private record Entry(Object value, long expiresAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public Optional<Object> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > entry.expiresAt()) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void put(String key, Object value, long ttlMs) {
        if (entries.size() > 2000) {
            evictExpired();
        }
        entries.put(key, new Entry(value, System.currentTimeMillis() + ttlMs));
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }
}
