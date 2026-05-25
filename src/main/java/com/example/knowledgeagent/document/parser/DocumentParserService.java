package com.example.knowledgeagent.document.parser;

import com.example.knowledgeagent.document.enums.FileType;

/**
 * 定义 DocumentParserService 接口，约定该模块对外提供的能力。
 */
public interface DocumentParserService {
    /**
     * 根据文件类型选择合适解析器并解析文件。
     */
    ParsedDocument parse(String filePath, FileType fileType);
}
