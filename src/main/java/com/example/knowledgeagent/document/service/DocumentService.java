package com.example.knowledgeagent.document.service;

import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.document.dto.DocumentUploadResponse;
import com.example.knowledgeagent.document.vo.DocumentChunkVO;
import com.example.knowledgeagent.document.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {
    DocumentUploadResponse uploadDocument(Long knowledgeBaseId, MultipartFile file);

    DocumentVO getDocument(Long documentId);

    PageResult<DocumentVO> pageDocuments(Long knowledgeBaseId, long page, long size, String keyword);

    void deleteDocument(Long documentId);

    DocumentUploadResponse reindexDocument(Long documentId);

    PageResult<DocumentChunkVO> listDocumentChunks(Long documentId, long page, long size);
}
