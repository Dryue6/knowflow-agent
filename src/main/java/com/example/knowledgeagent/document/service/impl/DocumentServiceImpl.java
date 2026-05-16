package com.example.knowledgeagent.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.dto.DocumentUploadResponse;
import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.entity.DocumentChunk;
import com.example.knowledgeagent.document.enums.DocumentStatus;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.mapper.DocumentChunkMapper;
import com.example.knowledgeagent.document.mapper.DocumentMapper;
import com.example.knowledgeagent.document.service.DocumentService;
import com.example.knowledgeagent.document.service.VectorStoreService;
import com.example.knowledgeagent.document.vo.DocumentChunkVO;
import com.example.knowledgeagent.document.vo.DocumentVO;
import com.example.knowledgeagent.job.DocumentIndexJobService;
import com.example.knowledgeagent.job.enums.IndexJobType;
import com.example.knowledgeagent.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledgeagent.knowledge.service.KnowledgeBaseService;
import com.example.knowledgeagent.storage.FileStorageService;
import com.example.knowledgeagent.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final FileStorageService fileStorageService;
    private final VectorStoreService vectorStoreService;
    private final DocumentIndexJobService documentIndexJobService;

    @Override
    @Transactional
    public DocumentUploadResponse uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        if (knowledgeBaseMapper.selectById(knowledgeBaseId) == null) {
            throw BusinessException.notFound("知识库不存在");
        }
        if (file.isEmpty()) {
            throw BusinessException.badRequest("上传文件不能为空");
        }
        FileType fileType = FileType.fromFileName(file.getOriginalFilename());
        StoredFile storedFile = fileStorageService.store(file, knowledgeBaseId);
        Document document = new Document();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileName(storedFile.fileName());
        document.setOriginalFileName(storedFile.originalFileName());
        document.setFileType(fileType);
        document.setFileSize(storedFile.size());
        document.setFilePath(storedFile.filePath());
        document.setTitle(storedFile.originalFileName());
        document.setStatus(DocumentStatus.UPLOADED);
        document.setChunkCount(0);
        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);
        Long jobId = documentIndexJobService.createIndexJob(document.getId(), knowledgeBaseId, IndexJobType.INDEX);
        scheduleIndexAfterCommit(jobId, document.getId());
        knowledgeBaseService.updateStatistics(knowledgeBaseId);
        return new DocumentUploadResponse(document.getId(), jobId, document.getStatus());
    }

    @Override
    public DocumentVO getDocument(Long documentId) {
        return DocumentVO.from(mustGet(documentId));
    }

    @Override
    public PageResult<DocumentVO> pageDocuments(Long knowledgeBaseId, long page, long size, String keyword) {
        Page<Document> result = documentMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .ne(Document::getStatus, DocumentStatus.DELETED)
                .like(StringUtils.hasText(keyword), Document::getOriginalFileName, keyword)
                .orderByDesc(Document::getCreatedAt));
        return PageResult.of(result.getRecords().stream().map(DocumentVO::from).toList(), result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        Document document = mustGet(documentId);
        vectorStoreService.deleteByDocumentId(documentId);
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
        fileStorageService.delete(document.getFilePath());
        document.setStatus(DocumentStatus.DELETED);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        knowledgeBaseService.updateStatistics(document.getKnowledgeBaseId());
    }

    @Override
    @Transactional
    public DocumentUploadResponse reindexDocument(Long documentId) {
        Document document = mustGet(documentId);
        Long jobId = documentIndexJobService.createIndexJob(documentId, document.getKnowledgeBaseId(), IndexJobType.REINDEX);
        scheduleIndexAfterCommit(jobId, documentId);
        return new DocumentUploadResponse(documentId, jobId, document.getStatus());
    }

    @Override
    public PageResult<DocumentChunkVO> listDocumentChunks(Long documentId, long page, long size) {
        mustGet(documentId);
        Page<DocumentChunk> result = documentChunkMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
                .orderByAsc(DocumentChunk::getChunkIndex));
        return PageResult.of(result.getRecords().stream().map(DocumentChunkVO::from).toList(), result.getTotal(), page, size);
    }

    private void scheduleIndexAfterCommit(Long jobId, Long documentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            documentIndexJobService.indexDocumentAsync(jobId, documentId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentIndexJobService.indexDocumentAsync(jobId, documentId);
            }
        });
    }

    private Document mustGet(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null || DocumentStatus.DELETED == document.getStatus()) {
            throw BusinessException.notFound("文档不存在");
        }
        return document;
    }
}

