package com.example.knowledgeagent.document.parser;

import com.example.knowledgeagent.document.enums.FileType;

import java.nio.file.Path;

/**
 * 定义 DocumentParser 接口，约定该模块对外提供的能力。
 */
public interface DocumentParser {
    /**
     * 判断解析器是否支持指定文件类型。
     */
    boolean supports(FileType fileType);

    /**
     * 解析文件并返回文本内容与元数据。
     */
    ParsedDocument parse(Path path);
}
