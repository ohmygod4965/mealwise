package com.mealwise.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * Agent 的"脸"：只声明一次对话入口，工具调用由 LangChain4j 按 @Tool 自动编排。
 *
 * <p>方法体不存在——{@code AiServices} 在运行时生成代理；
 * 具体能干什么全部写在 {@link MealAgentTools} 上。
 */
public interface MealAgent {

    /**
     * 一次用户消息。
     *
     * @param sessionId 多轮记忆的 key（同一用户同一会话复用）
     * @param message   用户自然语言
     * @return 助手回复（可能经历多轮工具调用后得出）
     */
    String chat(@MemoryId String sessionId, @UserMessage String message);
}
