package org.example.service;

import io.jsonwebtoken.JwtException;
import org.example.common.security.JwtProperties;
import org.example.common.security.JwtService;
import org.example.dto.TokenResponse;
import org.example.entity.Role;
import org.example.entity.User;
import org.example.exception.ApiException;
import org.example.repository.UserRepository;
import org.example.security.RefreshTokenStore;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 认证相关业务：注册、登录、刷新、登出。refresh token 状态由 Redis 维护。 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       RefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Transactional
    public void register(String username, String rawPassword) {
        String u = username.trim();
        if (userRepository.existsByUsername(u)) {
            throw new ApiException(409, "用户名已存在");
        }
        User user = new User(u, passwordEncoder.encode(rawPassword.trim()), Role.USER);
        userRepository.save(user);
    }

    public TokenResponse login(String username, String rawPassword) {
        String u = username.trim();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(u, rawPassword.trim()));
        } catch (BadCredentialsException ex) {
            throw new ApiException(401, "用户名或密码错误");
        }
        User user = userRepository.findByUsername(u)
                .orElseThrow(() -> new ApiException(401, "用户名或密码错误"));

        // 首次登录：iat0 = 现在，作为 30 天绝对上限的锚点；分配一个新设备 jti
        long iat0 = System.currentTimeMillis();
        String jti = jwtService.newJti();
        return issueTokens(user, jti, iat0);
    }

    /**
     * 用合法的 refresh token 换取新的 access + refresh token（滑动续期）。
     * <p>
     * 流程：验签+验类型 → 查 Redis 该设备是否有效 → 校验未超 30 天绝对上限
     *      → 签发新 token(同 iat0、新 jti、滑动 TTL) → 删旧写新。
     */
    public TokenResponse refresh(String refreshToken) {
        final String username;
        final String oldJti;
        final long iat0;
        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new ApiException(401, "无效的 refresh token");
            }
            username = jwtService.extractUsername(refreshToken);
            oldJti = jwtService.extractJti(refreshToken);
            iat0 = jwtService.extractIat0(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(401, "refresh token 无效或已过期");
        }

        // 该设备的 refresh token 必须仍存在于 Redis（未登出 / 未被顶替 / 未过期）
        if (!refreshTokenStore.isValid(username, oldJti, refreshToken)) {
            throw new ApiException(401, "refresh token 已失效，请重新登录");
        }

        // 绝对上限：从首次登录起最长 30 天，到点必须重新登录，刷新不再延长
        long absoluteDeadline = iat0 + jwtProperties.getRefreshMaxLifetime();
        if (System.currentTimeMillis() >= absoluteDeadline) {
            refreshTokenStore.revoke(username, oldJti);
            throw new ApiException(401, "登录已达最长有效期，请重新登录");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(401, "用户不存在"));

        // 签发新 token：保持 iat0 不变，换新 jti；删旧写新，完成滑动续期
        String newJti = jwtService.newJti();
        TokenResponse response = issueTokens(user, newJti, iat0);
        refreshTokenStore.revoke(username, oldJti);
        return response;
    }

    /** 登出：吊销当前设备的 refresh token（access token 15 分钟内自然失效）。 */
    public void logout(String refreshToken) {
        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                return;
            }
            String username = jwtService.extractUsername(refreshToken);
            String jti = jwtService.extractJti(refreshToken);
            refreshTokenStore.revoke(username, jti);
        } catch (JwtException | IllegalArgumentException ignored) {
            // 非法 token 直接忽略，登出保持幂等
        }
    }

    /**
     * 签发 access + refresh，并把 refresh 写入 Redis。
     * TTL 取 min(refresh 有效期, 距绝对上限的剩余时间)，保证滑动不越过 30 天封顶。
     */
    private TokenResponse issueTokens(User user, String jti, long iat0) {
        String access = jwtService.generateAccessToken(user.getUsername(), user.getRole().name(), user.getId());
        String refresh = jwtService.generateRefreshToken(user.getUsername(), jti, iat0);

        long remainingToCap = (iat0 + jwtProperties.getRefreshMaxLifetime()) - System.currentTimeMillis();
        long ttl = Math.min(jwtProperties.getRefreshExpiration(), remainingToCap);
        refreshTokenStore.store(user.getUsername(), jti, refresh, ttl);

        return TokenResponse.bearer(access, refresh, jwtService.getAccessExpirationSeconds());
    }
}
