package org.example.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.common.security.JwtService;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 轻量验签兜底过滤器（防绕过网关直连）。
 * <p>
 * 只验签名 + exp + type，不查库、不查 Redis。
 * 验通过后从 token claims 直接解出用户信息，写入 {@link UserContext}（ThreadLocal），
 * 同时设置 Spring SecurityContext；请求结束时清理 ThreadLocal。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private static final String BEARER_PREFIX = "Bearer ";

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                String token = authHeader.substring(BEARER_PREFIX.length());
                // 只接受 access token；parseSignedClaims 同时验签 + 验 exp
                if (jwtService.isAccessToken(token)) {
                    String username = jwtService.extractUsername(token);
                    String role     = jwtService.extractRole(token);
                    Long   userId   = jwtService.extractUserId(token);

                    UserContext.set(new UserContext.CurrentUser(userId, username, role));

                    var auth = new UsernamePasswordAuthenticationToken(
                            username, null,
                            role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) : List.of());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            UserContext.clear();
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
