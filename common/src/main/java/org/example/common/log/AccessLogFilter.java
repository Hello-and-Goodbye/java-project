package org.example.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 访问日志：每个请求一条，记录方法、路径、状态码、耗时。
 * <p>
 * 单独用 logger 名 {@code ACCESS} 输出，便于在 logback 中路由到独立文件、
 * 与业务日志分开采集分析(QPS / P99 / 错误率都从这里统计)。
 * <p>
 * 慢请求单独打 WARN，方便直接告警而不必事后聚合。
 * 排在 {@link TraceIdFilter} 之后，保证日志已带 traceId；
 * 注册顺序由 {@link LogAutoConfiguration} 显式指定。
 */
public class AccessLogFilter extends OncePerRequestFilter {

    /** 独立 logger 名，在 logback 中按此名配置 appender */
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS");

    /** 超过该毫秒数视为慢请求，升级为 WARN */
    private final long slowThresholdMs;

    /** 不记录访问日志的路径：健康检查、静态资源等高频无价值请求 */
    private static final List<String> EXCLUDE_PATTERNS = List.of(
            "/actuator/**",
            "/css/**", "/js/**", "/images/**",
            "/favicon.ico",
            "/swagger-ui/**", "/v3/api-docs/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AccessLogFilter(long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDE_PATTERNS.stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            String query = request.getQueryString();
            // query string 可能含 token 等敏感参数，交给脱敏转换器处理
            String path = query == null
                    ? request.getRequestURI()
                    : request.getRequestURI() + "?" + query;

            int status = response.getStatus();
            if (cost >= slowThresholdMs) {
                ACCESS_LOG.warn("{} {} status={} cost={}ms SLOW",
                        request.getMethod(), path, status, cost);
            } else if (status >= 500) {
                ACCESS_LOG.error("{} {} status={} cost={}ms",
                        request.getMethod(), path, status, cost);
            } else {
                ACCESS_LOG.info("{} {} status={} cost={}ms",
                        request.getMethod(), path, status, cost);
            }
        }
    }
}
