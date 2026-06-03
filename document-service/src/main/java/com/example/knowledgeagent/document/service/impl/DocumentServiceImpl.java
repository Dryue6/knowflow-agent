package com.example.knowledgeagent.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.dto.CompletePresignedUploadRequest;
import com.example.knowledgeagent.document.dto.CreatePresignedUploadUrlRequest;
import com.example.knowledgeagent.document.dto.DocumentUploadResponse;
import com.example.knowledgeagent.document.dto.PresignedUploadUrlResponse;
import com.example.knowledgeagent.document.dto.UpdateDocumentConstraintRequest;
import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.entity.DocumentChunk;
import com.example.knowledgeagent.document.enums.DocumentConstraintLevel;
import com.example.knowledgeagent.document.enums.DocumentStatus;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.mapper.DocumentChunkMapper;
import com.example.knowledgeagent.document.mapper.DocumentMapper;
import com.example.knowledgeagent.document.parser.DocumentParserService;
import com.example.knowledgeagent.document.service.DocumentService;
import com.example.knowledgeagent.document.service.VectorStoreService;
import com.example.knowledgeagent.document.vo.DocumentChunkVO;
import com.example.knowledgeagent.document.vo.DocumentFileResource;
import com.example.knowledgeagent.document.vo.DocumentPreviewTextVO;
import com.example.knowledgeagent.document.vo.DocumentVO;
import com.example.knowledgeagent.job.DocumentIndexJobService;
import com.example.knowledgeagent.job.enums.IndexJobType;
import com.example.knowledgeagent.storage.FileStorageService;
import com.example.knowledgeagent.storage.MinioPresignedUploadService;
import com.example.knowledgeagent.storage.StoredFile;
import com.example.knowledgeagent.storage.StoredFileMaterialization;
import com.example.knowflow.contract.client.KnowledgeClient;
import com.example.knowflow.contract.dto.KnowledgeStatisticsUpdateRequest;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档业务服务实现。
 *
 * <p>该服务只编排文档元数据、索引任务和文件存储抽象；原始文件的真实位置由
 * FileStorageService 负责，避免业务层直接假设本地磁盘路径。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeClient knowledgeClient;
    private final FileStorageService fileStorageService;
    private final MinioPresignedUploadService minioPresignedUploadService;
    private final VectorStoreService vectorStoreService;
    private final DocumentIndexJobService documentIndexJobService;
    private final DocumentParserService documentParserService;

    /**
     * 上传文档并创建异步索引任务。
     *
     * <p>文件先写入存储服务，随后在短事务内写入 document/index_job 记录；
     * 索引任务在事务提交后启动，避免异步线程读取到未提交的文档元数据。</p>
     */
    @Override
    @Transactional
    @GlobalTransactional(name = "document-upload-tx", rollbackFor = Exception.class)
    public DocumentUploadResponse uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        if (!ErrorCode.SUCCESS.getCode().equals(knowledgeClient.getKnowledgeBase(knowledgeBaseId).code())) {
            throw BusinessException.notFound("知识库不存在");
        }
        if (file.isEmpty()) {
            throw BusinessException.badRequest("上传文件不能为空");
        }
        FileType fileType = FileType.fromFileName(file.getOriginalFilename());
        StoredFile storedFile = fileStorageService.store(file, knowledgeBaseId);
        return createDocumentAndIndexJob(knowledgeBaseId, storedFile, fileType);
    }

    /**
     * 创建前端直传 MinIO 的短期上传地址。
     */
    @Override
    public PresignedUploadUrlResponse createPresignedUploadUrl(Long knowledgeBaseId, CreatePresignedUploadUrlRequest request) {
        ensureKnowledgeBaseExists(knowledgeBaseId);
        FileType.fromFileName(request.originalFileName());
        if (request.fileSize() == null || request.fileSize() <= 0) {
            throw BusinessException.badRequest("上传文件大小必须大于 0");
        }
        return minioPresignedUploadService.createUploadUrl(knowledgeBaseId, request.originalFileName());
    }

    /**
     * 确认前端直传完成后创建文档记录与索引任务。
     *
     * <p>对象已经由浏览器写入 MinIO，这里必须重新校验对象存在性、归属前缀和大小，避免客户端伪造完成状态。</p>
     */
    @Override
    @Transactional
    @GlobalTransactional(name = "document-presigned-upload-complete-tx", rollbackFor = Exception.class)
    public DocumentUploadResponse completePresignedUpload(Long knowledgeBaseId, CompletePresignedUploadRequest request) {
        ensureKnowledgeBaseExists(knowledgeBaseId);
        FileType fileType = FileType.fromFileName(request.originalFileName());
        StoredFile storedFile = minioPresignedUploadService.confirmUploadedObject(
                knowledgeBaseId, request.objectKey(), request.originalFileName(), request.fileSize());
        try {
            return createDocumentAndIndexJob(knowledgeBaseId, storedFile, fileType);
        } catch (RuntimeException ex) {
            minioPresignedUploadService.deleteObjectQuietly(request.objectKey());
            throw ex;
        }
    }

    /**
     * 复用文档元数据入库与索引任务创建逻辑，确保 multipart 上传和 MinIO 直传完成后的行为一致。
     */
    private DocumentUploadResponse createDocumentAndIndexJob(Long knowledgeBaseId, StoredFile storedFile, FileType fileType) {
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
        document.setConstraintLevel(DocumentConstraintLevel.NORMAL);
        document.setConstraintPriority(100);
        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);
        Long jobId = documentIndexJobService.createIndexJob(document.getId(), knowledgeBaseId, IndexJobType.INDEX);
        scheduleIndexAfterCommit(jobId, document.getId());
        // 统计刷新是派生数据维护，不放在上传确认事务内，避免 knowledge-service 锁冲突导致文件已上传但确认接口失败。
        return new DocumentUploadResponse(document.getId(), jobId, document.getStatus());
    }

    /**
     * 查询单个文档详情。
     */
    @Override
    public DocumentVO getDocument(Long documentId) {
        return DocumentVO.from(mustGet(documentId));
    }

    /**
     * 按知识库分页查询文档列表，并支持按原始文件名模糊搜索。
     */
    @Override
    public PageResult<DocumentVO> pageDocuments(Long knowledgeBaseId, long page, long size, String keyword) {
        Page<Document> result = documentMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .ne(Document::getStatus, DocumentStatus.DELETED)
                .like(StringUtils.hasText(keyword), Document::getOriginalFileName, keyword)
                .orderByDesc(Document::getCreatedAt));
        return PageResult.of(result.getRecords().stream().map(DocumentVO::from).toList(), result.getTotal(), page, size);
    }

    /**
     * 删除文档及其关联资源。
     *
     * <p>先把文档标记为 DELETED，让正在运行的索引任务在下一次状态检查时尽快中止；
     * 随后清理向量、切片和 MinIO 原文件，避免索引中删除操作长时间等待任务结束。</p>
     */
    @Override
    @Transactional
    @GlobalTransactional(name = "document-delete-tx", rollbackFor = Exception.class)
    public void deleteDocument(Long documentId) {
        Document document = mustGet(documentId);
        document.setStatus(DocumentStatus.DELETED);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        vectorStoreService.deleteByDocumentId(documentId);
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
        fileStorageService.delete(document.getFilePath());
        syncKnowledgeStatistics(document.getKnowledgeBaseId());
    }

    /**
     * 为已有文档重新创建索引任务。
     */
    @Override
    @Transactional
    public DocumentUploadResponse reindexDocument(Long documentId) {
        Document document = mustGet(documentId);
        Long jobId = documentIndexJobService.createIndexJob(documentId, document.getKnowledgeBaseId(), IndexJobType.REINDEX);
        scheduleIndexAfterCommit(jobId, documentId);
        return new DocumentUploadResponse(documentId, jobId, document.getStatus());
    }

    /**
     * 分页查询文档切片，供前端展示解析结果和排查 RAG 召回质量。
     */
    @Override
    public PageResult<DocumentChunkVO> listDocumentChunks(Long documentId, long page, long size) {
        mustGet(documentId);
        Page<DocumentChunk> result = documentChunkMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
                .orderByAsc(DocumentChunk::getChunkIndex));
        return PageResult.of(result.getRecords().stream().map(DocumentChunkVO::from).toList(), result.getTotal(), page, size);
    }

    /**
     * 获取文档原始文件资源，供在线预览和下载接口复用。
     */
    @Override
    public DocumentFileResource getDocumentFile(Long documentId) {
        Document document = mustGet(documentId);
        return new DocumentFileResource(
                fileStorageService.loadAsResource(document.getFilePath()),
                document.getOriginalFileName(),
                mediaType(document),
                fileSize(document));
    }

    /**
     * 获取可直接文本预览的文档内容；PDF 继续走原文件预览以保留版式。
     *
     * <p>MinIO 文件会先物化为临时文件，try-with-resources 关闭后自动清理，避免预览调用残留临时文件。</p>
     */
    @Override
    public DocumentPreviewTextVO previewText(Long documentId) {
        Document document = mustGet(documentId);
        if (document.getFileType() == FileType.PDF) {
            throw BusinessException.badRequest("PDF 文档请使用原文件预览");
        }
        if (document.getFileType() == FileType.DOCX) {
            String content = previewDocxText(document);
            return new DocumentPreviewTextVO(document.getId(), document.getOriginalFileName(), document.getFileType(), content, "TEXT");
        }
        try (StoredFileMaterialization materialization = fileStorageService.materialize(
                document.getFilePath(), document.getOriginalFileName())) {
            String content = switch (document.getFileType()) {
                case TXT, MD -> readUtf8(materialization);
                case DOCX -> throw BusinessException.badRequest("DOCX 文档请使用解析文本预览");
                case PDF -> throw BusinessException.badRequest("PDF 文档请使用原文件预览");
            };
            return new DocumentPreviewTextVO(document.getId(), document.getOriginalFileName(), document.getFileType(), content, "TEXT");
        }
    }

    /**
     * 更新文档约束等级和优先级，影响后续 RAG 上下文注入顺序。
     */
    @Override
    @Transactional
    public DocumentVO updateConstraint(Long documentId, UpdateDocumentConstraintRequest request) {
        Document document = mustGet(documentId);
        document.setConstraintLevel(request.constraintLevel());
        document.setConstraintPriority(request.constraintPriority() == null ? 100 : request.constraintPriority());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        return DocumentVO.from(document);
    }

    /**
     * 在事务提交后启动异步索引任务。
     */
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

    /**
     * 基于 document-service 自有数据计算统计，并通过 Feign 推送给 knowledge-service。
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
                // 统计字段可由后续索引、删除或维护任务再次校准，不能因为派生统计失败回滚文档主流程。
                log.warn("同步知识库统计失败，knowledgeBaseId={}, message={}", knowledgeBaseId, result.message());
            }
        } catch (Exception ex) {
            log.warn("同步知识库统计异常，knowledgeBaseId={}, message={}", knowledgeBaseId, ex.getMessage());
        }
    }

    /**
     * 查询文档并统一处理不存在或已删除的情况。
     */
    /**
     * 校验知识库存在，直传签名和完成确认都必须先做该校验，避免给无效知识库生成对象前缀。
     */
    private void ensureKnowledgeBaseExists(Long knowledgeBaseId) {
        if (!ErrorCode.SUCCESS.getCode().equals(knowledgeClient.getKnowledgeBase(knowledgeBaseId).code())) {
            throw BusinessException.notFound("知识库不存在");
        }
    }

    private Document mustGet(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null || DocumentStatus.DELETED == document.getStatus()) {
            throw BusinessException.notFound("文档不存在");
        }
        return document;
    }

    private long fileSize(Document document) {
        return document.getFileSize() == null ? 0 : document.getFileSize();
    }

    /**
     * 读取 UTF-8 文本文件，读取失败时转换为统一业务异常。
     */
    private String readUtf8(StoredFileMaterialization materialization) {
        try {
            return Files.readString(materialization.path(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "读取文件内容失败: " + ex.getMessage());
        }
    }

    /**
     * DOCX 预览优先展示已经入库的 chunk 内容，确保前端看到的 OCR 文本与 RAG 实际检索内容一致。
     *
     * <p>如果文档还没有完成索引或历史数据缺少 chunk，则回退到原有的临时解析逻辑；该回退只用于预览，
     * 不会写入 document_chunk，避免用户误以为数据库中的召回内容已经刷新。</p>
     */
    private String previewDocxText(Document document) {
        String indexedContent = previewDocxTextFromChunks(document);
        if (StringUtils.hasText(indexedContent)) {
            return indexedContent;
        }
        try (StoredFileMaterialization materialization = fileStorageService.materialize(
                document.getFilePath(), document.getOriginalFileName())) {
            log.info("DOCX 预览触发解析和 OCR，不写入 document_chunk，previewSource=PARSE_ON_DEMAND，documentId={}",
                    document.getId());
            return documentParserService.parse(materialization.path().toString(), document.getFileType()).text();
        }
    }

    /**
     * 按 chunkIndex 拼接已索引内容，chunk 之间加入分隔符，便于在前端快速定位 OCR 段落和切片边界。
     */
    private String previewDocxTextFromChunks(Document document) {
        List<DocumentChunk> chunks = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, document.getId())
                .orderByAsc(DocumentChunk::getChunkIndex));
        if (chunks == null || chunks.isEmpty()) {
            log.info("DOCX 预览未找到已索引 chunk，previewSource=PARSE_ON_DEMAND，documentId={}", document.getId());
            return "";
        }
        List<String> contents = chunks.stream()
                .map(DocumentChunk::getContent)
                .filter(StringUtils::hasText)
                .toList();
        String content = String.join("\n\n---\n\n", contents);
        boolean hasOcr = content.contains("【图片OCR DOCX");
        log.info("DOCX 预览使用已索引 chunk，previewSource=CHUNKS，documentId={}, chunkCount={}, hasDocxOcr={}",
                document.getId(), chunks.size(), hasOcr);
        return content;
    }

    /**
     * 根据业务文件类型推断响应类型，不再依赖本地文件系统探测。
     */
    private MediaType mediaType(Document document) {
        return switch (document.getFileType()) {
            case PDF -> MediaType.APPLICATION_PDF;
            case TXT -> MediaType.TEXT_PLAIN;
            case MD -> MediaType.valueOf("text/markdown");
            case DOCX -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        };
    }
}
