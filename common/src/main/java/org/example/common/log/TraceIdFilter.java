package org.example.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet 服务的链路上下文过滤器：把 traceId 放进 MDC，供日志 pattern 输出。
 * <p>
 * traceId 优先取上游(网关)透传的 {@link TraceContext#HEADER_TRACE_ID}，
 * 缺失时本地生成——保证直连服务时日志同样有 traceId。
 * <p>
 * 必须排在最前面(优先级高于鉴权过滤器)，否则鉴权失败的日志会没有 traceId。
 * 注册顺序由 {@link LogAutoConfiguration} 通过 FilterRegistrationBean 显式指定。
 * 同时把 traceId 写回响应头，便于前端/调用方上报问题时直接提供该 ID。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String traceId = request.getHeader(TraceContext.HEADER_TRACE_ID);
            if (!StringUtils.hasText(traceId)) {
                traceId = TraceContext.newTraceId();
            }
            TraceContext.setTraceId(traceId);
            TraceContext.setSpanId(TraceContext.newSpanId());
            TraceContext.setClientIp(ClientIpResolver.resolve(request));

            // 回写响应头：调用方拿到 traceId 才能在报障时精确定位
            response.setHeader(TraceContext.HEADER_TRACE_ID, traceId);

            filterChain.doFilter(request, response);
        } finally {
            // 线程池复用线程，不清理会串到下一个请求
            TraceContext.clear();
        }
    }
}
