package com.example.knowledgeagent.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.entity.DocumentChunk;
import com.example.knowledgeagent.document.enums.DocumentStatus;
import com.example.knowledgeagent.document.mapper.DocumentChunkMapper;
import com.example.knowledgeagent.document.mapper.DocumentMapper;
import com.example.knowledgeagent.document.service.VectorStoreService;
import com.example.knowledgeagent.knowledge.dto.KnowledgeBaseCreateRequest;
import com.example.knowledgeagent.knowledge.dto.KnowledgeBaseUpdateRequest;
import com.example.knowledgeagent.knowledge.entity.KnowledgeBase;
import com.example.knowledgeagent.knowledge.enums.KnowledgeBaseStatus;
import com.example.knowledgeagent.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledgeagent.knowledge.service.KnowledgeBaseService;
import com.example.knowledgeagent.knowledge.vo.KnowledgeBaseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final VectorStoreService vectorStoreService;

    @Override
    @Transactional
    public KnowledgeBaseVO createKnowledgeBase(KnowledgeBaseCreateRequest request) {
        KnowledgeBase entity = new KnowledgeBase();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus(KnowledgeBaseStatus.ACTIVE);
        entity.setDocumentCount(0);
        entity.setChunkCount(0);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        knowledgeBaseMapper.insert(entity);
        return KnowledgeBaseVO.from(entity);
    }

    @Override
    @Transactional
    public KnowledgeBaseVO updateKnowledgeBase(Long id, KnowledgeBaseUpdateRequest request) {
        KnowledgeBase entity = mustGet(id);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus(request.status() == null ? KnowledgeBaseStatus.ACTIVE : request.status());
        entity.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(entity);
        return KnowledgeBaseVO.from(entity);
    }

    @Override
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        mustGet(id);
        vectorStoreService.deleteByKnowledgeBaseId(id);
        knowledgeBaseMapper.deleteById(id);
    }

    @Override
    public KnowledgeBaseVO getKnowledgeBase(Long id) {
        return KnowledgeBaseVO.from(mustGet(id));
    }

    @Override
    public PageResult<KnowledgeBaseVO> pageKnowledgeBases(long page, long size, String keyword) {
        Page<KnowledgeBase> result = knowledgeBaseMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<KnowledgeBase>()
                        .like(StringUtils.hasText(keyword), KnowledgeBase::getName, keyword)
                        .orderByDesc(KnowledgeBase::getUpdatedAt));
        return PageResult.of(result.getRecords().stream().map(KnowledgeBaseVO::from).toList(), result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public void updateStatistics(Long knowledgeBaseId) {
        KnowledgeBase entity = mustGet(knowledgeBaseId);
        Long documentCount = documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .ne(Document::getStatus, DocumentStatus.DELETED));
        Long chunkCount = documentChunkMapper.selectCount(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getKnowledgeBaseId, knowledgeBaseId));
        entity.setDocumentCount(documentCount.intValue());
        entity.setChunkCount(chunkCount.intValue());
        entity.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(entity);
    }

    private KnowledgeBase mustGet(Long id) {
        KnowledgeBase entity = knowledgeBaseMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("知识库不存在");
        }
        return entity;
    }
}
