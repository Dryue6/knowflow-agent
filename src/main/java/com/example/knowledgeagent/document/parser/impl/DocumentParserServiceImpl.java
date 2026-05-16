package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.parser.DocumentParser;
import com.example.knowledgeagent.document.parser.DocumentParserService;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentParserServiceImpl implements DocumentParserService {
    private final List<DocumentParser> parsers;

    /**
     * 根据文件类型查找匹配的解析器并执行解析。
     */
    @Override
    public ParsedDocument parse(String filePath, FileType fileType) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileType))
                .findFirst()
                .orElseThrow(() -> BusinessException.badRequest("未找到文档解析器: " + fileType))
                .parse(Path.of(filePath));
    }
}
