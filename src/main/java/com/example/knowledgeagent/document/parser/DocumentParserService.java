package com.example.knowledgeagent.document.parser;

import com.example.knowledgeagent.document.enums.FileType;

public interface DocumentParserService {
    /**
     * 根据文件类型选择合适解析器并解析文件。
     */
    ParsedDocument parse(String filePath, FileType fileType);
}
