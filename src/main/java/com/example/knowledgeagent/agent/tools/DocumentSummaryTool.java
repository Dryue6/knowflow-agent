package com.example.knowledgeagent.agent.tools;

import com.example.knowledgeagent.document.service.DocumentService;
import com.example.knowledgeagent.document.vo.DocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentSummaryTool {
    private final DocumentService documentService;

    /**
     * Agent 工具：查询文档详情，可用于后续文档摘要或文档解释能力。
     */
    public DocumentVO document(Long documentId) {
        return documentService.getDocument(documentId);
    }
}
