package com.example.knowledgeagent.rag.service;

import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.entity.DocumentChunk;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.mapper.DocumentChunkMapper;
import com.example.knowledgeagent.document.mapper.DocumentMapper;
import com.example.knowledgeagent.document.parser.DocumentParserService;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import com.example.knowledgeagent.rag.vo.CitationVO;
import com.example.knowledgeagent.rag.vo.RagSearchItemVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
/**
 * 定义 RagCitationService 组件，承载对应模块的业务职责。
 */
public class RagCitationService {
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentParserService parserService;
    private final ObjectMapper objectMapper;

    /**
     * 根据召回切片构建引用信息，并尽量补齐页码、章节、段落等可定位字段。
     */
    public List<CitationVO> build(List<RagSearchItemVO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        Map<Long, DocumentChunk> chunkMap = documentChunkMapper.selectBatchIds(chunks.stream()
                        .map(RagSearchItemVO::chunkId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(DocumentChunk::getId, chunk -> chunk));
        Map<Long, Document> documentMap = chunks.stream()
                .map(RagSearchItemVO::documentId)
                .filter(Objects::nonNull)
                .distinct()
                .map(documentMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Document::getId, document -> document));
        Map<Long, ParsedDocument> parsedCache = new HashMap<>();
        Map<Long, List<String>> pdfPageCache = new HashMap<>();

        return chunks.stream()
                .map(item -> {
                    DocumentChunk chunk = chunkMap.get(item.chunkId());
                    Document document = documentMap.get(item.documentId());
                    Location location = resolveLocation(document, chunk, parsedCache, pdfPageCache);
                    return new CitationVO(
                            item.documentId(),
                            document == null ? item.documentName() : document.getOriginalFileName(),
                            item.chunkId(),
                            item.chunkIndex(),
                            preview(chunk == null ? item.content() : chunk.getContent()),
                            item.score(),
                            location.pageNumber(),
                            location.sectionTitle(),
                            location.paragraphIndex(),
                            location.locationText()
                    );
                })
                .toList();
    }

    /**
     * 对历史消息中的引用 JSON 进行二次补全，兼容旧数据缺少定位字段的情况。
     */
    public List<CitationVO> enrich(List<CitationVO> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        List<RagSearchItemVO> items = citations.stream()
                .map(citation -> {
                    DocumentChunk chunk = citation.chunkId() == null ? null : documentChunkMapper.selectById(citation.chunkId());
                    String content = chunk == null ? citation.contentPreview() : chunk.getContent();
                    return new RagSearchItemVO(citation.documentId(), citation.documentName(), citation.chunkId(), citation.chunkIndex(), content, citation.score());
                })
                .toList();
        List<CitationVO> enriched = build(items);
        Map<Long, CitationVO> byChunk = enriched.stream()
                .filter(citation -> citation.chunkId() != null)
                .collect(Collectors.toMap(CitationVO::chunkId, citation -> citation, (a, b) -> a, LinkedHashMap::new));
        List<CitationVO> result = new ArrayList<>();
        for (CitationVO citation : citations) {
            CitationVO current = citation.chunkId() == null ? null : byChunk.get(citation.chunkId());
            result.add(current == null ? normalize(citation) : current);
        }
        return result;
    }

    /**
     * 规范化引用对象，确保返回结构完整且字段顺序稳定。
     */
    private CitationVO normalize(CitationVO citation) {
        return new CitationVO(
                citation.documentId(),
                citation.documentName(),
                citation.chunkId(),
                citation.chunkIndex(),
                citation.contentPreview(),
                citation.score(),
                citation.pageNumber(),
                citation.sectionTitle(),
                citation.paragraphIndex(),
                citation.locationText()
        );
    }

