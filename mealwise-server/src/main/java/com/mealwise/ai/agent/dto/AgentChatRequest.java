package com.mealwise.ai.agent.dto;

/**
 * Agent 对话请求。
 *
 * @param sessionId 会话 id（为空则服务端生成）；同一用户 + 同一 sessionId 共享记忆
 * @param message   用户自然语言
 */
public record AgentChatRequest(String sessionId, String message) {
}
