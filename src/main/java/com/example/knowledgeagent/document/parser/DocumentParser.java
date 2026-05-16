package com.example.knowledgeagent.document.parser;

import com.example.knowledgeagent.document.enums.FileType;

import java.nio.file.Path;

public interface DocumentParser {
    boolean supports(FileType fileType);

    ParsedDocument parse(Path path);
}
