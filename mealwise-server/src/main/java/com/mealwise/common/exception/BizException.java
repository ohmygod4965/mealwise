package com.mealwise.common.exception;

/**
 * 业务异常：携带错误码与给用户看的消息。
 *
 * <p>只用于「可预期」的失败（参数语义错误、资源不存在、越权等）；
 * 未预期异常交给全局处理器兜底成 500。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getDefaultMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode.getCode(), message);
    }

    public int getCode() {
        return code;
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(ErrorCode.UNAUTHORIZED, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }
}
