package org.example.gateway.filter;

import org.example.common.log.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * 网关链路入口：生成 traceId，注入下游请求头，并输出访问日志。
 * <p>
 * <b>为什么不能直接用 MDC：</b>WebFlux 一个请求会在多个线程上流转
 * (netty event-loop、可能的 boundedElastic 等)，而 MDC 底层是 ThreadLocal，
 * 线程一切换值就丢了。因此 traceId 存入 <b>Reactor Context</b>(随信号流走，不绑线程)，
 * 只在真正写日志的那一刻临时放进 MDC，写完立即清理。
 * <p>
 * 优先级高于 {@link AuthGlobalFilter}(-100)，保证鉴权失败的日志也带 traceId。
 */
@Component
public class TraceWebFilter implements WebFilter, Ordered {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS");

    /** Reactor Context 中存放 traceId 的 key */
    public static final String CTX_TRACE_ID = TraceContext.MDC_TRACE_ID;

    /** 慢请求阈值 */
    private static final long SLOW_THRESHOLD_MS = 1000L;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 入口生成；若上游(如更外层的 Nginx/LB)已带则复用，保证全链路同一个 ID
        String upstream = request.getHeaders().getFirst(TraceContext.HEADER_TRACE_ID);
        String traceId = StringUtils.hasText(upstream) ? upstream : TraceContext.newTraceId();
        String spanId = TraceContext.newSpanId();
        String clientIp = resolveClientIp(exchange);

        // 关键：把 traceId 注入转发给下游的请求头，user-service 的 TraceIdFilter 会读取它
        ServerHttpRequest mutated = request.mutate()
                .header(TraceContext.HEADER_TRACE_ID, traceId)
                .header(TraceContext.HEADER_SPAN_ID, spanId)
                .build();

        // 回写响应头，方便调用方报障时提供 traceId
        exchange.getResponse().getHeaders().set(TraceContext.HEADER_TRACE_ID, traceId);

        long start = System.currentTimeMillis();
        String method = request.getMethod().name();
        String path = buildPath(request);

        return chain.filter(exchange.mutate().request(mutated).build())
                // doFinally 在完成/取消/异常时都会执行，等价于 Servlet 的 finally
                .doFinally(signal -> {
                    long cost = System.currentTimeMillis() - start;
                    Integer status = exchange.getResponse().getStatusCode() == null
                            ? null : exchange.getResponse().getStatusCode().value();
                    logAccess(traceId, spanId, clientIp, method, path, status, cost);
                })
                // 写入 Reactor Context，供下游算子按需取用
                .contextWrite(Context.of(CTX_TRACE_ID, traceId,
                        TraceContext.MDC_SPAN_ID, spanId));
    }

    /**
     * 输出访问日志：临时把链路信息放进 MDC，让 logback pattern 的 %X{traceId} 取到值，
     * 写完立即清理——event-loop 线程会被大量请求复用，残留会串号。
     */
    private void logAccess(String traceId, String spanId, String clientIp,
                           String method, String path, Integer status, long cost) {
        try {
            MDC.put(TraceContext.MDC_TRACE_ID, traceId);
            MDC.put(TraceContext.MDC_SPAN_ID, spanId);
            if (clientIp != null) {
                MDC.put(TraceContext.MDC_CLIENT_IP, clientIp);
            }
            int code = status == null ? 0 : status;
            if (cost >= SLOW_THRESHOLD_MS) {
                ACCESS_LOG.warn("{} {} status={} cost={}ms SLOW", method, path, code, cost);
            } else if (code >= 500) {
                ACCESS_LOG.error("{} {} status={} cost={}ms", method, path, code, cost);
            } else {
                ACCESS_LOG.info("{} {} status={} cost={}ms", method, path, code, cost);
            }
        } finally {
            TraceContext.clear();
        }
    }

    private String buildPath(ServerHttpRequest request) {
        String query = request.getURI().getRawQuery();
        String path = request.getURI().getPath();
        return query == null ? path : path + "?" + query;
    }

    /**
     * 取真实客户端 IP。转发头可伪造，仅用于日志排查，不作安全判定。
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return exchange.getRequest().getRemoteAddress() == null
                ? null
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    /** 必须早于 AuthGlobalFilter(-100)，否则鉴权失败日志无 traceId */
    @Override
    public int getOrder() {
        return -200;
    }
}
