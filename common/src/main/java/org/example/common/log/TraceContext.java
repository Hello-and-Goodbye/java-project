package org.example.common.log;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪上下文：统一 traceId / spanId 的 MDC key 与透传请求头名。
 * <p>
 * traceId 由入口(网关)生成，通过 {@link #HEADER_TRACE_ID} 请求头向下游透传，
 * 全链路复用同一个值；每个服务内部生成自己的 spanId 用于区分调用段。
 * <p>
 * 底层是 ThreadLocal({@link MDC})，因此<b>必须在请求结束时 {@link #clear()}</b>，
 * 否则线程池复用会把上一个请求的 traceId 串到下一个请求。
 */
public final class TraceContext {

    /** 跨服务透传的请求头，取业界通用命名 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_SPAN_ID  = "X-Span-Id";

    /** MDC key，与 logback pattern 中的 %X{traceId} 对应 */
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID  = "spanId";
    public static final String MDC_USER_ID  = "userId";
    public static final String MDC_CLIENT_IP = "clientIp";

    private TraceContext() {
    }

    /** 生成 32 位无连字符十六进制 ID，与 OpenTelemetry traceId 格式一致 */
    public static String newTraceId() {
        UUID uuid = UUID.randomUUID();
        return toHex(uuid.getMostSignificantBits()) + toHex(uuid.getLeastSignificantBits());
    }

    /** 生成 16 位 spanId */
    public static String newSpanId() {
        return toHex(UUID.randomUUID().getMostSignificantBits());
    }

    private static String toHex(long value) {
        // 补足前导零，避免高位为 0 时长度不一致
        return String.format("%016x", value);
    }

    public static void setTraceId(String traceId) {
        MDC.put(MDC_TRACE_ID, traceId);
    }

    public static String getTraceId() {
        return MDC.get(MDC_TRACE_ID);
    }

    public static void setSpanId(String spanId) {
        MDC.put(MDC_SPAN_ID, spanId);
    }

    public static void setUserId(String userId) {
        if (userId != null) {
            MDC.put(MDC_USER_ID, userId);
        }
    }

    public static void setClientIp(String clientIp) {
        if (clientIp != null) {
            MDC.put(MDC_CLIENT_IP, clientIp);
        }
    }

    /** 请求结束时清理，必须在 finally 中调用 */
    public static void clear() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_CLIENT_IP);
    }
}
