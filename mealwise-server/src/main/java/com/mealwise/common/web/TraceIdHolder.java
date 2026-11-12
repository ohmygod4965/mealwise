package com.mealwise.common.web;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * traceId 的线程内持有者：Filter 写入，Result 与日志读取。
 */
public final class TraceIdHolder {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceIdHolder() {
    }

    /** 32 位无横线 UUID，够短够唯一，方便用户贴图后 grep。 */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void set(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static String get() {
        return MDC.get(MDC_KEY);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
