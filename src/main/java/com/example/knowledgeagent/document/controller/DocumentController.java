package com.example.knowledgeagent.document.controller;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.document.dto.DocumentUploadResponse;
import com.example.knowledgeagent.document.service.DocumentService;
import com.example.knowledgeagent.document.vo.DocumentChunkVO;
import com.example.knowledgeagent.document.vo.DocumentVO;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService documentService;

    /**
     * 上传文档到指定知识库，并创建异步索引任务。
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseId}/documents/upload")
    public ApiResult<DocumentUploadResponse> upload(@PathVariable Long knowledgeBaseId, @RequestPart("file") MultipartFile file) {
        return ApiResult.ok(documentService.uploadDocument(knowledgeBaseId, file));
    }

    /**
     * 分页查询指定知识库下的文档。
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResult<PageResult<DocumentVO>> page(@PathVariable Long knowledgeBaseId,
                                                  @RequestParam(defaultValue = "1") @Min(1) long page,
                                                  @RequestParam(defaultValue = "10") @Min(1) long size,
                                                  @RequestParam(required = false) String keyword) {
        return ApiResult.ok(documentService.pageDocuments(knowledgeBaseId, page, size, keyword));
    }

    /**
     * 查询文档详情。
     */
    @GetMapping("/documents/{documentId}")
    public ApiResult<DocumentVO> detail(@PathVariable Long documentId) {
        return ApiResult.ok(documentService.getDocument(documentId));
    }

    /**
     * 删除文档、切片和向量数据。
     */
    @DeleteMapping("/documents/{documentId}")
    public ApiResult<Void> delete(@PathVariable Long documentId) {
        documentService.deleteDocument(documentId);
        return ApiResult.ok();
    }

    /**
     * 对已有文档重新创建索引任务。
     */
    @PostMapping("/documents/{documentId}/reindex")
    public ApiResult<DocumentUploadResponse> reindex(@PathVariable Long documentId) {
        return ApiResult.ok(documentService.reindexDocument(documentId));
    }

    /**
     * 分页查询文档切片。
     */
    @GetMapping("/documents/{documentId}/chunks")
    public ApiResult<PageResult<DocumentChunkVO>> chunks(@PathVariable Long documentId,
                                                         @RequestParam(defaultValue = "1") @Min(1) long page,
                                                         @RequestParam(defaultValue = "10") @Min(1) long size) {
        return ApiResult.ok(documentService.listDocumentChunks(documentId, page, size));
    }
}
