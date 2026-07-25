package org.example.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT 生成与解析，下沉到 common 供网关与各业务服务共用同一套验签逻辑与密钥。
 * <p>
 * access token：短命(默认 15 分钟)，无状态，仅靠签名 + exp 自证，不落 Redis。
 * refresh token：较长(默认 7 天)，带 jti(多设备区分) 与 iat0(首次登录时间，用于 30 天绝对封顶)，
 *                其状态由 Redis 维护(可吊销 / 滑动续期)。
 * <p>
 * 本类不加 Spring 注解，由 {@code JwtAutoConfiguration} 装配为 Bean，避免与各服务的组件扫描包耦合。
 */
public class JwtService {

    private final SecretKey key;
    private final long accessExpiration;
    private final long refreshExpiration;

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_UID  = "uid";
    /** 多设备：每个 refresh token 唯一标识，作为 Redis key 的一部分 */
    public static final String CLAIM_JTI = "jti";
    /** 首次登录时间(毫秒)，刷新时原样透传，用于计算 30 天绝对上限 */
    public static final String CLAIM_IAT0 = "iat0";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    public JwtService(String secret, long accessExpiration, long refreshExpiration) {
        // secret 以 Base64 存储，解码后作为 HMAC-SHA256 的密钥
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    // ==================== 生成 ====================

    public String generateAccessToken(String username, String role, Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_UID, userId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessExpiration))
                .signWith(key)
                .compact();
    }

    /**
     * 生成 refresh token。
     * @param jti  本次登录/刷新分配的唯一 id(多设备各自独立)
     * @param iat0 首次登录时间(毫秒)；刷新时把旧 token 的 iat0 透传进来以保持 30 天绝对上限不变
     */
    public String generateRefreshToken(String username, String jti, long iat0) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_JTI, jti)
                .claim(CLAIM_IAT0, iat0)
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshExpiration))
                .signWith(key)
                .compact();
    }

    /** 生成一个新的 jti */
    public String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ==================== 解析 ====================

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class));
    }

    public Long extractUserId(String token) {
        Number v = extractClaim(token, claims -> claims.get(CLAIM_UID, Number.class));
        return v == null ? null : v.longValue();
    }

    public String extractJti(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_JTI, String.class));
    }

    public long extractIat0(String token) {
        Number v = extractClaim(token, claims -> claims.get(CLAIM_IAT0, Number.class));
        return v == null ? 0L : v.longValue();
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(extractClaim(token, claims -> claims.get(CLAIM_TYPE, String.class)));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(extractClaim(token, claims -> claims.get(CLAIM_TYPE, String.class)));
    }

    /**
     * 校验签名与过期时间并解析 claims。任何非法/过期 token 都会抛出 JwtException，由调用方处理。
     * 注意：验签与验过期是一体的——parseSignedClaims 会同时校验签名和 exp。
     */
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }

    public long getAccessExpirationSeconds() {
        return accessExpiration / 1000;
    }

    public long getRefreshExpirationMillis() {
        return refreshExpiration;
    }
}
