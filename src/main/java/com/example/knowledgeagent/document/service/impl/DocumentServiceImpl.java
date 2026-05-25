package com.example.knowledgeagent.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.dto.DocumentUploadResponse;
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
import com.example.knowledgeagent.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledgeagent.knowledge.service.KnowledgeBaseService;
import com.example.knowledgeagent.storage.FileStorageService;
import com.example.knowledgeagent.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
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
import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
/**
 * 定义 DocumentServiceImpl 组件，承载对应模块的业务职责。
 */
public class DocumentServiceImpl implements DocumentService {
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final FileStorageService fileStorageService;
    private final VectorStoreService vectorStoreService;
    private final DocumentIndexJobService documentIndexJobService;
    private final DocumentParserService documentParserService;

    /**
     * 上传文档并创建异步索引任务。
     * <p>
     * 这个方法只负责完成“文件落盘 + 文档元数据入库 + 创建任务”，真正的解析、切片、
     * embedding 和向量写入会在事务提交后异步执行，避免索引线程读取到尚未提交的文档记录。
     */
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
        document.setConstraintLevel(DocumentConstraintLevel.NORMAL);
        document.setConstraintPriority(100);
        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);
        Long jobId = documentIndexJobService.createIndexJob(document.getId(), knowledgeBaseId, IndexJobType.INDEX);
        scheduleIndexAfterCommit(jobId, document.getId());
        knowledgeBaseService.updateStatistics(knowledgeBaseId);
        return new DocumentUploadResponse(document.getId(), jobId, document.getStatus());
    }

    /**
     * 查询单个文档的业务详情。
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
     * <p>
     * 当前实现会删除向量数据、切片元数据、本地原始文件，并将文档状态标记为 DELETED。
     * 采用软删除状态是为了后续保留审计或恢复空间，同时列表查询会过滤 DELETED。
     */
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

    /**
     * 为已有文档重新创建索引任务。
     * <p>
     * 重建索引会复用原始文件路径，后续异步索引流程会先清理旧切片和旧向量，再写入新结果。
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
     * 分页查询文档切片，用于前端展示解析结果、排查 RAG 召回质量。
     */
    @Override
    public PageResult<DocumentChunkVO> listDocumentChunks(Long documentId, long page, long size) {
        mustGet(documentId);
        Page<DocumentChunk> result = documentChunkMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
                .orderByAsc(DocumentChunk::getChunkIndex));
        return PageResult.of(result.getRecords().stream().map(DocumentChunkVO::from).toList(), result.getTotal(), page, size);
    }

    @Override
    /**
     * 获取文档原始文件资源，供在线预览和下载接口复用。
     */
    public DocumentFileResource getDocumentFile(Long documentId) {
        Document document = mustGet(documentId);
        Path path = existingFile(document);
        return new DocumentFileResource(new PathResource(path), document.getOriginalFileName(), mediaType(document, path), fileSize(path));
    }

    @Override
    /**
     * 获取可直接文本预览的文档内容，PDF 继续走原文件预览以保留版式。
     */
    public DocumentPreviewTextVO previewText(Long documentId) {
        Document document = mustGet(documentId);
        Path path = existingFile(document);
        String content = switch (document.getFileType()) {
            case TXT, MD -> readUtf8(path);
            case DOCX -> documentParserService.parse(path.toString(), document.getFileType()).text();
            case PDF -> throw BusinessException.badRequest("PDF 文档请使用原文件预览");
        };
        return new DocumentPreviewTextVO(document.getId(), document.getOriginalFileName(), document.getFileType(), content, "TEXT");
    }

    @Override
    @Transactional
    /**
     * 更新文档约束等级和优先级，影响后续 RAG 上下文注入顺序。
     */
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
     * <p>
     * 上传和重建索引都在事务中创建 document/index_job 记录；如果立即启动异步线程，
     * 线程可能先于事务提交执行，导致查询不到刚插入的文档。因此这里注册 afterCommit 回调，
     * 确保索引任务只在数据库状态稳定后开始。
     */
    private void scheduleIndexAfterCommit(Long jobId, Long documentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            documentIndexJobService.indexDocumentAsync(jobId, documentId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            /**
             * 事务提交后触发异步索引，避免索引线程读取到未提交数据。
             */
            public void afterCommit() {
                documentIndexJobService.indexDocumentAsync(jobId, documentId);
            }
        });
    }

    /**
     * 查询文档并统一处理不存在或已删除的情况。
     */
    private Document mustGet(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null || DocumentStatus.DELETED == document.getStatus()) {
            throw BusinessException.notFound("文档不存在");
        }
        return document;
    }

    /**
     * 校验本地文件仍然存在，并返回规范化后的绝对路径。
     */
    private Path existingFile(Document document) {
        Path path = Path.of(document.getFilePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "文件不存在或已被移除");
        }
        return path;
    }

    /**
     * 读取文件大小，读取失败时转换为统一业务异常。
     */
    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "读取文件大小失败: " + ex.getMessage());
        }
    }

    /**
     * 处理 readUtf8 方法对应的业务逻辑。
     */
    private String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "读取文件内容失败: " + ex.getMessage());
        }
    }

    /**
     * 推断文件响应类型，优先使用系统探测结果，失败时按业务文件类型兜底。
     */
    private MediaType mediaType(Document document, Path path) {
        try {
            String contentType = Files.probeContentType(path);
            if (StringUtils.hasText(contentType)) {
                return MediaType.parseMediaType(contentType);
            }
        } catch (Exception ignored) {
        }
        return switch (document.getFileType()) {
            case PDF -> MediaType.APPLICATION_PDF;
            case TXT -> MediaType.TEXT_PLAIN;
            case MD -> MediaType.valueOf("text/markdown");
            case DOCX -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        };
    }
}

