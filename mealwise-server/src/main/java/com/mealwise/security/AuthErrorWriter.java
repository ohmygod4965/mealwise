package com.mealwise.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealwise.common.api.Result;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * 在 Filter 层（还没进 MVC）时直接写统一响应体。
 *
 * <p>Security 的 401/403 发生在 DispatcherServlet 之前，{@code @RestControllerAdvice}
 * 捕获不到，所以这里手工序列化，保证前端拿到的仍是 {@code {code,message,traceId}}。
 */
@Component
public class AuthErrorWriter {

    private final ObjectMapper objectMapper;

    public AuthErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write401(HttpServletResponse response) throws IOException {
        write(response, 401, "未登录或登录已过期");
    }

    public void write403(HttpServletResponse response) throws IOException {
        write(response, 403, "无权访问该资源");
    }

    private void write(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
    }
}
