package com.example.knowledgeagent.agent.tools;

import com.example.knowledgeagent.rag.dto.RagSearchRequest;
import com.example.knowledgeagent.rag.service.RagService;
import com.example.knowledgeagent.rag.vo.RagSearchResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {
    private final RagService ragService;

    /**
     * Agent 工具：检索知识库相似片段。
     */
    public RagSearchResponseVO search(Long knowledgeBaseId, String query, Integer topK) {
        return ragService.retrieve(new RagSearchRequest(knowledgeBaseId, query, topK, null));
    }
}
