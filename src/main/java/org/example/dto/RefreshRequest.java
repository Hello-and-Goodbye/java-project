package org.example.dto;

import jakarta.validation.constraints.NotBlank;

/** 刷新 token 请求 */
public record RefreshRequest(
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
) {
}
