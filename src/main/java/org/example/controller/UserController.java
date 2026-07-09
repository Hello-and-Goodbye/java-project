package org.example.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 受保护的示例接口：必须携带有效 access token 才能访问。 */
@RestController
@RequestMapping("/api")
public class UserController {

    /** 返回当前登录用户信息 */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return Map.of(
                "username", authentication.getName(),
                "roles", roles
        );
    }
}
