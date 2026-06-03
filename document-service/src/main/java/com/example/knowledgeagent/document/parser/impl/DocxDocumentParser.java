package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.common.util.TextSanitizer;
import com.example.knowledgeagent.config.OcrProperties;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.ocr.OcrImageNormalizer;
import com.example.knowledgeagent.document.ocr.OcrResult;
import com.example.knowledgeagent.document.ocr.OcrService;
import com.example.knowledgeagent.document.parser.DocumentParser;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
/**
 * 定义 DocxDocumentParser 组件，承载对应模块的业务职责。
 */
public class DocxDocumentParser implements DocumentParser {
    private final OcrService ocrService;
    private final OcrProperties ocrProperties;
    private final DocxImageExtractor docxImageExtractor;
    private final OcrImageNormalizer ocrImageNormalizer;

    /**
     * 声明该解析器支持 DOCX 文件。
     */
    @Override
    public boolean supports(FileType fileType) {
        return FileType.DOCX == fileType;
    }

    /**
     * 使用 Apache POI 抽取 Word 文档文本，并把内嵌图片的 OCR 结果追加为可检索文本。
     */
    @Override
    public ParsedDocument parse(Path path) {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path))) {
            StringBuilder text = new StringBuilder();
            List<DocxImageExtractor.DocxImage> images = docxImageExtractor.extract(document);
            List<Map<String, Object>> ocrItems = new ArrayList<>();
            List<Map<String, Object>> ocrFailures = new ArrayList<>();
            OcrDocxContext context = new OcrDocxContext(path, images, ocrItems, ocrFailures);
            appendDocumentText(document, text, context);
            appendFallbackPictures(text, context);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("paragraphs", document.getParagraphs().size());
            metadata.put("ocrEnabled", ocrService.isEnabled());
            metadata.put("docxOcrEnabled", isDocxOcrEnabled());
            metadata.put("docxImageCount", images.size());
            metadata.put("docxOcrAttemptCount", context.stats.attemptCount);
            metadata.put("docxOcrSuccessCount", context.stats.successCount);
            metadata.put("docxUnsupportedImageCount", context.stats.unsupportedImageCount);
            metadata.put("ocrItems", ocrItems);
            metadata.put("ocrFailures", ocrFailures);
            metadata.put("ocrFailedCount", ocrFailures.size());
            return new ParsedDocument(path.getFileName().toString(), text.toString(), metadata);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "解析 docx 文件失败: " + ex.getMessage());
        }
    }

    /**
     * 按 Word 的结构顺序拼接正文、表格、页眉和页脚；图片 OCR 文本会插入到图片所在 run 附近。
     */
    private void appendDocumentText(XWPFDocument document, StringBuilder text, OcrDocxContext context) {
        if (!isDocxOcrEnabled()) {
            if (!context.images.isEmpty()) {
                context.ocrFailures.add(failureMetadata(0, "DOCX OCR disabled", null));
            }
        }
        appendBodyElements(document.getBodyElements(), "正文", "正文", text, context);
        for (int i = 0; i < document.getHeaderList().size(); i++) {
            XWPFHeader header = document.getHeaderList().get(i);
            String area = "页眉" + (i + 1);
            appendBodyElements(header.getBodyElements(), area, area, text, context);
        }
        for (int i = 0; i < document.getFooterList().size(); i++) {
            XWPFFooter footer = document.getFooterList().get(i);
            String area = "页脚" + (i + 1);
            appendBodyElements(footer.getBodyElements(), area, area, text, context);
        }
    }

    /**
     * 遍历段落和表格时保留结构顺序，确保图片 OCR 文本靠近 Word 中的真实位置。
     */
    private void appendBodyElements(List<IBodyElement> elements, String area, String prefix, StringBuilder text,
                                    OcrDocxContext context) {
        ElementCounters counters = new ElementCounters();
        for (IBodyElement element : elements) {
            if (element.getElementType() == BodyElementType.PARAGRAPH && element instanceof XWPFParagraph paragraph) {
                counters.paragraphIndex++;
                appendParagraph(paragraph, area, prefix + "第" + counters.paragraphIndex + "段", text, context);
            } else if (element.getElementType() == BodyElementType.TABLE && element instanceof XWPFTable table) {
                counters.tableIndex++;
                appendTable(table, area, prefix + "表格" + counters.tableIndex, text, context);
            }
        }
    }

    /**
     * 段落内按 run 顺序写入文本和图片 OCR，避免图片识别结果集中堆到全文末尾。
     */
    private void appendParagraph(XWPFParagraph paragraph, String area, String location, StringBuilder text,
                                 OcrDocxContext context) {
        StringBuilder paragraphText = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            appendRunText(paragraphText, run);
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                appendParagraphText(text, paragraphText.toString());
                paragraphText.setLength(0);
                appendPictureOcrBlock(text, picture.getPictureData(), area, location, context);
            }
        }
        appendParagraphText(text, paragraphText.toString());
    }

    /**
     * 表格按行列递归处理，单元格图片 OCR 会落到对应单元格文本附近。
     */
    private void appendTable(XWPFTable table, String area, String prefix, StringBuilder text, OcrDocxContext context) {
        List<XWPFTableRow> rows = table.getRows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<XWPFTableCell> cells = rows.get(rowIndex).getTableCells();
            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                XWPFTableCell cell = cells.get(cellIndex);
                String cellPrefix = prefix + "-行" + (rowIndex + 1) + "列" + (cellIndex + 1) + "-";
                appendBodyElements(cell.getBodyElements(), area, cellPrefix, text, context);
            }
        }
    }

    /**
     * run.text() 保留同一 run 内的文本内容，空值跳过，防止额外空段落污染切片。
     */
    private void appendRunText(StringBuilder paragraphText, XWPFRun run) {
        String runText = run.text();
        if (StringUtils.hasText(runText)) {
            paragraphText.append(TextSanitizer.removeNullBytes(runText));
        }
    }

    /**
     * 追加普通段落文本，统一换行，给后续切片器提供稳定的段落边界。
     */
    private void appendParagraphText(StringBuilder text, String paragraphText) {
        String sanitized = TextSanitizer.removeNullBytes(paragraphText == null ? "" : paragraphText).trim();
        if (!StringUtils.hasText(sanitized)) {
            return;
        }
        text.append(sanitized).append('\n');
    }

    /**
     * 对 run 内图片进行 OCR，并以独立段落写入当前位置，便于预览和 RAG 召回直接看到图片文字。
     */
    private void appendPictureOcrBlock(StringBuilder text, XWPFPictureData pictureData, String area, String location,
                                       OcrDocxContext context) {
        if (!isDocxOcrEnabled() || pictureData == null || pictureData.getData() == null) {
            return;
        }
        String hash = docxImageExtractor.sha256(pictureData.getData());
        context.seenHashes.add(hash);
        DocxImageExtractor.DocxImage image = context.imagesByHash.get(hash);
        if (image == null) {
            image = new DocxImageExtractor.DocxImage(context.imagesByHash.size() + 1, area,
                    pictureData.suggestFileExtension(), pictureData.getData(), hash, List.of(location));
            context.imagesByHash.put(hash, image);
        }
        OcrEvaluation evaluation = evaluateImage(context, image);
        if (evaluation.result().success()) {
            appendOcrBlock(text, image.imageIndex(), location, evaluation.result().text());
        }
    }

    /**
     * 对 run 遍历仍未覆盖的包级图片做兜底追加，避免非标准 DOCX 关系中的图片完全丢失。
     */
    private void appendFallbackPictures(StringBuilder text, OcrDocxContext context) {
        if (!isDocxOcrEnabled()) {
            return;
        }
        for (DocxImageExtractor.DocxImage image : context.images) {
            if (context.seenHashes.contains(image.hash())) {
                continue;
            }
            OcrEvaluation evaluation = evaluateImage(context, image);
            if (evaluation.result().success()) {
                appendOcrBlock(text, image.imageIndex(), image.primaryLocation(), evaluation.result().text());
            }
        }
    }

    /**
     * 相同 hash 的图片只 OCR 一次，多处出现时复用缓存结果插入到各自位置。
     */
    private OcrEvaluation evaluateImage(OcrDocxContext context, DocxImageExtractor.DocxImage image) {
        OcrEvaluation cached = context.ocrCache.get(image.hash());
        if (cached != null) {
            return cached;
        }
        if (context.stats.attemptCount >= context.maxImages) {
            context.ocrFailures.add(failureMetadata(image.imageIndex(), "DOCX OCR max image limit reached", image));
            OcrEvaluation evaluation = new OcrEvaluation(OcrResult.failure("DOCX OCR max image limit reached", 0));
            context.ocrCache.put(image.hash(), evaluation);
            return evaluation;
        }
        OcrImageNormalizer.NormalizedImage normalized = ocrImageNormalizer.normalize(image.data(), image.extension());
        if (!normalized.ready()) {
            context.stats.unsupportedImageCount++;
            context.ocrFailures.add(failureMetadata(image.imageIndex(), normalized.reason(), image));
            OcrEvaluation evaluation = new OcrEvaluation(OcrResult.failure(normalized.reason(), 0));
            context.ocrCache.put(image.hash(), evaluation);
            return evaluation;
        }
        context.stats.attemptCount++;
        String imageName = context.path.getFileName() + "-docx-image-" + image.imageIndex() + ".png";
        OcrResult ocrResult = ocrService.recognize(normalized.bytes(), imageName);
        if (ocrResult.success()) {
            context.stats.successCount++;
            context.ocrItems.add(ocrItemMetadata(image, ocrResult, normalized));
        } else {
            context.ocrFailures.add(failureMetadata(image.imageIndex(), ocrResult.error(), image));
        }
        OcrEvaluation evaluation = new OcrEvaluation(ocrResult);
        context.ocrCache.put(image.hash(), evaluation);
        return evaluation;
    }

    /**
     * OCR 文本前后保留空行，让切片器能把图片文字识别为稳定的独立块。
     */
    private void appendOcrBlock(StringBuilder text, int imageIndex, String location, String ocrText) {
        String sanitized = TextSanitizer.removeNullBytes(ocrText == null ? "" : ocrText).trim();
        if (!StringUtils.hasText(sanitized)) {
            return;
        }
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
        if (text.length() >= 1 && text.charAt(text.length() - 1) == '\n') {
            text.append('\n');
        }
        text.append("【图片OCR DOCX 第 ").append(imageIndex)
                .append(" 张 / ").append(location).append("】")
                .append(sanitized)
                .append("\n\n");
    }

    /**
     * DOCX OCR 需要同时满足全局 OCR 开关和 DOCX 独立开关，便于本地排障时明确判断。
     */
    private boolean isDocxOcrEnabled() {
        return ocrProperties.resolvedDocx().isEnabled(ocrService.isEnabled());
    }

    /**
     * 构造成功 OCR 的 metadata，保留图片位置、尺寸、字节大小和 hash 方便排查召回来源。
     */
    private Map<String, Object> ocrItemMetadata(DocxImageExtractor.DocxImage image, OcrResult result,
                                                OcrImageNormalizer.NormalizedImage normalized) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", "DOCX_IMAGE");
        metadata.put("imageIndex", image.imageIndex());
        metadata.put("sourceArea", image.area());
        metadata.put("locationText", image.primaryLocation());
        metadata.put("locations", image.locations());
        metadata.put("extension", image.extension());
        metadata.put("byteSize", image.size());
        metadata.put("normalizedWidth", normalized.width());
        metadata.put("normalizedHeight", normalized.height());
        metadata.put("hash", image.hash());
        metadata.put("text", result.text());
        metadata.put("confidence", result.confidence());
        metadata.put("elapsedMs", result.elapsedMs());
        return metadata;
    }

    /**
     * 构造跳过或失败 metadata；图片为空时用于记录整体配置跳过原因。
     */
    private Map<String, Object> failureMetadata(int imageIndex, String reason, DocxImageExtractor.DocxImage image) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("imageIndex", imageIndex);
        metadata.put("reason", reason);
        if (image != null) {
            metadata.put("sourceArea", image.area());
            metadata.put("locationText", image.primaryLocation());
            metadata.put("locations", image.locations());
            metadata.put("extension", image.extension());
            metadata.put("byteSize", image.size());
            metadata.put("hash", image.hash());
        }
        return metadata;
    }

    /**
     * 汇总 DOCX OCR 尝试、成功和跳过数量，供解析 metadata 和排障日志使用。
     */
    private static class OcrDocxStats {
        private int attemptCount;
        private int successCount;
        private int unsupportedImageCount;
    }

    /**
     * DOCX OCR 解析上下文，集中保存去重缓存、统计计数和已落点图片集合。
     */
    private class OcrDocxContext {
        private final Path path;
        private final List<DocxImageExtractor.DocxImage> images;
        private final Map<String, DocxImageExtractor.DocxImage> imagesByHash;
        private final List<Map<String, Object>> ocrItems;
        private final List<Map<String, Object>> ocrFailures;
        private final Map<String, OcrEvaluation> ocrCache = new LinkedHashMap<>();
        private final Set<String> seenHashes = new HashSet<>();
        private final OcrDocxStats stats = new OcrDocxStats();
        private final int maxImages = ocrProperties.resolvedDocx().resolvedMaxImages();

        private OcrDocxContext(Path path, List<DocxImageExtractor.DocxImage> images,
                               List<Map<String, Object>> ocrItems,
                               List<Map<String, Object>> ocrFailures) {
            this.path = path;
            this.images = images;
            this.imagesByHash = images.stream()
                    .collect(Collectors.toMap(DocxImageExtractor.DocxImage::hash, image -> image,
                            (left, ignored) -> left, LinkedHashMap::new));
            this.ocrItems = ocrItems;
            this.ocrFailures = ocrFailures;
        }
    }

    /**
     * OCR 结果缓存项，仅缓存识别结果本身，插入位置由每次图片出现的位置决定。
     */
    private record OcrEvaluation(OcrResult result) {
    }

    /**
     * 记录当前结构层级内的段落和表格序号。
     */
    private static class ElementCounters {
        private int paragraphIndex;
        private int tableIndex;
    }
}
