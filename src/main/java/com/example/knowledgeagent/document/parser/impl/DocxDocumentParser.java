package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.parser.DocumentParser;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class DocxDocumentParser implements DocumentParser {
    @Override
    public boolean supports(FileType fileType) {
        return FileType.DOCX == fileType;
    }

    @Override
    public ParsedDocument parse(Path path) {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return new ParsedDocument(path.getFileName().toString(), extractor.getText(), Map.of("paragraphs", document.getParagraphs().size()));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "解析 docx 文件失败: " + ex.getMessage());
        }
    }
}
