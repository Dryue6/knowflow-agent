package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.config.OcrProperties;
import com.example.knowledgeagent.document.ocr.OcrImageNormalizer;
import com.example.knowledgeagent.document.ocr.OcrResult;
import com.example.knowledgeagent.document.ocr.OcrService;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DocxDocumentParserTest {
    @TempDir
    Path tempDir;

    @Test
    void docxParserAppendsBodyTableHeaderAndFooterPictureOcrText() throws Exception {
        Path docx = tempDir.resolve("picture.docx");
        createDocxWithPictures(docx, createImage(Color.WHITE, Color.BLACK));
        CountingOcrService ocrService = new CountingOcrService(true, true, "图片里的报名截止日期");
        DocxDocumentParser parser = parser(ocrService, defaultProperties());

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.text()).contains("正文段落");
        assertThat(parsed.text()).contains("【图片OCR DOCX 第 1 张 / 正文第1段】图片里的报名截止日期");
        assertThat(parsed.text()).contains("正文表格");
        assertThat(parsed.text()).contains("页眉1第1段");
        assertThat(parsed.text()).contains("页脚1第1段");
        assertThat(parsed.metadata().get("docxImageCount")).isEqualTo(4);
        assertThat(parsed.metadata().get("docxOcrAttemptCount")).isEqualTo(4);
        assertThat(parsed.metadata().get("docxOcrSuccessCount")).isEqualTo(4);
        assertThat(parsed.metadata().get("docxUnsupportedImageCount")).isEqualTo(0);
        assertThat(ocrService.calls()).isEqualTo(4);
    }

    @Test
    void duplicateImagesOnlyCallOcrOnceAndKeepAllLocations() throws Exception {
        Path docx = tempDir.resolve("duplicate.docx");
        byte[] image = createImage(Color.WHITE, Color.BLUE);
        try (XWPFDocument document = new XWPFDocument()) {
            addPicture(document.createParagraph().createRun(), image, "same.png");
            XWPFTable table = document.createTable(1, 1);
            addPicture(table.getRow(0).getCell(0).addParagraph().createRun(), image, "same-again.png");
            try (var output = Files.newOutputStream(docx)) {
                document.write(output);
            }
        }
        CountingOcrService ocrService = new CountingOcrService(true, true, "重复图片文字");
        DocxDocumentParser parser = parser(ocrService, defaultProperties());

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.metadata().get("docxImageCount")).isEqualTo(1);
        assertThat(ocrService.calls()).isEqualTo(1);
        assertThat(parsed.metadata().get("ocrItems").toString()).contains("正文第1段", "正文表格1-行1列1-第2段");
    }

    @Test
    void basicScreenshotStyleImageIsSentToOcr() throws Exception {
        Path docx = tempDir.resolve("basic-screenshot.docx");
        createDocxWithPicture(docx, createScreenshotStyleImage());
        CountingOcrService ocrService = new CountingOcrService(true, true, "物联网环境监测系统 数据采集层 Serial.cpp");
        DocxDocumentParser parser = parser(ocrService, defaultProperties());

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.text()).contains("【图片OCR DOCX 第 1 张 / 正文第1段】物联网环境监测系统");
        assertThat(parsed.metadata().get("docxOcrAttemptCount")).isEqualTo(1);
        assertThat(parsed.metadata().get("docxUnsupportedImageCount")).isEqualTo(0);
        assertThat(ocrService.calls()).isEqualTo(1);
    }

    @Test
    void leadingPictureOcrTextIsInsertedNearDocumentStart() throws Exception {
        Path docx = tempDir.resolve("leading-picture.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph first = document.createParagraph();
            addPicture(first.createRun(), createScreenshotStyleImage(), "leading.png");
            document.createParagraph().createRun().setText("图片后面的正文段落");
            try (var output = Files.newOutputStream(docx)) {
                document.write(output);
            }
        }
        CountingOcrService ocrService = new CountingOcrService(true, true, "物联网环境监测系统 数据采集层 Serial.cpp");
        DocxDocumentParser parser = parser(ocrService, defaultProperties());

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.text()).startsWith("【图片OCR DOCX 第 1 张 / 正文第1段】物联网环境监测系统");
        assertThat(parsed.text().indexOf("【图片OCR DOCX 第 1 张")).isLessThan(parsed.text().indexOf("图片后面的正文段落"));
        assertThat(ocrService.calls()).isEqualTo(1);
    }

    @Test
    void disabledOcrDoesNotCallServiceAndRecordsSkipReason() throws Exception {
        Path docx = tempDir.resolve("disabled.docx");
        createDocxWithPicture(docx, createImage(Color.WHITE, Color.BLACK));
        CountingOcrService ocrService = new CountingOcrService(false, true, "不应出现");
        DocxDocumentParser parser = parser(ocrService, defaultProperties());

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.text()).doesNotContain("不应出现");
        assertThat(parsed.metadata().get("docxOcrEnabled")).isEqualTo(false);
        assertThat(parsed.metadata().get("docxOcrAttemptCount")).isEqualTo(0);
        assertThat(parsed.metadata().get("ocrFailures").toString()).contains("DOCX OCR disabled");
        assertThat(ocrService.calls()).isZero();
    }

    @Test
    void invalidAndLowConfidenceImagesAreRecordedAsFailures() throws Exception {
        Path docx = tempDir.resolve("invalid.docx");
        createDocxWithPicture(docx, createImage(Color.WHITE, Color.BLACK));
        CountingOcrService ocrService = new CountingOcrService(true, false, "低置信度");
        DocxDocumentParser parser = parser(ocrService, defaultProperties());

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.text()).doesNotContain("低置信度");
        assertThat(parsed.metadata().get("docxOcrAttemptCount")).isEqualTo(1);
        assertThat(parsed.metadata().get("docxOcrSuccessCount")).isEqualTo(0);
        assertThat(parsed.metadata().get("ocrFailures").toString()).contains("low confidence");
    }

    @Test
    void blankImageIsSkippedBeforeCallingOcr() throws Exception {
        Path docx = tempDir.resolve("blank.docx");
        createDocxWithPicture(docx, createImage(Color.WHITE, Color.WHITE));
        CountingOcrService ocrService = new CountingOcrService(true, true, "不应调用");
        DocxDocumentParser parser = parser(ocrService, defaultProperties());

        ParsedDocument parsed = parser.parse(docx);

        assertThat(parsed.metadata().get("docxUnsupportedImageCount")).isEqualTo(1);
        assertThat(parsed.metadata().get("ocrFailures").toString()).contains("blank image");
        assertThat(ocrService.calls()).isZero();
    }

    private DocxDocumentParser parser(OcrService ocrService, OcrProperties properties) {
        return new DocxDocumentParser(ocrService, properties, new DocxImageExtractor(), new OcrImageNormalizer(properties));
    }

    private OcrProperties defaultProperties() {
        return new OcrProperties(true, "", 5, 0.5, 20, 120,
                new OcrProperties.Docx(true, 30, 0, 12_000_000L));
    }

    private void createDocxWithPicture(Path path, byte[] imageBytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("正文段落");
            addPicture(paragraph.createRun(), imageBytes, "ocr.png");
            try (var output = Files.newOutputStream(path)) {
                document.write(output);
            }
        }
    }

    private void createDocxWithPictures(Path path, byte[] imageBytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("正文段落");
            addPicture(paragraph.createRun(), imageBytes, "body.png");

            XWPFTable table = document.createTable(1, 1);
            XWPFTableCell cell = table.getRow(0).getCell(0);
            cell.setText("表格单元格");
            addPicture(cell.addParagraph().createRun(), createImage(Color.WHITE, Color.RED), "table.png");

            addPicture(document.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun(),
                    createImage(Color.WHITE, Color.GREEN), "header.png");
            addPicture(document.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun(),
                    createImage(Color.WHITE, Color.MAGENTA), "footer.png");
            try (var output = Files.newOutputStream(path)) {
                document.write(output);
            }
        }
    }

    private void addPicture(XWPFRun run, byte[] imageBytes, String fileName) throws Exception {
        run.addPicture(new ByteArrayInputStream(imageBytes), Document.PICTURE_TYPE_PNG,
                fileName, Units.toEMU(80), Units.toEMU(40));
    }

    private byte[] createImage(Color background, Color foreground) throws Exception {
        BufferedImage image = new BufferedImage(140, 80, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(background);
        graphics.fillRect(0, 0, 140, 80);
        graphics.setColor(foreground);
        graphics.drawString("OCR TEST", 12, 40);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] createScreenshotStyleImage() throws Exception {
        BufferedImage image = new BufferedImage(900, 520, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 900, 520);
        graphics.setColor(Color.BLACK);
        String[] lines = {
                "物联网环境监测系统",
                "├─ 数据采集层",
                "│  ├─ 串口通信模块 (Serial.cpp)",
                "│  └─ 传感器数据解析模块 (SensorModule.cpp)",
                "├─ 数据处理层",
                "│  ├─ 数据存储模块 (StorageModule.cpp)",
                "│  └─ 报警检测模块 (AlarmModule.cpp)"
        };
        int y = 40;
        for (String line : lines) {
            graphics.drawString(line, 36, y);
            y += 58;
        }
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private record CountingOcrService(boolean enabled, boolean success, String text) implements OcrService {
        private static final AtomicInteger CALLS = new AtomicInteger();

        private CountingOcrService {
            CALLS.set(0);
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public OcrResult recognize(byte[] imageBytes, String fileName) {
            CALLS.incrementAndGet();
            if (!success) {
                return OcrResult.failure("low confidence", 8);
            }
            return OcrResult.success(text, 0.98, 8);
        }

        int calls() {
            return CALLS.get();
        }
    }
}
