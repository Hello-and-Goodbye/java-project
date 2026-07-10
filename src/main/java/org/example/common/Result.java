package org.example.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一返回体。所有接口(除页面渲染外)都返回该结构。
 * <p>
 * code 复用 HTTP 状态码：2xx 表示成功，4xx/5xx 表示业务或系统错误。
 * HTTP 响应状态码同时保留真实语义，前端既可看 HTTP 状态也可看 code。
 *
 * @param code    状态码，与 HTTP 状态码一致(200 成功、201 已创建、400/401/409/500 等)
 * @param message 可读提示信息
 * @param data    业务数据，失败时为 null
 */
@Schema(description = "统一返回体")
public record Result<T>(
        @Schema(description = "状态码，与 HTTP 状态码一致", example = "200") int code,
        @Schema(description = "提示信息", example = "success") String message,
        @Schema(description = "业务数据，失败时为 null") T data
) {

    private static final String DEFAULT_SUCCESS_MESSAGE = "success";

    /** 200 成功，携带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, DEFAULT_SUCCESS_MESSAGE, data);
    }

    /** 200 成功，自定义提示 + 数据 */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 200 成功，无数据 */
    public static Result<Void> success() {
        return new Result<>(200, DEFAULT_SUCCESS_MESSAGE, null);
    }

    /** 指定状态码的成功响应(如 201 Created)，携带数据 */
    public static <T> Result<T> ok(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    /** 失败响应 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
