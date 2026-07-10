package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.common.Result;
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

/** 认证接口：注册、登录、刷新。全部放行(无需 token)。 */
@Tag(name = "认证", description = "注册 / 登录 / 刷新 Token，均无需鉴权")
@SecurityRequirements // 覆盖全局 bearerAuth：本控制器下的接口在文档中标记为无需鉴权
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户注册", description = "创建普通用户(USER)，用户名唯一，密码将以 BCrypt 加密存储")
    @PostMapping("/register")
    public ResponseEntity<Result<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(201, "注册成功", null));
    }

    @Operation(summary = "用户登录", description = "校验用户名密码，成功返回 access + refresh token")
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request.username(), request.password()));
    }

    @Operation(summary = "刷新 Token", description = "用合法的 refresh token 换取新的 access + refresh token")
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request.refreshToken()));
    }
}
