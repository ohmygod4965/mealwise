package com.mealwise.ai.agent;

import com.mealwise.ai.AiProperties;
import com.mealwise.ai.agent.dto.AgentChatRequest;
import com.mealwise.ai.agent.vo.AgentChatResponse;
import com.mealwise.food.service.FoodService;
import com.mealwise.meal.service.MealService;
import com.mealwise.nutrition.service.NutritionTargetService;
import com.mealwise.nutrition.vo.NutritionTargetVO;
import com.mealwise.recommendation.service.ReplacementService;
import com.mealwise.user.entity.SysUser;
import com.mealwise.user.service.UserService;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent 编排入口：LangChain4j 负责「让模型自己决定调哪个工具、调几次」，业务仍落在现有 Service。
 *
 * <p>关键约束：
 * <ul>
 *   <li>工具实例按请求构造，{@code userId} 由登录态注入，模型没有入口去改；</li>
 *   <li>模型不碰热量公式与替换排序——那两条链路在 {@link MealService} / {@link ReplacementService}；</li>
 *   <li>没有密钥时不建模型、不给假答案，直接返回 {@code source=disabled}；</li>
 *   <li>记忆按「用户 + 会话」隔离，过敏原、减脂/增重偏好就在对话历史里延续。</li>
 * </ul>
 */
@Service
public class MealAgentService {

    private static final Logger log = LoggerFactory.getLogger(MealAgentService.class);

    private final AiProperties properties;
    private final FoodService foodService;
    private final MealService mealService;
    private final ReplacementService replacementService;
    private final UserService userService;
    private final NutritionTargetService targetService;
    private final AgentMemoryStore memoryStore;

    private volatile ChatModel chatModel;

    public MealAgentService(AiProperties properties,
                            FoodService foodService,
                            MealService mealService,
                            ReplacementService replacementService,
                            UserService userService,
                            NutritionTargetService targetService,
                            AgentMemoryStore memoryStore) {
        this.properties = properties;
        this.foodService = foodService;
        this.mealService = mealService;
        this.replacementService = replacementService;
        this.userService = userService;
        this.targetService = targetService;
        this.memoryStore = memoryStore;
    }

    public AgentChatResponse chat(long userId, AgentChatRequest request) {
        String sessionId = request == null || request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId().trim();
        String message = request == null ? null : request.message();

        if (!properties.usable()) {
            return new AgentChatResponse(sessionId,
                    "未配置大模型密钥，Agent 暂不可用；可以先用 /api/ai/parse-meal（规则解析）录餐。",
                    "disabled");
        }
        if (message == null || message.isBlank()) {
            return new AgentChatResponse(sessionId, "先告诉我你想做什么，例如「午饭吃了两碗米饭和一份鸡胸肉」。", "llm");
        }

        MealAgentTools tools = new MealAgentTools(userId, foodService, mealService, replacementService, userService);
        String systemPrompt = systemPrompt(userId);
        ChatMemory memory = memoryStore.get(userId, sessionId);

        MealAgent agent = AiServices.builder(MealAgent.class)
                .chatModel(model())
                .tools(tools)
                .chatMemoryProvider(id -> memory)
                .systemMessageProvider(id -> systemPrompt)
                .build();

        String reply = agent.chat(sessionId, message);
        return new AgentChatResponse(sessionId, reply, "llm");
    }

    /** 清空某个会话的记忆（"重新开始"）。 */
    public void reset(long userId, String sessionId) {
        memoryStore.clear(userId, sessionId);
    }

    private ChatModel model() {
        ChatModel existing = chatModel;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (chatModel == null) {
                chatModel = OpenAiChatModel.builder()
                        .baseUrl(properties.baseUrl())
                        .apiKey(properties.apiKey())
                        .modelName(properties.model())
                        .temperature(0.2)
                        .timeout(Duration.ofMillis(properties.timeoutMs()))
                        .logRequests(false)
                        .logResponses(false)
                        .build();
            }
            return chatModel;
        }
    }

    /**
     * 系统提示：把用户档案（过敏原、目标热量）作为"已知事实"交给模型，并要求它按工具流程办事。
     */
    private String systemPrompt(long userId) {
        SysUser user = userService.requireById(userId);
        List<String> allergens = userService.allergensOf(userId);
        NutritionTargetVO target = targetService.compute(user);

        StringBuilder sb = new StringBuilder();
        sb.append("你是 Mealwise 的饮食记录助手，用中文回答，语气简洁。\n");
        sb.append("今天是 ").append(LocalDate.now()).append("。\n");
        sb.append("用户档案：\n");
        sb.append("- 过敏原：").append(allergens.isEmpty() ? "无" : String.join(",", allergens)).append('\n');
        if (target != null) {
            sb.append("- 每日目标：").append(target.calories()).append(" kcal，蛋白质 ")
                    .append(target.protein()).append("g，脂肪 ").append(target.fat())
                    .append("g，碳水 ").append(target.carb()).append("g\n");
        } else {
            sb.append("- 每日目标：资料不全，无法计算\n");
        }
        sb.append("""
                工作规则：
                1. 只能通过工具完成数据操作：searchFood 查食物、createMeal 建餐、addMealItem 加食物、
                   calculateCalories 算热量、getReplacements 看替换建议。不要凭记忆编 foodId 或 mealId。
                2. 参数缺失就先向用户追问，一次只问缺的那一个（例如没说克数就问克数，没说哪一餐就问哪一餐）。
                3. 用户提到过敏、忌口、减脂或增重时，要记住并在此后所有推荐中遵守；可用 searchFood 的
                   extraAllergens 参数排除本次新提到的过敏原。减脂优先 lower 策略，增重可看 higher。
                4. 热量与替换结果一律以工具返回为准，不要自己计算或改写数字。
                5. 加完食物后主动调用 calculateCalories 汇报结果；用户问"怎么换"时调用 getReplacements。
                6. 工具返回「错误:」时，把它翻译成给用户的话，并给出下一步建议，不要编造成功。
                """);
        return sb.toString();
    }
}
