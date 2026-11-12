package com.mealwise.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mealwise.ai.AiProperties;
import com.mealwise.ai.agent.dto.AgentChatRequest;
import com.mealwise.ai.agent.vo.AgentChatResponse;
import com.mealwise.food.service.FoodService;
import com.mealwise.meal.service.MealService;
import com.mealwise.nutrition.service.NutritionTargetService;
import com.mealwise.recommendation.service.ReplacementService;
import com.mealwise.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 编排层的边界：没有密钥不调用模型、不假装成功；sessionId 语义正确。
 */
class MealAgentServiceTest {

    private MealAgentService service(AiProperties properties) {
        return new MealAgentService(
                properties,
                mock(FoodService.class),
                mock(MealService.class),
                mock(ReplacementService.class),
                mock(UserService.class),
                mock(NutritionTargetService.class),
                new AgentMemoryStore());
    }

    @Test
    @DisplayName("没有密钥 → source=disabled，不调用模型")
    void disabledWithoutKey() {
        MealAgentService service = service(new AiProperties(false, "", "https://api.deepseek.com", "deepseek-chat", 60_000, 15_000));

        AgentChatResponse response = service.chat(1L, new AgentChatRequest(null, "午饭吃了两碗米饭"));

        assertThat(response.source()).isEqualTo("disabled");
        assertThat(response.reply()).contains("未配置");
        assertThat(response.sessionId()).isNotBlank();
    }

    @Test
    @DisplayName("空消息 → 直接提示，不发起模型调用")
    void blankMessage() {
        MealAgentService service = service(new AiProperties(true, "sk-test", "https://api.deepseek.com", "deepseek-chat", 60_000, 15_000));

        AgentChatResponse response = service.chat(1L, new AgentChatRequest("s1", "  "));

        assertThat(response.sessionId()).isEqualTo("s1");
        assertThat(response.source()).isEqualTo("llm");
        assertThat(response.reply()).contains("告诉我");
    }

    @Test
    @DisplayName("sessionId 会原样回传，供下一轮续聊")
    void keepsSessionId() {
        MealAgentService service = service(new AiProperties(false, "", "https://api.deepseek.com", "deepseek-chat", 60_000, 15_000));

        AgentChatResponse response = service.chat(1L, new AgentChatRequest("abc-123", "你好"));

        assertThat(response.sessionId()).isEqualTo("abc-123");
    }
}
