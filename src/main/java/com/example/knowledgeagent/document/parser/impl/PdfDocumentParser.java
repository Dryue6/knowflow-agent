package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.parser.DocumentParser;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@Component
public class PdfDocumentParser implements DocumentParser {
    /**
     * 声明该解析器支持 PDF 文件。
     */
    @Override
    public boolean supports(FileType fileType) {
        return FileType.PDF == fileType;
    }

    /**
     * 使用 PDFBox 抽取 PDF 文本，并记录页数元数据。
     */
    @Override
    public ParsedDocument parse(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            String text = new PDFTextStripper().getText(document);
            return new ParsedDocument(path.getFileName().toString(), text, Map.of("pages", document.getNumberOfPages()));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "解析 pdf 文件失败: " + ex.getMessage());
        }
    }
}
