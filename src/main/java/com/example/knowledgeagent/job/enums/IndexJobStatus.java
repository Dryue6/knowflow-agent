package com.example.knowledgeagent.job.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
/**
 * 定义 IndexJobStatus 枚举，集中描述业务状态或类型取值。
 */
public enum IndexJobStatus {
    PENDING("PENDING"), RUNNING("RUNNING"), SUCCESS("SUCCESS"), FAILED("FAILED");

    @EnumValue
    @JsonValue
    private final String value;

    IndexJobStatus(String value) {
        this.value = value;
    }
}
