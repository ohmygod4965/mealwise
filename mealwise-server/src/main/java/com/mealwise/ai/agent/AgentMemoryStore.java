package com.mealwise.ai.agent;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 多轮记忆仓库：按「用户 + 会话」隔离，最近 20 条消息，LRU 上限 200 个会话。
 *
 * <p>只放对话历史（过敏原、减脂/增重偏好就记在这里）；业务数据仍然在数据库，
 * 记忆丢失不会影响已记录的餐次，只是助手"忘了聊过什么"。
 */
@Component
public class AgentMemoryStore {

    private static final int MAX_MESSAGES = 20;
    private static final int MAX_SESSIONS = 200;

    private final Map<String, ChatMemory> memories = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ChatMemory> eldest) {
            return size() > MAX_SESSIONS;
        }
    };

    /** 取（或创建）某个用户在某个会话里的记忆。 */
    public synchronized ChatMemory get(long userId, String sessionId) {
        String key = userId + ":" + sessionId;
        return memories.computeIfAbsent(key, k -> MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES));
    }

    /** 清空某用户某会话（用户点"新对话"时用）。 */
    public synchronized void clear(long userId, String sessionId) {
        memories.remove(userId + ":" + sessionId);
    }

    /** 仅测试/诊断用：当前缓存的会话数。 */
    public synchronized int size() {
        return memories.size();
    }
}
