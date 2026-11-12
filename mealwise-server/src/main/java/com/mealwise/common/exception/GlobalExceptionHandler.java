package com.mealwise.common.exception;

import com.mealwise.common.api.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：把各类异常翻译成统一响应体 + 正确的 HTTP 状态码。
 *
 * <p>顺序原则：越具体的异常越先匹配（Spring 会选最匹配的 handler）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：码即状态码。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        log.warn("biz error: code={}, message={}", ex.getCode(), ex.getMessage());
        return build(ex.getCode(), ex.getMessage());
    }

    /** @RequestBody 校验失败：取第一条字段错误，附带字段名。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return build(ErrorCode.PARAM_ERROR.getCode(), message.isBlank() ? "参数校验失败" : message);
    }

    /** 表单/查询参数绑定失败。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return build(ErrorCode.PARAM_ERROR.getCode(), message.isBlank() ? "参数校验失败" : message);
    }

    /** 方法参数上的约束（@RequestParam + @Min 等）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(this::formatViolation)
                .collect(Collectors.joining("; "));
        return build(ErrorCode.PARAM_ERROR.getCode(), message.isBlank() ? "参数校验失败" : message);
    }

    /** 请求体不可解析（JSON 语法错误、类型不匹配、body 为空）。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        // 把底层原因写进日志（Jackson 只带行列号，不回显 body 内容，安全）
        log.warn("unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.PARAM_ERROR.getCode(), "请求体格式错误，请检查 JSON");
    }

    /**
     * Content-Type 不被支持（例如把 JSON 当 text/plain 或 form-urlencoded 发）。
     *
     * <p>必须单独处理：否则会被下面的 {@code Exception} 兜底成 500，
     * 让调用方误以为"服务端挂了"，实际是自己发错了类型。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        String contentType = ex.getContentType() == null ? "未提供" : ex.getContentType().toString();
        log.warn("unsupported media type: {}", contentType);
        return build(415, "不支持的 Content-Type：" + contentType + "，请使用 application/json");
    }

    /**
     * 数据库约束冲突（列长度/精度、唯一键、外键）。
     *
     * <p>校验漏网的数值如果直接抛到兜底处理器会变成 500，让调用方误以为服务崩溃。
     * 这里统一降级为 400，并只记录原因不外泄细节。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("data integrity violated: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.PARAM_ERROR.getCode(), "数据超出允许范围或违反约束");
    }

    /** Accept 头要求的响应类型无法满足。 */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<Void>> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return build(406, "无法按 Accept 指定的类型返回，本服务只返回 application/json");
    }

    /** 缺少必填查询参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(ErrorCode.PARAM_ERROR.getCode(), "缺少参数: " + ex.getParameterName());
    }

    /** 路径/查询参数类型不匹配（如 id 传了字母）。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        // 记录原始值，便于排查"6 为什么转不成 long"这类问题
        log.warn("type mismatch: name={}, value={}, requiredType={}, parameter={}",
                ex.getName(), ex.getValue(),
                ex.getRequiredType() == null ? "?" : ex.getRequiredType().getName(),
                ex.getParameter() == null ? "?" : ex.getParameter().getParameterName());
        return build(ErrorCode.PARAM_ERROR.getCode(), "参数类型错误: " + ex.getName());
    }

    /** 已登录但无权限（方法级 @PreAuthorize 等）。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage());
    }

    /** 兜底：记录完整堆栈（带 traceId），对外只给通用文案。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected error", ex);
        return build(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String formatViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        int idx = path.lastIndexOf('.');
        String field = idx >= 0 ? path.substring(idx + 1) : path;
        return (field.isBlank() ? "" : field + ": ") + violation.getMessage();
    }

    private ResponseEntity<Result<Void>> build(int code, String message) {
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(Result.fail(code, message));
    }
}
