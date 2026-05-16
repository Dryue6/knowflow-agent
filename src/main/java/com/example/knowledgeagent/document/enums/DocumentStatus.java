package com.example.knowledgeagent.document.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum DocumentStatus {
    UPLOADED("UPLOADED"), PARSING("PARSING"), PARSED("PARSED"), INDEXING("INDEXING"), INDEXED("INDEXED"), FAILED("FAILED"), DELETED("DELETED");

    @EnumValue
    @JsonValue
    private final String value;

    DocumentStatus(String value) {
        this.value = value;
    }
}
