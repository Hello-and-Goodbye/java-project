package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 注册请求 */
public record RegisterRequest(
        @Schema(description = "用户名，3-64 位，全局唯一", example = "zhangsan")
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度需在 3-64 之间")
        String username,

        @Schema(description = "密码，6-64 位", example = "123456")
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
        String password
) {
}
