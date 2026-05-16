package com.example.knowledgeagent.knowledge.service;

import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.knowledge.dto.KnowledgeBaseCreateRequest;
import com.example.knowledgeagent.knowledge.dto.KnowledgeBaseUpdateRequest;
import com.example.knowledgeagent.knowledge.vo.KnowledgeBaseVO;

public interface KnowledgeBaseService {
    KnowledgeBaseVO createKnowledgeBase(KnowledgeBaseCreateRequest request);

    KnowledgeBaseVO updateKnowledgeBase(Long id, KnowledgeBaseUpdateRequest request);

    void deleteKnowledgeBase(Long id);

    KnowledgeBaseVO getKnowledgeBase(Long id);

    PageResult<KnowledgeBaseVO> pageKnowledgeBases(long page, long size, String keyword);

    void updateStatistics(Long knowledgeBaseId);
}
