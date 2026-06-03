package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.config.OcrProperties;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.ocr.OcrResult;
import com.example.knowledgeagent.document.ocr.OcrService;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentParserTest {
    @TempDir
    Path tempDir;

    @Test
    void nativeTextPdfDoesNotTriggerOcr() throws Exception {
        Path pdf = tempDir.resolve("native.pdf");
        createTextPdf(pdf, "This page already has enough native text for parsing.");
        CountingOcrService ocrService = new CountingOcrService(true, "should-not-appear");
        PdfDocumentParser parser = new PdfDocumentParser(ocrService, new OcrProperties(true, "", 5, 0.5, 20, 120));

        ParsedDocument parsed = parser.parse(pdf);

        assertThat(parsed.text()).contains("This page already has enough native text");
        assertThat(parsed.text()).doesNotContain("should-not-appear");
        assertThat(ocrService.calls()).isZero();
        assertThat(parsed.metadata()).containsKey("pageRanges");
    }

    @Test
    void lowTextPdfPageAppendsOcrTextAndKeepsPageMetadata() throws Exception {
        Path pdf = tempDir.resolve("scan.pdf");
        createBlankPdf(pdf);
        CountingOcrService ocrService = new CountingOcrService(true, "扫描图片中的招生规则");
        PdfDocumentParser parser = new PdfDocumentParser(ocrService, new OcrProperties(true, "", 5, 0.5, 20, 120));

        ParsedDocument parsed = parser.parse(pdf);

        assertThat(parsed.text()).contains("【图片OCR 第 1 页】扫描图片中的招生规则");
        assertThat(ocrService.calls()).isEqualTo(1);
        assertThat(parsed.metadata().get("ocrFailedCount")).isEqualTo(0);
        assertThat(parsed.metadata().get("ocrItems").toString()).contains("PDF_PAGE");
    }

    private void createTextPdf(Path path, String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(path.toFile());
        }
    }

    private void createBlankPdf(Path path) throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(path.toFile());
        }
    }

    private static class CountingOcrService implements OcrService {
        private final boolean enabled;
        private final String text;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingOcrService(boolean enabled, String text) {
            this.enabled = enabled;
            this.text = text;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public OcrResult recognize(byte[] imageBytes, String fileName) {
            calls.incrementAndGet();
            return OcrResult.success(text, 0.99, 10);
        }

        int calls() {
            return calls.get();
        }
    }
}
