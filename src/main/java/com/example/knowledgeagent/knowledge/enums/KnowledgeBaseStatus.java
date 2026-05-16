package com.example.knowledgeagent.knowledge.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum KnowledgeBaseStatus {
    ACTIVE("ACTIVE"), DISABLED("DISABLED");

    @EnumValue
    @JsonValue
    private final String value;

    KnowledgeBaseStatus(String value) {
        this.value = value;
    }
}
