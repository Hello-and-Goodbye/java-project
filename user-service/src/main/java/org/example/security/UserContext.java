package org.example.security;

/**
 * 进程内的当前登录用户上下文，基于 ThreadLocal。
 * <p>
 * 由 {@link JwtAuthenticationFilter} 在验签通过后写入（不查库、不查 Redis，
 * 全部信息直接从不可伪造的 access token claims 中解出），Controller / Service
 * 各层通过 {@link #get()} 复用，请求结束时由过滤器 {@link #clear()} 清理，
 * 避免线程复用导致的用户信息串号。
 */
public final class UserContext {

    /** 不可伪造的当前用户快照，字段全部来自已验签的 token。 */
    public record CurrentUser(Long id, String username, String role) {
    }

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /** 当前请求的用户；未认证请求返回 null。 */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long currentUserId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.id();
    }

    public static String currentUsername() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.username();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
