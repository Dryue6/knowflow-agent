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

    @Override
    @Transactional
    public void indexDocument(Long documentId) {
        Document document = mustGet(documentId);
        try {
            updateDocumentStatus(document, DocumentStatus.PARSING, null, document.getChunkCount());
            ParsedDocument parsed = parserService.parse(document.getFilePath(), document.getFileType());
            updateDocumentStatus(document, DocumentStatus.PARSED, null, document.getChunkCount());
            List<TextChunk> chunks = splitterService.split(parsed.text(), ragProperties.chunkSize(), ragProperties.chunkOverlap());
            updateDocumentStatus(document, DocumentStatus.INDEXING, null, document.getChunkCount());
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
            List<VectorChunkInput> vectorInputs = new ArrayList<>();
            for (int i = 0; i < chunkEntities.size(); i++) {
                DocumentChunk chunk = chunkEntities.get(i);
                vectorInputs.add(new VectorChunkInput(chunk.getKnowledgeBaseId(), chunk.getDocumentId(), chunk.getId(),
                        chunk.getChunkIndex(), chunk.getContent(), embeddings.get(i), chunk.getMetadataJson()));
            }
            List<String> vectorIds = vectorInputs.isEmpty() ? List.of() : vectorStoreService.upsertChunks(vectorInputs);
            for (int i = 0; i < chunkEntities.size(); i++) {
                DocumentChunk chunk = chunkEntities.get(i);
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

    @Override
    @Transactional
    public void deleteDocumentVectors(Long documentId) {
        vectorStoreService.deleteByDocumentId(documentId);
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
    }

    @Override
    public void reindexDocument(Long documentId) {
        indexDocument(documentId);
    }

    private Document mustGet(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null || DocumentStatus.DELETED == document.getStatus()) {
            throw BusinessException.notFound("文档不存在");
        }
        return document;
    }

    private void updateDocumentStatus(Document document, DocumentStatus status, String error, Integer chunkCount) {
        document.setStatus(status);
        document.setErrorMessage(error);
        document.setChunkCount(chunkCount == null ? 0 : chunkCount);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