    /**
     * 按“切片列字段 -> 元数据 JSON -> 原文反查”的优先级解析引用位置。
     */
    private Location resolveLocation(Document document, DocumentChunk chunk, Map<Long, ParsedDocument> parsedCache, Map<Long, List<String>> pdfPageCache) {
        Location columnLocation = locationFromColumns(chunk);
        if (columnLocation.hasLocation()) {
            return columnLocation;
        }
        Location metadataLocation = locationFromMetadata(chunk == null ? null : chunk.getMetadataJson());
        if (metadataLocation.hasLocation()) {
            return metadataLocation;
        }
        if (document == null || chunk == null || !StringUtils.hasText(chunk.getContent())) {
            return Location.empty();
        }
        if (document.getFileType() == FileType.PDF) {
            Integer pageNumber = inferPdfPage(document, chunk.getContent(), pdfPageCache);
            return pageNumber == null ? Location.empty() : new Location(pageNumber, null, null, "第 " + pageNumber + " 页");
        }
        Location textLocation = inferTextLocation(document, chunk.getContent(), parsedCache);
        return textLocation == null ? Location.empty() : textLocation;
    }

    /**
     * 优先使用 document_chunk 表中的结构化定位字段。
     */
    private Location locationFromColumns(DocumentChunk chunk) {
        if (chunk == null) {
            return Location.empty();
        }
        return new Location(chunk.getPageNumber(), chunk.getSectionTitle(), chunk.getParagraphIndex(), chunk.getLocationText());
    }

