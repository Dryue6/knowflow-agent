package com.example.knowledgeagent.document.parser.impl;

import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 DOCX 结构提取图片并记录来源位置，避免只用包级 getAllPictures 时丢失图片上下文。
 */
@Component
public class DocxImageExtractor {
    /**
     * 从正文、表格、页眉、页脚和包级兜底图片中提取去重后的图片列表。
     */
    public List<DocxImage> extract(XWPFDocument document) {
        Map<String, MutableDocxImage> images = new LinkedHashMap<>();
        scanParagraphs(document.getParagraphs(), "正文", "正文", images);
        scanTables(document.getTables(), "正文", "正文", images);
        for (int i = 0; i < document.getHeaderList().size(); i++) {
            XWPFHeader header = document.getHeaderList().get(i);
            String area = "页眉" + (i + 1);
            scanParagraphs(header.getParagraphs(), area, area, images);
            scanTables(header.getTables(), area, area, images);
        }
        for (int i = 0; i < document.getFooterList().size(); i++) {
            XWPFFooter footer = document.getFooterList().get(i);
            String area = "页脚" + (i + 1);
            scanParagraphs(footer.getParagraphs(), area, area, images);
            scanTables(footer.getTables(), area, area, images);
        }
        scanPackagePictures(document.getAllPictures(), images);
        return images.values().stream()
                .map(MutableDocxImage::toDocxImage)
                .toList();
    }

    /**
     * 扫描段落 run 中的嵌入图片，并记录段落级位置。
     */
    private void scanParagraphs(List<XWPFParagraph> paragraphs, String area, String prefix,
                                Map<String, MutableDocxImage> images) {
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph paragraph = paragraphs.get(i);
            String location = prefix + "第" + (i + 1) + "段";
            for (XWPFRun run : paragraph.getRuns()) {
                for (XWPFPicture picture : run.getEmbeddedPictures()) {
                    addPicture(picture.getPictureData(), area, location, images);
                }
            }
        }
    }

    /**
     * 递归扫描表格单元格中的段落和嵌套表格，覆盖表格截图这类常见资料格式。
     */
    private void scanTables(List<XWPFTable> tables, String area, String prefix, Map<String, MutableDocxImage> images) {
        for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
            XWPFTable table = tables.get(tableIndex);
            List<XWPFTableRow> rows = table.getRows();
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<XWPFTableCell> cells = rows.get(rowIndex).getTableCells();
                for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                    XWPFTableCell cell = cells.get(cellIndex);
                    String cellPrefix = prefix + "表格" + (tableIndex + 1)
                            + "-行" + (rowIndex + 1) + "列" + (cellIndex + 1) + "-";
                    scanParagraphs(cell.getParagraphs(), area, cellPrefix, images);
                    scanTables(cell.getTables(), area, cellPrefix, images);
                }
            }
        }
    }

    /**
     * 包级图片作为兜底来源，能暴露 run 遍历未覆盖的图片，但位置只能记录为未知。
     */
    private void scanPackagePictures(List<XWPFPictureData> pictures, Map<String, MutableDocxImage> images) {
        for (XWPFPictureData picture : pictures) {
            addPicture(picture, "包级兜底", "未知位置", images);
        }
    }

    /**
     * 按图片内容 hash 去重，并保留重复出现的位置列表。
     */
    private void addPicture(XWPFPictureData picture, String area, String location, Map<String, MutableDocxImage> images) {
        if (picture == null || picture.getData() == null) {
            return;
        }
        byte[] data = picture.getData();
        String hash = sha256(data);
        MutableDocxImage image = images.computeIfAbsent(hash, ignored -> new MutableDocxImage(
                images.size() + 1,
                area,
                picture.suggestFileExtension(),
                data,
                hash));
        image.locations().add(location);
    }

    /**
     * 计算图片字节摘要，用于跨段落、表格和包级兜底去重。
     */
    public String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * DOCX 图片候选，locations 记录同一图片在文档中的一个或多个来源。
     */
    public record DocxImage(int imageIndex, String area, String extension, byte[] data, String hash,
                            List<String> locations) {
        /**
         * 用第一个结构化位置作为 OCR 文本前缀，全部位置仍写入 metadata。
         */
        public String primaryLocation() {
            return locations.isEmpty() ? area : locations.get(0);
        }

        /**
         * 图片原始字节大小，供 OCR 跳过策略和 metadata 使用。
         */
        public int size() {
            return data == null ? 0 : data.length;
        }
    }

    /**
     * 构建阶段的可变图片对象，最终会转成不可变 record 返回。
     */
    private record MutableDocxImage(int imageIndex, String area, String extension, byte[] data, String hash,
                                    List<String> locations) {
        private MutableDocxImage(int imageIndex, String area, String extension, byte[] data, String hash) {
            this(imageIndex, area, extension, data, hash, new ArrayList<>());
        }

        private DocxImage toDocxImage() {
            return new DocxImage(imageIndex, area, extension, data, hash, List.copyOf(locations));
        }
    }
}
