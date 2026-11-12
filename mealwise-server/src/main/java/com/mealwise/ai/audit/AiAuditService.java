package com.mealwise.ai.audit;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 写 AI 调用审计。
 *
 * <p>审计失败绝不能影响业务：所有异常在这里吞掉，只留一条 warn 日志。
 */
@Service
public class AiAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiAuditService.class);
    private static final int DETAIL_MAX = 200;

    private final AiAuditLogMapper mapper;

    public AiAuditService(AiAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(long userId, String feature, String source, String result, long latencyMs, String detail) {
        try {
            AiAuditLog entry = new AiAuditLog();
            entry.setUserId(userId);
            entry.setFeature(feature);
            entry.setSource(source);
            entry.setResult(result);
            entry.setLatencyMs(latencyMs);
            entry.setDetail(truncate(detail));
            entry.setCreatedAt(LocalDateTime.now());
            mapper.insert(entry);
        } catch (Exception ex) {
            log.warn("ai audit write failed: {}", ex.toString());
        }
    }

    private static String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= DETAIL_MAX ? detail : detail.substring(0, DETAIL_MAX);
    }
}
