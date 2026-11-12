package com.mealwise.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求生成/透传 traceId，写入 MDC 与响应头，便于日志串联。
 *
 * <p>优先使用调用方传入的 {@code X-Trace-Id}（便于网关或前端透传），否则生成新的。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(TraceIdHolder.HEADER);
        String traceId = (incoming != null && !incoming.isBlank()) ? incoming.trim() : TraceIdHolder.newTraceId();
        TraceIdHolder.set(traceId);
        response.setHeader(TraceIdHolder.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            TraceIdHolder.clear();
        }
    }
}
