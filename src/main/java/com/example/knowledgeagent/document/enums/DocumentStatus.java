package com.example.knowledgeagent.document.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
/**
 * 定义 DocumentStatus 枚举，集中描述业务状态或类型取值。
 */
public enum DocumentStatus {
    UPLOADED("UPLOADED"), PARSING("PARSING"), PARSED("PARSED"), INDEXING("INDEXING"), INDEXED("INDEXED"), FAILED("FAILED"), DELETED("DELETED");

    @EnumValue
    @JsonValue
    private final String value;

    DocumentStatus(String value) {
        this.value = value;
    }
}
