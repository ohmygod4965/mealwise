package com.mealwise.meal.controller;

import com.mealwise.ai.assist.RecommendationExplainer;
import com.mealwise.ai.assist.vo.ExplainResponseVO;
import com.mealwise.common.api.Result;
import com.mealwise.meal.dto.AddItemRequest;
import com.mealwise.meal.dto.CreateMealRequest;
import com.mealwise.meal.dto.UpdateItemRequest;
import com.mealwise.meal.service.MealService;
import com.mealwise.meal.vo.MealCaloriesVO;
import com.mealwise.meal.vo.MealItemVO;
import com.mealwise.meal.vo.MealVO;
import com.mealwise.recommendation.service.ReplacementService;
import com.mealwise.recommendation.vo.ReplacementResponse;
import com.mealwise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 餐次与推荐接口。
 *
 * <p>{@code userId} 一律来自 {@link UserPrincipal}（JWT 解析结果），
 * 不从请求体或参数读取，从根上杜绝"传别人 id"的越权。
 */
@Tag(name = "03. 餐次", description = "记录一餐、计算热量、获取替换建议")
@RestController
@RequestMapping("/api/meals")
public class MealController {

    private final MealService mealService;
    private final ReplacementService replacementService;
    private final RecommendationExplainer recommendationExplainer;
    private final com.mealwise.ai.AiQuotaService aiQuota;
    private final com.mealwise.ai.AiMetrics aiMetrics;
    private final com.mealwise.ai.AiCache aiCache;
    private final com.mealwise.ai.audit.AiAuditService aiAudit;

    public MealController(MealService mealService,
                          ReplacementService replacementService,
                          RecommendationExplainer recommendationExplainer,
                          com.mealwise.ai.AiQuotaService aiQuota,
                          com.mealwise.ai.AiMetrics aiMetrics,
                          com.mealwise.ai.AiCache aiCache,
                          com.mealwise.ai.audit.AiAuditService aiAudit) {
        this.mealService = mealService;
        this.replacementService = replacementService;
        this.recommendationExplainer = recommendationExplainer;
        this.aiQuota = aiQuota;
        this.aiMetrics = aiMetrics;
        this.aiCache = aiCache;
        this.aiAudit = aiAudit;
    }

    @Operation(summary = "创建一餐", description = "mealType: BREAKFAST/LUNCH/DINNER/SNACK；eatenAt 省略则取当前时间")
    @PostMapping
    public Result<MealVO> create(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody CreateMealRequest request) {
        return Result.ok(mealService.create(principal.id(), request));
    }

    @Operation(summary = "添加食物项", description = "克数必须大于 0；食物必须存在且上架")
    @PostMapping("/{mealId}/items")
    public Result<MealItemVO> addItem(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable long mealId,
                                      @Valid @RequestBody AddItemRequest request) {
        return Result.ok(mealService.addItem(principal.id(), mealId, request));
    }

    @Operation(summary = "修改食物项克数")
    @PutMapping("/{mealId}/items/{itemId}")
    public Result<MealItemVO> updateItem(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable long mealId,
                                         @PathVariable long itemId,
                                         @Valid @RequestBody UpdateItemRequest request) {
        return Result.ok(mealService.updateItem(principal.id(), mealId, itemId, request));
    }

    @Operation(summary = "删除食物项")
    @DeleteMapping("/{mealId}/items/{itemId}")
    public Result<Void> deleteItem(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable long mealId,
                                   @PathVariable long itemId) {
        mealService.deleteItem(principal.id(), mealId, itemId);
        return Result.ok();
    }

    @Operation(summary = "删除整餐", description = "连带删除食物项；用于撤销刚才的录入（AI 入账后可一键撤销）")
    @DeleteMapping("/{mealId}")
    public Result<Void> deleteMeal(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable long mealId) {
        mealService.delete(principal.id(), mealId);
        return Result.ok();
    }

    @Operation(summary = "热量计算", description = "返回明细与合计（热量/蛋白质/脂肪/碳水），保留 1 位小数")
    @GetMapping("/{mealId}/calories")
    public Result<MealCaloriesVO> calories(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable long mealId) {
        return Result.ok(mealService.calories(principal.id(), mealId));
    }

    @Operation(summary = "替换建议", description = "strategy=higher（默认）/lower；无候选时返回空 suggestions + message")
    @GetMapping("/{mealId}/replacements")
    public Result<ReplacementResponse> replacements(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable long mealId,
                                                    @RequestParam(defaultValue = "higher") String strategy) {
        return Result.ok(replacementService.recommend(principal.id(), mealId, strategy));
    }

    @Operation(summary = "替换建议 + 人话解释",
            description = "先跑规则引擎拿到 Top3（排序/delta 不变），再由 LLM 把理由润色成一句话；模型不可用时回退规则原文")
    @GetMapping("/{mealId}/replacements/explain")
    public Result<ExplainResponseVO> explainReplacements(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable long mealId,
                                                         @RequestParam(defaultValue = "higher") String strategy) {
        String cacheKey = "explain:" + principal.id() + ":" + mealId + ":" + strategy;
        Object cached = aiCache.get(cacheKey).orElse(null);
        if (cached instanceof ExplainResponseVO hit) {
            return Result.ok(hit);
        }

        aiQuota.check(principal.id());
        long start = System.currentTimeMillis();
        try {
            ExplainResponseVO result = recommendationExplainer.explain(principal.id(), mealId, strategy);
            long elapsed = System.currentTimeMillis() - start;
            aiMetrics.success("explain", result.source(), elapsed);
            aiAudit.record(principal.id(), "explain", result.source(), "success", elapsed, null);
            aiCache.put(cacheKey, result, 5 * 60_000L);
            return Result.ok(result);
        } catch (RuntimeException ex) {
            long elapsed = System.currentTimeMillis() - start;
            aiMetrics.failure("explain", ex.getClass().getSimpleName(), elapsed);
            aiAudit.record(principal.id(), "explain", "unknown", "failure", elapsed,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        }
    }
}
