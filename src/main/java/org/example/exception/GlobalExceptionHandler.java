package org.example.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 统一异常处理，所有错误都返回 {code, message} 结构的 JSON。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage());
    }

    /** @Valid 校验失败：收集字段错误信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST.value(), message.isEmpty() ? "参数校验失败" : message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误: " + ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
