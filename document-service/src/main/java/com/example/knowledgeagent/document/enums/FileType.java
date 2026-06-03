package com.example.knowledgeagent.document.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

@Getter
/**
 * 定义 FileType 枚举，集中描述业务状态或类型取值。
 */
public enum FileType {
    TXT("TXT", ".txt"), MD("MD", ".md"), PDF("PDF", ".pdf"), DOCX("DOCX", ".docx");

    @EnumValue
    @JsonValue
    private final String value;
    private final String extension;

    FileType(String value, String extension) {
        this.value = value;
        this.extension = extension;
    }

    /**
     * 根据文件名后缀识别文档类型。
     */
    public static FileType fromFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        return Arrays.stream(values())
                .filter(type -> lower.endsWith(type.extension))
                .findFirst()
                .orElseThrow(() -> BusinessException.badRequest("仅支持 txt、md、pdf、docx 文件"));
    }
}
