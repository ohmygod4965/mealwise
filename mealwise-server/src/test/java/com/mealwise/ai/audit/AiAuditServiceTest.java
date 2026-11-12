package com.mealwise.ai.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiAuditServiceTest {

    @Mock
    private AiAuditLogMapper mapper;

    @Test
    @DisplayName("正常写入：字段完整、detail 截断到 200 字")
    void recordsEntry() {
        AiAuditService service = new AiAuditService(mapper);
        String longDetail = "x".repeat(500);

        service.record(7L, "ask", "llm", "success", 123L, longDetail);

        ArgumentCaptor<AiAuditLog> captor = ArgumentCaptor.forClass(AiAuditLog.class);
        verify(mapper).insert(captor.capture());
        AiAuditLog entry = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(entry.getUserId()).isEqualTo(7L);
        org.assertj.core.api.Assertions.assertThat(entry.getFeature()).isEqualTo("ask");
        org.assertj.core.api.Assertions.assertThat(entry.getLatencyMs()).isEqualTo(123L);
        org.assertj.core.api.Assertions.assertThat(entry.getDetail()).hasSize(200);
    }

    @Test
    @DisplayName("审计写失败不能影响业务：异常被吞掉")
    void swallowsInsertFailure() {
        when(mapper.insert(any(AiAuditLog.class))).thenThrow(new RuntimeException("db down"));
        AiAuditService service = new AiAuditService(mapper);

        assertThatCode(() -> service.record(1L, "parse-meal", "rule", "success", 10L, null))
                .doesNotThrowAnyException();
    }
}
