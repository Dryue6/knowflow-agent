package com.example.knowledgeagent.agent.tools;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowflow.contract.client.RagClient;
import com.example.knowflow.contract.dto.RagSearchCommand;
import com.example.knowflow.contract.dto.RagSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/**
 * 定义 KnowledgeSearchTool 组件，承载对应模块的业务职责。
 */
public class KnowledgeSearchTool {
    private final RagClient ragClient;

    /**
     * Agent 工具：检索知识库相似片段。
     */
    public RagSearchResult search(Long knowledgeBaseId, String query, Integer topK) {
        var response = ragClient.search(new RagSearchCommand(knowledgeBaseId, query, topK, null));
        if (!ErrorCode.SUCCESS.getCode().equals(response.code())) {
            throw new BusinessException(ErrorCode.AI_ERROR, response.message());
        }
        return response.data();
    }
}
