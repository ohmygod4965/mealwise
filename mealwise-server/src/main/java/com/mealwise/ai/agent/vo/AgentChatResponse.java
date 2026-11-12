package com.mealwise.ai.agent.vo;

/**
 * Agent 对话响应。
 *
 * @param sessionId 会话 id（调用方下次带上它可续聊）
 * @param reply     助手回复
 * @param source    llm（模型编排）/ disabled（未配置密钥，未调用模型）
 */
public record AgentChatResponse(String sessionId, String reply, String source) {
}
