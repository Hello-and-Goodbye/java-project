package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 登录/刷新成功后返回的 token 对 */
@Schema(description = "Token 响应")
public record TokenResponse(
        @Schema(description = "访问令牌，放入 Authorization: Bearer 头") String accessToken,
        @Schema(description = "刷新令牌，用于换取新 accessToken") String refreshToken,
        @Schema(description = "令牌类型", example = "Bearer") String tokenType,
        @Schema(description = "accessToken 有效期（秒）", example = "7200") long expiresIn
) {
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
