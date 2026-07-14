package org.example.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 只负责渲染页面。真正的鉴权由 JWT + Spring Security 完成，
 * 页面通过前端 JS 携带 Bearer token 调用 /api/** 接口。
 */
@Hidden // 页面渲染控制器，不纳入 OpenAPI 接口文档
@Controller
public class PageController {

    @GetMapping({"/", "/login"})
    public String loginPage() {
        return "login";
    }

    @GetMapping("/home")
    public String home() {
        return "home";
    }
}
