package com.example.knowledgeagent.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.common.util.HashUtils;
import com.example.knowledgeagent.config.RagProperties;
import com.example.knowledgeagent.document.embedding.EmbeddingService;
import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.entity.DocumentChunk;
import com.example.knowledgeagent.document.enums.DocumentStatus;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.mapper.DocumentChunkMapper;
import com.example.knowledgeagent.document.mapper.DocumentMapper;
import com.example.knowledgeagent.document.parser.DocumentParserService;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import com.example.knowledgeagent.document.service.DocumentIndexService;
import com.example.knowledgeagent.document.service.VectorChunkInput;
import com.example.knowledgeagent.document.service.VectorStoreService;
import com.example.knowledgeagent.document.splitter.DocumentSplitterService;
import com.example.knowledgeagent.document.splitter.TextChunk;
import com.example.knowledgeagent.knowledge.service.KnowledgeBaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
/**
 * 定义 DocumentIndexServiceImpl 组件，承载对应模块的业务职责。
 */
public class DocumentIndexServiceImpl implements DocumentIndexService {
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentParserService parserService;
    private final DocumentSplitterService splitterService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    /**
     * 执行完整的文档索引流程。
     * <p>
     * 流程顺序是：更新状态为解析中 -> 解析原文 -> 切片 -> 清理旧向量/切片 -> 批量生成 embedding
     * -> 保存切片元数据 -> 写入向量库 -> 回填 vectorId -> 更新文档状态和知识库统计。
     * 任一环节失败都会把文档置为 FAILED，并把错误信息写回 document.error_message。
     */
    @Override
    @Transactional
    public void indexDocument(Long documentId) {
        Document document = mustGet(documentId);
        try {
            // 解析前先更新状态，方便前端轮询时看到处理进度。
            updateDocumentStatus(document, DocumentStatus.PARSING, null, document.getChunkCount());
            ParsedDocument parsed = parserService.parse(document.getFilePath(), document.getFileType());
            updateDocumentStatus(document, DocumentStatus.PARSED, null, document.getChunkCount());

            // 切片参数由 rag 配置统一控制，保证索引阶段和召回上下文大小策略一致。
            List<TextChunk> chunks = splitterService.split(parsed.text(), ragProperties.chunkSize(), ragProperties.chunkOverlap());
            updateDocumentStatus(document, DocumentStatus.INDEXING, null, document.getChunkCount());

            // 重建索引时必须先删除旧切片和旧向量，否则同一文档会被重复召回。
            deleteDocumentVectors(documentId);
            List<String> texts = chunks.stream().map(TextChunk::content).toList();
            List<List<Double>> embeddings = texts.isEmpty() ? List.of() : embeddingService.embedTexts(texts);
            List<DocumentChunk> chunkEntities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                TextChunk chunk = chunks.get(i);
                DocumentChunk entity = new DocumentChunk();
                entity.setKnowledgeBaseId(document.getKnowledgeBaseId());
                entity.setDocumentId(documentId);
                entity.setChunkIndex(chunk.index());
                entity.setContent(chunk.content());
                entity.setContentHash(HashUtils.sha256(chunk.content()));
                entity.setTokenCount(chunk.tokenCount());
                Map<String, Object> metadata = chunkMetadata(document, parsed, chunk);
                entity.setPageNumber(asInteger(metadata.get("pageNumber")));
                entity.setSectionTitle(asString(metadata.get("sectionTitle")));
                entity.setParagraphIndex(asInteger(metadata.get("paragraphIndex")));
                entity.setLocationText(asString(metadata.get("locationText")));
                entity.setMetadataJson(toJson(metadata));
                LocalDateTime now = LocalDateTime.now();
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                documentChunkMapper.insert(entity);
                chunkEntities.add(entity);
            }

            // 先保存切片获取数据库 chunkId，再把 chunkId 写入向量元数据，便于召回后反查引用来源。
            List<VectorChunkInput> vectorInputs = new ArrayList<>();
            for (int i = 0; i < chunkEntities.size(); i++) {
                DocumentChunk chunk = chunkEntities.get(i);
                vectorInputs.add(new VectorChunkInput(chunk.getKnowledgeBaseId(), chunk.getDocumentId(), chunk.getId(),
                        chunk.getChunkIndex(), chunk.getContent(), embeddings.get(i), chunk.getMetadataJson()));
            }
            List<String> vectorIds = vectorInputs.isEmpty() ? List.of() : vectorStoreService.upsertChunks(vectorInputs);
            for (int i = 0; i < chunkEntities.size(); i++) {
                DocumentChunk chunk = chunkEntities.get(i);
                // vectorId 是向量库侧的主键，回填到 document_chunk 后方便删除、排障和前端展示。
                chunk.setVectorId(vectorIds.get(i));
                chunk.setUpdatedAt(LocalDateTime.now());
                documentChunkMapper.updateById(chunk);
            }
            document.setTitle(parsed.title());
            updateDocumentStatus(document, DocumentStatus.INDEXED, null, chunkEntities.size());
            knowledgeBaseService.updateStatistics(document.getKnowledgeBaseId());
        } catch (BusinessException ex) {
            updateDocumentStatus(document, DocumentStatus.FAILED, ex.getMessage(), document.getChunkCount());
            throw ex;
        } catch (Exception ex) {
            updateDocumentStatus(document, DocumentStatus.FAILED, ex.getMessage(), document.getChunkCount());
            throw new BusinessException(ErrorCode.FILE_ERROR, "文档索引失败: " + ex.getMessage());
        }
    }

    /**
     * 删除指定文档的向量和切片元数据。
     */
    @Override
    @Transactional
    public void deleteDocumentVectors(Long documentId) {
        vectorStoreService.deleteByDocumentId(documentId);
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
    }

    /**
     * 重建文档索引。当前直接复用完整索引流程，索引流程内部会清理旧数据。
     */
    @Override
    public void reindexDocument(Long documentId) {
        indexDocument(documentId);
    }

    /**
     * 查询可索引文档并统一处理不存在、已删除的异常语义。
     */
    private Document mustGet(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null || DocumentStatus.DELETED == document.getStatus()) {
            throw BusinessException.notFound("文档不存在");
        }
        return document;
    }

    /**
     * 更新文档处理状态、错误信息和切片数量。
     */
    private void updateDocumentStatus(Document document, DocumentStatus status, String error, Integer chunkCount) {
        document.setStatus(status);
        document.setErrorMessage(error);
        document.setChunkCount(chunkCount == null ? 0 : chunkCount);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    /**
     * 将元数据对象序列化成 JSON 字符串。
     * <p>
     * 元数据不是索引主流程的关键路径，序列化异常时降级为空对象，避免因为非核心字段阻断索引。
     */
    private Map<String, Object> chunkMetadata(Document document, ParsedDocument parsed, TextChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", parsed.title());
        metadata.put("source", document.getOriginalFileName());
        metadata.putAll(locationMetadata(document.getFileType(), parsed, chunk));
        return metadata;
    }

    /**
     * 根据文件类型构造切片定位元数据，PDF 使用页码，文本型文档使用段落和章节。
     */
    private Map<String, Object> locationMetadata(FileType fileType, ParsedDocument parsed, TextChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (fileType == FileType.PDF) {
            Integer pageNumber = pageNumber(parsed.metadata().get("pageRanges"), chunk.startOffset());
            if (pageNumber != null) {
                metadata.put("pageNumber", pageNumber);
                metadata.put("locationText", "第 " + pageNumber + " 页");
            }
            return metadata;
        }

        ParagraphLocation paragraph = paragraphLocation(parsed.text(), chunk.startOffset());
        if (paragraph != null) {
            metadata.put("paragraphIndex", paragraph.index());
            if (paragraph.sectionTitle() != null && !paragraph.sectionTitle().isBlank()) {
                metadata.put("sectionTitle", paragraph.sectionTitle());
                metadata.put("locationText", paragraph.sectionTitle() + " / 第 " + paragraph.index() + " 段");
            } else {
                metadata.put("locationText", "第 " + paragraph.index() + " 段");
            }
        }
        return metadata;
    }

    /**
     * 查询 pageNumber 对应的数据或业务结果。
     */
    private Integer pageNumber(Object pageRanges, int offset) {
        if (!(pageRanges instanceof List<?> ranges) || offset < 0) {
            return null;
        }
        for (Object item : ranges) {
            if (!(item instanceof Map<?, ?> range)) {
                continue;
            }
            Integer page = asInteger(range.get("pageNumber"));
            Integer start = asInteger(range.get("startOffset"));
            Integer end = asInteger(range.get("endOffset"));
            if (page != null && start != null && end != null && offset >= start && offset <= end) {
                return page;
            }
        }
        return null;
    }

    /**
     * 根据切片起始偏移推断段落位置，并记录最近出现的章节标题。
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
            if (offset >= start && offset <= end) {
                return new ParagraphLocation(paragraphIndex, sectionTitle);
            }
        }
        return new ParagraphLocation(Math.max(1, paragraphIndex), sectionTitle);
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
     * 将解析元数据中的数字或数字字符串安全转为 Integer。
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
     * 将解析元数据值安全转为字符串。
     */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 转换或构建 toJson 所需的数据结构。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    /**
     * 保存切片所属段落序号和章节标题。
     */
    private record ParagraphLocation(int index, String sectionTitle) {
    }
}
