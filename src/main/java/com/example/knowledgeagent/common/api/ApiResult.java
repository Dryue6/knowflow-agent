package com.example.knowledgeagent.common.api;

import java.time.LocalDateTime;

public record ApiResult<T>(String code, String message, T data, LocalDateTime timestamp) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, LocalDateTime.now());
    }

    public static ApiResult<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> fail(ErrorCode errorCode, String message) {
        return new ApiResult<>(errorCode.getCode(), message, null, LocalDateTime.now());
    }
}
