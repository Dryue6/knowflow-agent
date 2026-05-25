package com.example.knowledgeagent.chat.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
/**
 * 定义 ChatRole 枚举，集中描述业务状态或类型取值。
 */
public enum ChatRole {
    USER("USER"), ASSISTANT("ASSISTANT"), SYSTEM("SYSTEM");

    @EnumValue
    @JsonValue
    private final String value;

    ChatRole(String value) {
        this.value = value;
    }
}
