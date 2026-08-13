package org.example.gateway.filter;

import io.jsonwebtoken.JwtException;
import org.example.common.log.TraceContext;
import org.example.common.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关统一鉴权过滤器：所有请求(除白名单)在此校验 access token 签名 + 过期 + 类型。
 * <p>
 * 未通过直接返回 401 JSON，请求<b>不会被转发到下游业务服务</b>——这是“鉴权上移网关层”的核心。
 * 通过后放行；下游 user-service 仍会做一次轻量验签兜底(防绕过网关直连)，故此处不注入任何用户头。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    /** 无需鉴权的路径：登录/注册/刷新/登出 + 接口文档 */
    private static final List<String> WHITELIST = List.of(
            "/api/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final JwtService jwtService;

    public AuthGlobalFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单直接放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "未携带 token", path);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            // 验签 + 验过期(parseSignedClaims 一体完成)，并确认是 access token
            if (!jwtService.isAccessToken(token)) {
                return unauthorized(exchange, "token 类型错误", path);
            }
            // 触发一次完整解析，过期/被篡改会抛异常
            jwtService.extractUsername(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange, "token 无效或已过期", path);
        }

        return chain.filter(exchange);
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message, String path) {
        // 鉴权失败必须留痕：安全审计与撞库排查都依赖这条日志。
        // WARN 而非 ERROR——这是预期内的拒绝，不是系统故障。
        logRejection(exchange, message, path);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // 与业务服务统一的 {code,message,data} 结构
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 输出拒绝日志。traceId 由 {@link TraceWebFilter} 放入 Reactor Context，
     * 但此处是同步代码段，需临时填充 MDC 才能被 logback pattern 取到，写完即清理。
     */
    private void logRejection(ServerWebExchange exchange, String reason, String path) {
        String traceId = exchange.getResponse().getHeaders()
                .getFirst(TraceContext.HEADER_TRACE_ID);
        try {
            if (traceId != null) {
                MDC.put(TraceContext.MDC_TRACE_ID, traceId);
            }
            log.warn("网关鉴权拒绝: path={}, reason={}", path, reason);
        } finally {
            MDC.remove(TraceContext.MDC_TRACE_ID);
        }
    }

    /** 需在路由转发前执行，取较高优先级 */
    @Override
    public int getOrder() {
        return -100;
    }
}
