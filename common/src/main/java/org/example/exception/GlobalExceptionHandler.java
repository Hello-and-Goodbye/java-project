package org.example.exception;

import org.example.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/** 统一异常处理，所有错误都返回 {@link Result} 结构，HTTP 状态码保留真实语义。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：状态码由抛出方指定 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Result<Void>> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage());
    }

    /** @Valid 校验失败：收集字段错误信息，返回 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST.value(), message.isEmpty() ? "参数校验失败" : message);
    }

    /** 认证失败(如凭证异常)：返回 401 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED.value(), "未认证或凭证无效");
    }

    /** 已认证但无权限：返回 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN.value(), "无权限访问");
    }

    /** 请求方法不支持：返回 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED.value(), "不支持的请求方法: " + ex.getMethod());
    }

    /** 路径不存在：返回 404。NoHandlerFoundException 与静态资源未命中的 NoResourceFoundException 都归此处 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFound(Exception ex) {
        return build(HttpStatus.NOT_FOUND.value(), "资源不存在");
    }

    /** 兜底：未预期的异常记录日志并返回 500，避免泄露堆栈细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception ex) {
        log.error("未处理的服务器异常", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误");
    }

    private ResponseEntity<Result<Void>> build(int status, String message) {
        return ResponseEntity.status(status).body(Result.error(status, message));
    }
}
