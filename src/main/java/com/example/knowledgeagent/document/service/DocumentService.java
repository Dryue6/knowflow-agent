package com.example.knowledgeagent.document.service;

import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.document.dto.DocumentUploadResponse;
import com.example.knowledgeagent.document.vo.DocumentChunkVO;
import com.example.knowledgeagent.document.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {
    /**
     * 上传文档并创建索引任务。
     */
    DocumentUploadResponse uploadDocument(Long knowledgeBaseId, MultipartFile file);

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
}
