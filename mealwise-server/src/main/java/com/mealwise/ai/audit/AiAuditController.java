package com.mealwise.ai.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mealwise.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用审计查询（仅管理员）。路径受 `/api/admin/**` 的 hasRole("ADMIN") 保护。
 */
@Tag(name = "09. AI 审计（管理员）", description = "查看 AI 调用记录：谁、哪个能力、模型还是降级、耗时")
@RestController
@RequestMapping("/api/admin/ai-audit")
public class AiAuditController {

    private static final int MAX_LIMIT = 200;

    private final AiAuditLogMapper mapper;

    public AiAuditController(AiAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Operation(summary = "最近 AI 调用（倒序）")
    @GetMapping
    public Result<List<AiAuditLog>> recent(@RequestParam(defaultValue = "50") int limit) {
        int safe = Math.max(1, Math.min(MAX_LIMIT, limit));
        List<AiAuditLog> rows = mapper.selectList(new LambdaQueryWrapper<AiAuditLog>()
                .orderByDesc(AiAuditLog::getId)
                .last("LIMIT " + safe));
        return Result.ok(rows);
    }
}
