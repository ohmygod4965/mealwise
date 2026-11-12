package com.mealwise.ai.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * AI 调用审计：一次请求一行，用于回答"谁、在什么时候、让 AI 做了什么、花了多久"。
 *
 * <p>只记元数据（feature / source / 结果 / 延迟），不落用户输入的原文，
 * 避免把健康相关的敏感文本再存一份。
 */
@TableName("ai_audit_log")
public class AiAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** parse-meal / explain / ask / agent / meal-plan */
    private String feature;

    /** llm / rule / retrieval / disabled */
    private String source;

    /** success / failure */
    private String result;

    private Long latencyMs;

    /** 失败原因或补充信息（不含用户原文） */
    private String detail;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
