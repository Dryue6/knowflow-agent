package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.document.enums.FileType;
import org.springframework.stereotype.Component;

@Component
public class MarkdownDocumentParser extends TxtDocumentParser {
    @Override
    public boolean supports(FileType fileType) {
        return FileType.MD == fileType;
    }
}
