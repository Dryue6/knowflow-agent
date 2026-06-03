package com.example.knowledgeagent.common.api;

import lombok.Getter;

@Getter
/**
 * 定义 ErrorCode 枚举，集中描述业务状态或类型取值。
 */
public enum ErrorCode {
    SUCCESS("0", "success"),
    BAD_REQUEST("400", "请求参数错误"),
    NOT_FOUND("404", "资源不存在"),
    FILE_ERROR("460", "文件处理失败"),
    AI_ERROR("470", "AI 服务调用失败"),
    VECTOR_ERROR("480", "向量服务调用失败"),
    INTERNAL_ERROR("500", "系统异常");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
