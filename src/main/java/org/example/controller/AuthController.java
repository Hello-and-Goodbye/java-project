package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.LoginRequest;
import org.example.dto.RefreshRequest;
import org.example.dto.RegisterRequest;
import org.example.dto.TokenResponse;
import org.example.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 认证接口：注册、登录、刷新。全部放行(无需 token)。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("code", 201, "message", "注册成功"));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }
}
