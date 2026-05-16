package com.example.knowledgeagent.chat.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ChatRole {
    USER("USER"), ASSISTANT("ASSISTANT"), SYSTEM("SYSTEM");

    @EnumValue
    @JsonValue
    private final String value;

    ChatRole(String value) {
        this.value = value;
    }
}
