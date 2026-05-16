package com.example.knowledgeagent.agent.tools;

import com.example.knowledgeagent.document.service.DocumentService;
import com.example.knowledgeagent.document.vo.DocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentSummaryTool {
    private final DocumentService documentService;

    public DocumentVO document(Long documentId) {
        return documentService.getDocument(documentId);
    }
}
