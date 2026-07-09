package org.example.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

/**
 * 负责 JWT 的生成与解析。
 * access token 用于日常请求鉴权，refresh token 仅用于换取新的 access token。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessExpiration;
    private final long refreshExpiration;

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration}") long accessExpiration,
            @Value("${app.jwt.refresh-expiration}") long refreshExpiration) {
        // secret 以 Base64 存储，解码后作为 HMAC-SHA256 的密钥
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String generateAccessToken(String username, String role) {
        return buildToken(username, role, TYPE_ACCESS, accessExpiration);
    }

    public String generateRefreshToken(String username) {
        return buildToken(username, null, TYPE_REFRESH, refreshExpiration);
    }

    private String buildToken(String username, String role, String type, long ttlMillis) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(username)
                .claim(CLAIM_TYPE, type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(key);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(extractClaim(token, claims -> claims.get(CLAIM_TYPE, String.class)));
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(extractClaim(token, claims -> claims.get(CLAIM_TYPE, String.class)));
    }

    /**
     * 校验签名与过期时间并解析 claims。任何非法/过期 token 都会抛出 JwtException，由调用方处理。
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
}
