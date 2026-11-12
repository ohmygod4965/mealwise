package com.mealwise.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mealwise.common.exception.ErrorCode;
import com.mealwise.common.web.TraceIdHolder;

/**
 * 统一响应体：{@code {code, message, data, traceId}}。
 *
 * <p>约定：{@code code} 与 HTTP 状态码保持一致，前端拦截器先看 HTTP 状态、再看业务码，
 * 避免"HTTP 200 里塞错误"这种要解析两次的写法。
 *
 * <p>{@code data} 为 null 时不序列化（{@code NON_NULL}），避免前端拿到无意义空字段。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(int code, String message, T data, String traceId) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getDefaultMessage(), data, TraceIdHolder.get());
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getDefaultMessage());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, TraceIdHolder.get());
    }
}
