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
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
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
                entity.setMetadataJson(toJson(Map.of("title", parsed.title(), "source", document.getOriginalFileName())));
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
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
