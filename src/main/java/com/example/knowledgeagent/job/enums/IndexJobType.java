package com.example.knowledgeagent.job.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum IndexJobType {
    INDEX("INDEX"), REINDEX("REINDEX");

    @EnumValue
    @JsonValue
    private final String value;

    IndexJobType(String value) {
        this.value = value;
    }
}
