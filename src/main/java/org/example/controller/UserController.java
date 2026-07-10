package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.common.Result;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 受保护的示例接口：必须携带有效 access token 才能访问。 */
@Tag(name = "用户", description = "需携带有效 access token 访问")
@RestController
@RequestMapping("/api")
public class UserController {

    /** 返回当前登录用户信息 */
    @Operation(summary = "当前用户信息", description = "返回 token 对应的用户名与角色")
    @GetMapping("/me")
    public Result<Map<String, Object>> me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return Result.success(Map.of(
                "username", authentication.getName(),
                "roles", roles
        ));
    }
}