    /**
     * 从旧版 metadata_json 中读取定位字段，兼容索引升级前的数据。
     */
    private Location locationFromMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Location.empty();
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, new TypeReference<>() {
            });
            Integer pageNumber = asInteger(metadata.get("pageNumber"));
            String sectionTitle = asString(metadata.get("sectionTitle"));
            Integer paragraphIndex = asInteger(metadata.get("paragraphIndex"));
            String locationText = asString(metadata.get("locationText"));
            return new Location(pageNumber, sectionTitle, paragraphIndex, locationText);
        } catch (Exception ignored) {
            return Location.empty();
        }
    }

    /**
     * 通过切片内容在 PDF 每页文本中反查页码，用于缺少 pageNumber 的旧切片。
     */
    private Integer inferPdfPage(Document document, String content, Map<Long, List<String>> pdfPageCache) {
        try {
            List<String> pages = pdfPageCache.computeIfAbsent(document.getId(), ignored -> loadPdfPages(document.getFilePath()));
            List<String> needles = candidateNeedles(content);
            if (needles.isEmpty()) {
                return null;
            }
            // PDF 抽取文本可能有空白差异，因此使用归一化后的候选片段进行包含判断。
            for (int i = 0; i < pages.size(); i++) {
                String page = normalize(pages.get(i));
                if (needles.stream().anyMatch(page::contains)) {
                    return i + 1;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /**
     * 逐页抽取 PDF 文本，供引用定位按页反查使用。
     */
    private List<String> loadPdfPages(String filePath) {
        List<String> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(Path.of(filePath).toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return pages;
    }

    /**
     * 对 TXT、MD、DOCX 等文本型文档，通过原文偏移量推断段落和章节位置。
     */
    private Location inferTextLocation(Document document, String content, Map<Long, ParsedDocument> parsedCache) {
        try {
            ParsedDocument parsed = parsedCache.computeIfAbsent(document.getId(), ignored -> parserService.parse(document.getFilePath(), document.getFileType()));
            int offset = findOffset(parsed.text(), content);
            ParagraphLocation paragraph = paragraphLocation(parsed.text(), offset);
            if (paragraph == null) {
                return Location.empty();
            }
            String locationText = paragraph.sectionTitle() == null || paragraph.sectionTitle().isBlank()
                    ? "第 " + paragraph.index() + " 段"
                    : paragraph.sectionTitle() + " / 第 " + paragraph.index() + " 段";
            return new Location(null, paragraph.sectionTitle(), paragraph.index(), locationText);
        } catch (Exception ignored) {
            return Location.empty();
        }
    }

    /**
     * 在原文中查找切片内容起点，先精确匹配，再用去空白后的短片段兜底匹配。
     */
    private int findOffset(String text, String content) {
        if (text == null || content == null) {
            return -1;
        }
        String needle = content.length() <= 120 ? content.trim() : content.substring(0, 120).trim();
        int offset = text.indexOf(needle);
        if (offset >= 0) {
            return offset;
        }
        String compactNeedle = normalizedNeedle(content);
        if (!StringUtils.hasText(compactNeedle)) {
            return -1;
        }
        return normalize(text).indexOf(compactNeedle);
    }

    /**
     * 根据字符偏移推断所在段落，同时记录最近出现的章节标题。
     */
    private ParagraphLocation paragraphLocation(String text, int offset) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        int cursor = 0;
        int paragraphIndex = 0;
        String sectionTitle = null;
        for (String line : lines) {
            int start = cursor;
            int end = cursor + line.length();
            cursor = end + 1;
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            paragraphIndex++;
            if (isSectionTitle(trimmed)) {
                sectionTitle = cleanSectionTitle(trimmed);
            }
            if (offset >= 0 && offset >= start && offset <= end) {
                return new ParagraphLocation(paragraphIndex, sectionTitle);
            }
        }
        return paragraphIndex == 0 ? null : new ParagraphLocation(1, sectionTitle);
    }

    /**
     * 校验 isSectionTitle 对应的业务条件。
     */
    private boolean isSectionTitle(String text) {
        if (text.length() > 80) {
            return false;
        }
        return text.startsWith("#")
                || text.matches("^第.{1,12}[章节篇部分].*")
                || text.matches("^[一二三四五六七八九十]+[、.．].*")
                || text.matches("^\\d+(\\.\\d+)*[、.．\\s].*");
    }

    /**
     * 处理 cleanSectionTitle 对应的兜底、清洗或默认值逻辑。
     */
    private String cleanSectionTitle(String text) {
        return text.replaceFirst("^#+\\s*", "").trim();
    }

    /**
     * 构造较短的归一化检索片段，避免长切片全文匹配过于脆弱。
     */
    private String normalizedNeedle(String content) {
        String normalized = normalize(content);
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    /**
     * 生成多组候选片段，提高 PDF 页码反查在换行和空白差异下的命中率。
     */
    private List<String> candidateNeedles(String content) {
        String normalized = normalize(content);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        List<String> needles = new ArrayList<>();
        if (normalized.length() >= 12) {
            needles.add(normalized.substring(0, Math.min(80, normalized.length())));
        }
        for (String line : content.replace("\r\n", "\n").split("\n")) {
            String candidate = normalize(line);
            if (candidate.length() >= 12) {
                needles.add(candidate.substring(0, Math.min(80, candidate.length())));
            }
        }
        for (int start = 0; start < normalized.length(); start += 40) {
            int end = Math.min(start + 40, normalized.length());
            if (end - start >= 12) {
                needles.add(normalized.substring(start, end));
            }
        }
        return needles.stream().distinct().limit(12).toList();
    }

    /**
     * 去掉空白字符，用于跨解析器文本差异的宽松匹配。
     */
    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    /**
     * 截断引用预览文本，避免接口返回过长切片内容。
     */
    private String preview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 160 ? content : content.substring(0, 160) + "...";
    }

    /**
     * 将元数据中的数字或数字字符串安全转换为 Integer。
     */
    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 将元数据值安全转换为字符串。
     */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 表示一次引用定位结果，字段全部为空时代表无法定位。
     */
    private record Location(Integer pageNumber, String sectionTitle, Integer paragraphIndex, String locationText) {
        static Location empty() {
            return new Location(null, null, null, null);
        }

        boolean hasLocation() {
            return pageNumber != null || StringUtils.hasText(sectionTitle) || paragraphIndex != null || StringUtils.hasText(locationText);
        }
    }

    /**
     * 记录文本型文档的段落序号和最近章节标题。
     */
    private record ParagraphLocation(int index, String sectionTitle) {
    }
}
