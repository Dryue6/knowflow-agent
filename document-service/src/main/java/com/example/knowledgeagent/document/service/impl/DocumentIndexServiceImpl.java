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
import com.example.knowledgeagent.document.service.DocumentIndexCancelledException;
import com.example.knowledgeagent.document.service.DocumentIndexService;
import com.example.knowledgeagent.document.service.VectorChunkInput;
import com.example.knowledgeagent.document.service.VectorStoreService;
import com.example.knowledgeagent.document.splitter.DocumentSplitterService;
import com.example.knowledgeagent.document.splitter.TextChunk;
import com.example.knowledgeagent.job.entity.IndexJob;
import com.example.knowledgeagent.job.enums.IndexJobStatus;
import com.example.knowledgeagent.job.mapper.IndexJobMapper;
import com.example.knowledgeagent.storage.FileStorageService;
import com.example.knowledgeagent.storage.StoredFileMaterialization;
import com.example.knowflow.contract.client.KnowledgeClient;
import com.example.knowflow.contract.dto.KnowledgeStatisticsUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * 定义 DocumentIndexServiceImpl 组件，承载对应模块的业务职责。
 */
public class DocumentIndexServiceImpl implements DocumentIndexService {
    private static final Pattern DOCX_OCR_PREFIX_PATTERN = Pattern.compile("【图片OCR DOCX 第 (\\d+) 张 / ([^】]+)】");

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentParserService parserService;
    private final FileStorageService fileStorageService;
    private final DocumentSplitterService splitterService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final KnowledgeClient knowledgeClient;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final IndexJobMapper indexJobMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${document.index.embedding-batch-size:8}")
    private int embeddingBatchSize;

