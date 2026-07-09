package org.example.exception;

/** 业务异常：用于返回可读的错误信息(如用户名已存在、凭证错误)。 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
