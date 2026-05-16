package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.parser.DocumentParser;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class TxtDocumentParser implements DocumentParser {
    /**
     * 声明该解析器支持 TXT 文件。
     */
    @Override
    public boolean supports(FileType fileType) {
        return FileType.TXT == fileType;
    }

    /**
     * 按 UTF-8 读取纯文本文件。
     */
    @Override
    public ParsedDocument parse(Path path) {
        try {
            return new ParsedDocument(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8), Map.of());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "读取 txt 文件失败: " + ex.getMessage());
        }
    }
}
