package com.example.knowledgeagent.document.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
/**
 * 定义 DocumentConstraintLevel 枚举，集中描述业务状态或类型取值。
 */
public enum DocumentConstraintLevel {
    NORMAL("NORMAL"),
    PINNED("PINNED"),
    SYSTEM("SYSTEM");

    @EnumValue
    @JsonValue
    private final String value;

    DocumentConstraintLevel(String value) {
        this.value = value;
    }
}
