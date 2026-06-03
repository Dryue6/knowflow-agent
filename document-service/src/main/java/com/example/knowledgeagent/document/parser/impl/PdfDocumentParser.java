package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.common.util.TextSanitizer;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.config.OcrProperties;
import com.example.knowledgeagent.document.ocr.OcrItem;
import com.example.knowledgeagent.document.ocr.OcrResult;
import com.example.knowledgeagent.document.ocr.OcrService;
import com.example.knowledgeagent.document.parser.DocumentParser;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
/**
 * 定义 PdfDocumentParser 组件，承载对应模块的业务职责。
 */
public class PdfDocumentParser implements DocumentParser {
    private final OcrService ocrService;
    private final OcrProperties ocrProperties;

    /**
     * 声明该解析器支持 PDF 文件。
     */
    @Override
    public boolean supports(FileType fileType) {
        return FileType.PDF == fileType;
    }

    /**
     * 使用 PDFBox 抽取 PDF 文本，并在扫描页或低文本页上补充 OCR 识别结果。
     * <p>
     * OCR 文本会按页拼接到原生文本之后，后续切片和 embedding 仍复用既有链路。
     */
    @Override
    public ParsedDocument parse(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder text = new StringBuilder();
            List<Map<String, Object>> pageRanges = new ArrayList<>();
            List<Map<String, Object>> ocrItems = new ArrayList<>();
            int ocrFailedCount = 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                int start = text.length();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String nativeText = TextSanitizer.removeNullBytes(stripper.getText(document));
                text.append(nativeText);
                if (shouldOcrPage(nativeText)) {
                    OcrResult ocrResult = recognizePage(renderer, page, path.getFileName().toString());
                    if (ocrResult.success()) {
                        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
                            text.append('\n');
                        }
                        text.append("【图片OCR 第 ").append(page).append(" 页】")
                                .append(ocrResult.text());
                        ocrItems.add(new OcrItem("PDF_PAGE", page, null, ocrResult.text(),
                                ocrResult.confidence(), ocrResult.elapsedMs()).toMetadata());
                    } else if (ocrService.isEnabled()) {
                        ocrFailedCount++;
                    }
                }
                pageRanges.add(Map.of("pageNumber", page, "startOffset", start, "endOffset", text.length()));
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("pages", document.getNumberOfPages());
            metadata.put("pageRanges", pageRanges);
            metadata.put("ocrEnabled", ocrService.isEnabled());
            metadata.put("ocrItems", ocrItems);
            metadata.put("ocrFailedCount", ocrFailedCount);
            return new ParsedDocument(path.getFileName().toString(), text.toString(), metadata);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "解析 pdf 文件失败: " + ex.getMessage());
        }
    }

    /**
     * 只有 OCR 开启且当前页原生文本很少时才识别整页图片，避免正常 PDF 文本和 OCR 文本重复入库。
     */
    private boolean shouldOcrPage(String nativeText) {
        if (!ocrService.isEnabled()) {
            return false;
        }
        String normalized = nativeText == null ? "" : nativeText.replaceAll("\\s+", "");
        return normalized.length() < ocrProperties.resolvedPdfMinTextLength();
    }

    /**
     * 将 PDF 页渲染为 PNG 后送入 OCR 服务；渲染或识别失败都由 OCR 失败结果承接，索引流程继续。
     */
    private OcrResult recognizePage(PDFRenderer renderer, int pageNumber, String originalFileName) {
        long startAt = System.currentTimeMillis();
        try {
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, ocrProperties.resolvedPdfDpi());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return ocrService.recognize(output.toByteArray(), originalFileName + "-page-" + pageNumber + ".png");
        } catch (IOException ex) {
            return OcrResult.failure("PDF page render failed: " + ex.getMessage(), System.currentTimeMillis() - startAt);
        }
    }
}
