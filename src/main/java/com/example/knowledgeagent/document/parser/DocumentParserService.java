package com.example.knowledgeagent.document.parser;

import com.example.knowledgeagent.document.enums.FileType;

public interface DocumentParserService {
    ParsedDocument parse(String filePath, FileType fileType);
}
