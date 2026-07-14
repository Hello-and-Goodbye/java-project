package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 刷新 token 请求 */
public record RefreshRequest(
        @Schema(description = "登录时下发的 refresh token", example = "eyJhbGciOiJIUzI1NiJ9...")
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
) {
}
