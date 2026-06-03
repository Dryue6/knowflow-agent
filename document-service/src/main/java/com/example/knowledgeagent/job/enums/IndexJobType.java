package com.example.knowledgeagent.job.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
/**
 * 定义 IndexJobType 枚举，集中描述业务状态或类型取值。
 */
public enum IndexJobType {
    INDEX("INDEX"), REINDEX("REINDEX");

    @EnumValue
    @JsonValue
    private final String value;

    IndexJobType(String value) {
        this.value = value;
    }
}
