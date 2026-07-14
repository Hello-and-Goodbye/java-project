package org.example.service;

import io.jsonwebtoken.JwtException;
import org.example.dto.TokenResponse;
import org.example.entity.Role;
import org.example.entity.User;
import org.example.exception.ApiException;
import org.example.repository.UserRepository;
import org.example.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 认证相关业务：注册、登录、刷新 token。 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
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
        return issueTokens(user);
    }

    /** 用合法的 refresh token 换取新的 access + refresh token。 */
    public TokenResponse refresh(String refreshToken) {
        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new ApiException(401, "无效的 refresh token");
            }
            String username = jwtService.extractUsername(refreshToken);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ApiException(401, "用户不存在"));
            return issueTokens(user);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(401, "refresh token 无效或已过期");
        }
    }

    private TokenResponse issueTokens(User user) {
        String access = jwtService.generateAccessToken(user.getUsername(), user.getRole().name());
        String refresh = jwtService.generateRefreshToken(user.getUsername());
        return TokenResponse.bearer(access, refresh, jwtService.getAccessExpirationSeconds());
    }
}
