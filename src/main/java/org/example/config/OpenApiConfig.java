package org.example.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc / OpenAPI 3 配置。
 * <p>
 * 定义全局文档信息，并声明一个名为 bearerAuth 的 HTTP Bearer(JWT) 安全方案，
 * 使 Swagger UI 顶部出现 Authorize 按钮，填入 access token 后即可调试受保护接口。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("Java Project API")
                        .description("用户认证与鉴权服务接口文档（JWT access + refresh token）")
                        .version("v1.0.0")
                        .contact(new Contact().name("LiuXiaoyue")))
                // 全局默认要求 bearerAuth；在 permitAll 的接口上用 @SecurityRequirements 覆盖为无需鉴权
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("在此填入登录接口返回的 accessToken（不需要加 Bearer 前缀）")));
    }
}
