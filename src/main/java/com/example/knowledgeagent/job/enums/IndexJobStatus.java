package com.example.knowledgeagent.job.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum IndexJobStatus {
    PENDING("PENDING"), RUNNING("RUNNING"), SUCCESS("SUCCESS"), FAILED("FAILED");

    @EnumValue
    @JsonValue
    private final String value;

    IndexJobStatus(String value) {
        this.value = value;
    }
}
