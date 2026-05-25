package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.common.util.TextSanitizer;
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
/**
 * 定义 DocxDocumentParser 组件，承载对应模块的业务职责。
 */
public class DocxDocumentParser implements DocumentParser {
    /**
     * 声明该解析器支持 DOCX 文件。
     */
    @Override
    public boolean supports(FileType fileType) {
        return FileType.DOCX == fileType;
    }

    /**
     * 使用 Apache POI 抽取 Word 文档文本，并记录段落数量。
     */
    @Override
    public ParsedDocument parse(Path path) {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return new ParsedDocument(path.getFileName().toString(), TextSanitizer.removeNullBytes(extractor.getText()), Map.of("paragraphs", document.getParagraphs().size()));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "解析 docx 文件失败: " + ex.getMessage());
        }
    }
}
