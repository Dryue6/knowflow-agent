package com.example.knowledgeagent.document.service;

import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.document.dto.CompletePresignedUploadRequest;
import com.example.knowledgeagent.document.dto.CreatePresignedUploadUrlRequest;
import com.example.knowledgeagent.document.dto.DocumentUploadResponse;
import com.example.knowledgeagent.document.dto.PresignedUploadUrlResponse;
import com.example.knowledgeagent.document.dto.UpdateDocumentConstraintRequest;
import com.example.knowledgeagent.document.vo.DocumentChunkVO;
import com.example.knowledgeagent.document.vo.DocumentFileResource;
import com.example.knowledgeagent.document.vo.DocumentPreviewTextVO;
import com.example.knowledgeagent.document.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 定义 DocumentService 接口，约定该模块对外提供的能力。
 */
public interface DocumentService {
    /**
     * 上传文档并创建索引任务。
     */
    DocumentUploadResponse uploadDocument(Long knowledgeBaseId, MultipartFile file);

    /**
     * 为前端直传 MinIO 创建短期上传地址。
     */
    PresignedUploadUrlResponse createPresignedUploadUrl(Long knowledgeBaseId, CreatePresignedUploadUrlRequest request);

    /**
     * 确认前端直传完成，并创建文档记录与索引任务。
     */
    DocumentUploadResponse completePresignedUpload(Long knowledgeBaseId, CompletePresignedUploadRequest request);

    /**
     * 查询文档详情。
     */
    DocumentVO getDocument(Long documentId);

    /**
     * 分页查询知识库文档。
     */
    PageResult<DocumentVO> pageDocuments(Long knowledgeBaseId, long page, long size, String keyword);

    /**
     * 删除文档及关联数据。
     */
    void deleteDocument(Long documentId);

    /**
     * 重新索引文档。
     */
    DocumentUploadResponse reindexDocument(Long documentId);

    /**
     * 分页查询文档切片。
     */
    PageResult<DocumentChunkVO> listDocumentChunks(Long documentId, long page, long size);

    /**
     * 声明  能力，由具体实现类完成业务处理。
     */
    DocumentFileResource getDocumentFile(Long documentId);

    /**
     * 声明  能力，由具体实现类完成业务处理。
     */
    DocumentPreviewTextVO previewText(Long documentId);

    /**
     * 声明  能力，由具体实现类完成业务处理。
     */
    DocumentVO updateConstraint(Long documentId, UpdateDocumentConstraintRequest request);
}