    /**
     * 执行完整的文档索引流程。
     * <p>
     * 流程顺序是：更新状态为解析中 -> 解析原文 -> 切片 -> 清理旧向量/切片 -> 批量生成 embedding
     * -> 保存切片元数据 -> 写入向量库 -> 回填 vectorId -> 更新文档状态和知识库统计。
     * 任一环节失败都会把文档置为 FAILED，并把错误信息写回 document.error_message。
     */
    @Override
    public void indexDocument(Long jobId, Long documentId) {
        Document document = mustGetIndexable(documentId);
        try {
            // 每个状态更新都单独提交，避免解析/embedding 的长耗时让前端一直看不到进度变化。
            updateJobProgress(jobId, 15);
            updateDocumentStatusInTransaction(documentId, DocumentStatus.PARSING, null, null);
            ParsedDocument parsed = parseDocument(document);
            ensureDocumentAlive(documentId);
            updateDocumentStatusInTransaction(documentId, DocumentStatus.PARSED, null, null);
            updateJobProgress(jobId, 40);

            // 切片参数由 rag 配置统一控制，保证索引阶段和召回上下文大小策略一致。
            List<TextChunk> chunks = splitterService.split(parsed.text(), ragProperties.chunkSize(), ragProperties.chunkOverlap());
            updateDocumentStatusInTransaction(documentId, DocumentStatus.INDEXING, null, chunks.size());
            updateJobProgress(jobId, 55);

            List<String> texts = chunks.stream().map(TextChunk::content).toList();
            List<List<Double>> embeddings = texts.isEmpty() ? List.of() : embedTextsInBatches(jobId, texts);
            ensureDocumentAlive(documentId);
            int chunkCount = replaceIndexDataInTransaction(documentId, parsed, chunks, embeddings);
            updateJobProgress(jobId, 95);
            syncKnowledgeStatistics(document.getKnowledgeBaseId());
            Map<String, Object> embeddingMetadata = embeddingService.diagnosticMetadata();
            log.info("文档索引完成，documentId={}, chunkCount={}, avgChunkChars={}, maxChunkChars={}, embeddingModel={}, embeddingProvider={}, embeddingDimension={}, docxImageCount={}, docxOcrSuccessCount={}, ocrChunkCount={}",
                    documentId, chunkCount, averageChunkChars(chunks), maxChunkChars(chunks),
                    embeddingMetadata.get("embeddingModel"), embeddingMetadata.get("embeddingProvider"),
                    embeddingMetadata.get("embeddingConfiguredDimension"), parsed.metadata().get("docxImageCount"),
                    parsed.metadata().get("docxOcrSuccessCount"), ocrChunkCount(chunks));
        } catch (DocumentIndexCancelledException ex) {
            throw ex;
        } catch (BusinessException ex) {
            markDocumentFailed(documentId, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            markDocumentFailed(documentId, ex.getMessage());
            throw new BusinessException(ErrorCode.FILE_ERROR, "文档索引失败: " + ex.getMessage());
        }
    }

    /**
     * 删除指定文档的向量和切片元数据。
     */
    @Override
    @Transactional
    public void deleteDocumentVectors(Long documentId) {
        deleteIndexData(documentId);
    }

    /**
     * 清理旧向量和切片，供重建索引和删除文档复用。
     */
    private void deleteIndexData(Long documentId) {
        vectorStoreService.deleteByDocumentId(documentId);
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
    }

    /**
     * 索引完成后把 document-service 自有统计推送给 knowledge-service。
     */
    private void syncKnowledgeStatistics(Long knowledgeBaseId) {
        Long documentCount = documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .ne(Document::getStatus, DocumentStatus.DELETED));
        Long chunkCount = documentChunkMapper.selectCount(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getKnowledgeBaseId, knowledgeBaseId));
        try {
            var result = knowledgeClient.updateStatistics(new KnowledgeStatisticsUpdateRequest(
                    knowledgeBaseId,
                    documentCount.intValue(),
                    chunkCount.intValue()));
            if (!ErrorCode.SUCCESS.getCode().equals(result.code())) {
                // 知识库统计是冗余展示数据，更新失败只记录告警，避免把已完成的文档索引标记为失败。
                log.warn("索引完成后同步知识库统计失败，knowledgeBaseId={}, message={}", knowledgeBaseId, result.message());
            }
        } catch (Exception ex) {
            log.warn("索引完成后同步知识库统计异常，knowledgeBaseId={}, message={}", knowledgeBaseId, ex.getMessage());
        }
    }

    /**
     * 重建文档索引。当前直接复用完整索引流程，索引流程内部会清理旧数据。
     */
    @Override
    public void reindexDocument(Long jobId, Long documentId) {
        indexDocument(jobId, documentId);
    }

    /**
     * 查询可索引文档并统一处理不存在、已删除的异常语义。
     */
    private Document mustGetIndexable(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw BusinessException.notFound("文档不存在");
        }
        if (DocumentStatus.DELETED == document.getStatus()) {
            throw new DocumentIndexCancelledException("文档已删除，索引任务已中止");
        }
        return document;
    }

    /**
     * 索引阶段需要反复确认文档未被删除，避免用户删除后继续生成 embedding 或写入向量。
     */
    private void ensureDocumentAlive(Long documentId) {
        mustGetIndexable(documentId);
    }

    /**
     * 使用短事务更新文档处理状态，让前端轮询能及时看到阶段变化。
     */
    private void updateDocumentStatusInTransaction(Long documentId, DocumentStatus status, String error, Integer chunkCount) {
        transactionTemplate.executeWithoutResult(tx -> {
            Document document = mustGetIndexable(documentId);
            updateDocumentStatus(document, status, error, chunkCount);
        });
    }

    /**
     * 索引失败只影响未删除文档；如果用户已删除文档，不再把状态覆盖为 FAILED。
     */
    private void markDocumentFailed(Long documentId, String error) {
        transactionTemplate.executeWithoutResult(tx -> {
            Document document = documentMapper.selectById(documentId);
            if (document == null || DocumentStatus.DELETED == document.getStatus()) {
                return;
            }
            updateDocumentStatus(document, DocumentStatus.FAILED, error, document.getChunkCount());
        });
    }

    /**
     * 更新文档处理状态、错误信息和切片数量。
     */
    private void updateDocumentStatus(Document document, DocumentStatus status, String error, Integer chunkCount) {
        document.setStatus(status);
        document.setErrorMessage(error);
        if (chunkCount != null) {
            document.setChunkCount(chunkCount);
        }
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    /**
     * MinIO 中保存的是对象引用，解析器仍以 Path 为输入，因此索引前先物化为临时文件并在解析结束后清理。
     */
    private ParsedDocument parseDocument(Document document) {
        try (StoredFileMaterialization materialization = fileStorageService.materialize(
                document.getFilePath(), document.getOriginalFileName())) {
            return parserService.parse(materialization.path().toString(), document.getFileType());
        }
    }

    /**
     * 按批生成 embedding，保留现有高质量模型链路，同时让长文档的进度能逐批推进。
     */
    private List<List<Double>> embedTextsInBatches(Long jobId, List<String> texts) {
        int safeBatchSize = Math.max(1, embeddingBatchSize);
        List<List<Double>> embeddings = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += safeBatchSize) {
            int end = Math.min(texts.size(), start + safeBatchSize);
            long startAt = System.currentTimeMillis();
            List<List<Double>> batchEmbeddings = embeddingService.embedTexts(texts.subList(start, end));
            if (batchEmbeddings.size() != end - start) {
                throw new BusinessException(ErrorCode.VECTOR_ERROR, "embedding 返回数量与切片数量不一致");
            }
            embeddings.addAll(batchEmbeddings);
            int progress = 60 + (int) Math.floor(20.0 * end / texts.size());
            updateJobProgress(jobId, progress);
            log.info("文档 embedding 批次完成，jobId={}, range={}/{}, costMs={}", jobId, end, texts.size(),
                    System.currentTimeMillis() - startAt);
        }
        return embeddings;
    }

    /**
     * 在一个短事务内替换切片和向量，写入前重新读取文档以拿到用户最新调整的资料层级。
     */
    private int replaceIndexDataInTransaction(Long documentId, ParsedDocument parsed, List<TextChunk> chunks,
                                              List<List<Double>> embeddings) {
        Integer chunkCount = transactionTemplate.execute(tx -> {
            Document latestDocument = mustGetIndexable(documentId);
            // 重建索引时必须先删除旧切片和旧向量，否则同一文档会被重复召回。
            deleteIndexData(documentId);

            List<DocumentChunk> chunkEntities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                TextChunk chunk = chunks.get(i);
                DocumentChunk entity = new DocumentChunk();
                entity.setKnowledgeBaseId(latestDocument.getKnowledgeBaseId());
                entity.setDocumentId(documentId);
                entity.setChunkIndex(chunk.index());
                entity.setContent(chunk.content());
                entity.setContentHash(HashUtils.sha256(chunk.content()));
                entity.setTokenCount(chunk.tokenCount());
                Map<String, Object> metadata = chunkMetadata(latestDocument, parsed, chunk, chunks.size());
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
            if (vectorIds.size() != chunkEntities.size()) {
                throw new BusinessException(ErrorCode.VECTOR_ERROR, "向量库返回主键数量与切片数量不一致");
            }
            for (int i = 0; i < chunkEntities.size(); i++) {
                DocumentChunk chunk = chunkEntities.get(i);
                // vectorId 是向量库侧的主键，回填到 document_chunk 后方便删除、排障和前端展示。
                chunk.setVectorId(vectorIds.get(i));
                chunk.setUpdatedAt(LocalDateTime.now());
                documentChunkMapper.updateById(chunk);
            }
            latestDocument.setTitle(parsed.title());
            updateDocumentStatus(latestDocument, DocumentStatus.INDEXED, null, chunkEntities.size());
            return chunkEntities.size();
        });
        return chunkCount == null ? 0 : chunkCount;
    }

    /**
     * 更新索引任务进度，进度只前进不回退，避免异步阶段乱序导致前端进度条闪回。
     */
    private void updateJobProgress(Long jobId, int progress) {
        if (jobId == null) {
            return;
        }
        IndexJob job = indexJobMapper.selectById(jobId);
        if (job == null || job.getStatus() != IndexJobStatus.RUNNING) {
            return;
        }
        job.setProgress(Math.max(job.getProgress() == null ? 0 : job.getProgress(), progress));
        job.setUpdatedAt(LocalDateTime.now());
        indexJobMapper.updateById(job);
    }

    /**
     * 将元数据对象序列化成 JSON 字符串。
     * <p>
     * 元数据不是索引主流程的关键路径，序列化异常时降级为空对象，避免因为非核心字段阻断索引。
     */
    private Map<String, Object> chunkMetadata(Document document, ParsedDocument parsed, TextChunk chunk, int totalChunks) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", parsed.title());
        metadata.put("source", document.getOriginalFileName());
        // 切片诊断字段用于确认本次索引是否已经使用结构化切片策略，不需要新增数据库列。
        metadata.put("chunkIndex", chunk.index());
        metadata.put("estimatedTokens", chunk.tokenCount());
        metadata.put("charLength", chunk.content() == null ? 0 : chunk.content().length());
        metadata.put("splitStrategy", chunk.splitStrategy());
        if (chunk.index() > 0) {
            metadata.put("prevChunkIndex", chunk.index() - 1);
        }
        if (chunk.index() + 1 < totalChunks) {
            metadata.put("nextChunkIndex", chunk.index() + 1);
        }
        // 将 embedding 配置写入每个 chunk，后续可直接用 SQL 判断切片使用的模型和维度。
        metadata.putAll(embeddingService.diagnosticMetadata());
        metadata.putAll(locationMetadata(document.getFileType(), parsed, chunk));
        metadata.putAll(ocrChunkMetadata(parsed, chunk.content()));
        return metadata;
    }

    /**
     * OCR 切片写入稳定 metadata，方便直接从 chunk 表判断图片文字是否参与索引和召回。
     */
    private Map<String, Object> ocrChunkMetadata(ParsedDocument parsed, String content) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (content == null || !content.contains("【图片OCR DOCX")) {
            return metadata;
        }
        List<Map<String, Object>> matchedItems = new ArrayList<>();
        Matcher matcher = DOCX_OCR_PREFIX_PATTERN.matcher(content);
        while (matcher.find()) {
            Integer imageIndex = asInteger(matcher.group(1));
            Map<String, Object> item = ocrItemByIndex(parsed, imageIndex);
            Map<String, Object> matched = new LinkedHashMap<>();
            matched.put("imageIndex", imageIndex);
            matched.put("locationText", matcher.group(2));
            if (item != null) {
                matched.put("confidence", item.get("confidence"));
                matched.put("sourceArea", item.get("sourceArea"));
                matched.put("hash", item.get("hash"));
            }
            matchedItems.add(matched);
        }
        if (matchedItems.isEmpty()) {
            return metadata;
        }
        Map<String, Object> first = matchedItems.get(0);
        metadata.put("ocr", true);
        metadata.put("ocrSourceType", "DOCX_IMAGE");
        metadata.put("imageIndex", first.get("imageIndex"));
        metadata.put("locationText", first.get("locationText"));
        metadata.put("confidence", first.get("confidence"));
        metadata.put("ocrItems", matchedItems);
        return metadata;
    }

    /**
     * 从解析 metadata 的 OCR 明细中查找图片置信度和 hash 等信息。
     */
    private Map<String, Object> ocrItemByIndex(ParsedDocument parsed, Integer imageIndex) {
        if (imageIndex == null || !(parsed.metadata().get("ocrItems") instanceof List<?> items)) {
            return null;
        }
        for (Object item : items) {
            if (item instanceof Map<?, ?> map && imageIndex.equals(asInteger(map.get("imageIndex")))) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        result.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                return result;
            }
        }
        return null;
    }

    /**
     * 统计含 DOCX 图片 OCR 前缀的切片数，作为索引完成日志中的排障指标。
     */
    private long ocrChunkCount(List<TextChunk> chunks) {
        return chunks.stream()
                .filter(chunk -> chunk.content() != null && chunk.content().contains("【图片OCR DOCX"))
                .count();
    }

    /**
     * 统计平均 chunk 字符数，索引完成日志可直接判断切片参数是否按预期生效。
     */
    private long averageChunkChars(List<TextChunk> chunks) {
        return Math.round(chunks.stream()
                .map(TextChunk::content)
                .filter(value -> value != null && !value.isBlank())
                .mapToInt(String::length)
                .average()
                .orElse(0));
    }

    /**
     * 统计最大 chunk 字符数，便于发现长段落或 OCR 文本是否超出预期窗口。
     */
    private int maxChunkChars(List<TextChunk> chunks) {
        return chunks.stream()
                .map(TextChunk::content)
                .filter(value -> value != null && !value.isBlank())
                .mapToInt(String::length)
                .max()
                .orElse(0);
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
