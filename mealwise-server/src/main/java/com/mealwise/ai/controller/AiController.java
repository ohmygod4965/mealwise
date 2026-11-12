package com.mealwise.ai.controller;

import com.mealwise.ai.AiCache;
import com.mealwise.ai.AiMetrics;
import com.mealwise.ai.AiQuotaService;
import com.mealwise.ai.agent.MealAgentService;
import com.mealwise.ai.agent.dto.AgentChatRequest;
import com.mealwise.ai.agent.vo.AgentChatResponse;
import com.mealwise.ai.assist.MealParseService;
import com.mealwise.ai.assist.NutritionQaService;
import com.mealwise.ai.assist.vo.AskRequest;
import com.mealwise.ai.assist.vo.AskResponseVO;
import com.mealwise.ai.assist.vo.ParseMealRequest;
import com.mealwise.ai.assist.vo.ParsedMealVO;
import com.mealwise.ai.audit.AiAuditService;
import com.mealwise.ai.dto.AiMealPlanRequest;
import com.mealwise.ai.service.AiMealPlanService;
import com.mealwise.ai.vo.AiPlanVO;
import com.mealwise.common.api.Result;
import com.mealwise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 相关接口。
 *
 * <p>共同的边界：<b>模型只做语言理解，热量与规则计算全部在 Java 侧</b>；
 * 无密钥/调用失败时一律降级到本地规则，接口不报 5xx。
 *
 * <p>每个入口统一经过 {@link #measure}：限流 → 计时 → 指标 → 审计。
 */
@Tag(name = "06. AI 能力", description = "饮食方案 / 自然语言录餐 / 营养问答 / Agent")
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final long ASK_CACHE_MS = 10 * 60_000L;

    private final AiMealPlanService aiMealPlanService;
    private final MealParseService mealParseService;
    private final NutritionQaService nutritionQaService;
    private final MealAgentService mealAgentService;
    private final AiQuotaService quota;
    private final AiMetrics metrics;
    private final AiAuditService audit;
    private final AiCache cache;

    public AiController(AiMealPlanService aiMealPlanService,
                        MealParseService mealParseService,
                        NutritionQaService nutritionQaService,
                        MealAgentService mealAgentService,
                        AiQuotaService quota,
                        AiMetrics metrics,
                        AiAuditService audit,
                        AiCache cache) {
        this.aiMealPlanService = aiMealPlanService;
        this.mealParseService = mealParseService;
        this.nutritionQaService = nutritionQaService;
        this.mealAgentService = mealAgentService;
        this.quota = quota;
        this.metrics = metrics;
        this.audit = audit;
        this.cache = cache;
    }

    @Operation(summary = "生成 AI 饮食方案",
            description = "按目标（减脂/维持/增肌）生成三餐，并联网核验每样食物的热量；无密钥时降级为本地搭配")
    @PostMapping("/meal-plan")
    public Result<AiPlanVO> plan(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody AiMealPlanRequest request) {
        return Result.ok(measure("meal-plan", principal.id(), () -> aiMealPlanService.plan(principal.id(), request)));
    }

    @Operation(summary = "自然语言录餐",
            description = "「中午吃了两碗米饭和一份鸡胸肉」→ 结构化食物项；匹配不到或克数不明时返回追问（status=NEED_CLARIFICATION）")
    @PostMapping("/parse-meal")
    public Result<ParsedMealVO> parseMeal(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody ParseMealRequest request) {
        return Result.ok(measure("parse-meal", principal.id(), () -> mealParseService.parse(principal.id(), request)));
    }

    @Operation(summary = "营养知识问答（小型 RAG）",
            description = "先用 BM25 在食物库 + 营养常识里检索，再让模型只依据资料回答；无密钥时返回检索要点")
    @PostMapping("/ask")
    public Result<AskResponseVO> ask(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody AskRequest request) {
        String question = request == null ? null : request.question();
        String cacheKey = "ask:" + sha256(question == null ? "" : question);
        Object cached = cache.get(cacheKey).orElse(null);
        if (cached instanceof AskResponseVO hit) {
            return Result.ok(hit);
        }
        AskResponseVO fresh = measure("ask", principal.id(), () -> nutritionQaService.ask(question));
        cache.put(cacheKey, fresh, ASK_CACHE_MS);
        return Result.ok(fresh);
    }

    @Operation(summary = "Agent 对话（LangChain4j @Tool 编排）",
            description = "模型自主决定调用 searchFood / createMeal / addMealItem / calculateCalories / getReplacements 的顺序；"
                    + "缺参数会追问，多轮记忆保留在 sessionId 维度")
    @PostMapping("/agent")
    public Result<AgentChatResponse> agent(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody AgentChatRequest request) {
        return Result.ok(measure("agent", principal.id(), () -> mealAgentService.chat(principal.id(), request)));
    }

    @Operation(summary = "重置 Agent 会话记忆")
    @DeleteMapping("/agent/{sessionId}")
    public Result<Void> resetAgent(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable String sessionId) {
        mealAgentService.reset(principal.id(), sessionId);
        return Result.ok();
    }

    /**
     * 统一的 AI 调用护栏：限流（超限 429）→ 计时 → 指标 → 审计。
     * 业务异常照常抛出（由全局处理器映射状态码），成功与失败都会留痕。
     */
    private <T> T measure(String feature, long userId, java.util.function.Supplier<T> call) {
        quota.check(userId);
        long start = System.currentTimeMillis();
        try {
            T result = call.get();
            long elapsed = System.currentTimeMillis() - start;
            String source = sourceOf(result);
            metrics.success(feature, source, elapsed);
            audit.record(userId, feature, source, "success", elapsed, null);
            return result;
        } catch (RuntimeException ex) {
            long elapsed = System.currentTimeMillis() - start;
            metrics.failure(feature, ex.getClass().getSimpleName(), elapsed);
            audit.record(userId, feature, "unknown", "failure", elapsed,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        }
    }

    /** 各个 AI 响应都带 source（llm / rule / retrieval / disabled），用于指标与审计分桶。 */
    private static String sourceOf(Object result) {
        if (result instanceof ParsedMealVO vo) {
            return vo.source();
        }
        if (result instanceof AskResponseVO vo) {
            return vo.source();
        }
        if (result instanceof AgentChatResponse vo) {
            return vo.source();
        }
        return "unknown";
    }

    /** 问题文本只用于做缓存键，不落库。 */
    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
