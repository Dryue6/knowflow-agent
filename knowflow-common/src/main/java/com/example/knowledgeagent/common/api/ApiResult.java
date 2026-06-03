package com.example.knowledgeagent.common.api;

import java.time.LocalDateTime;

/**
 * 定义 ApiResult 数据结构，用于在层间传递结构化数据。
 */
public record ApiResult<T>(String code, String message, T data, LocalDateTime timestamp) {

    /**
     * 构造成功响应并携带数据。
     */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, LocalDateTime.now());
    }

    /**
     * 构造无数据的成功响应。
     */
    public static ApiResult<Void> ok() {
        return ok(null);
    }

    /**
     * 构造失败响应。
     */
    public static <T> ApiResult<T> fail(ErrorCode errorCode, String message) {
        return new ApiResult<>(errorCode.getCode(), message, null, LocalDateTime.now());
    }
}
