package com.example.knowledgeagent.job.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.service.DocumentIndexService;
import com.example.knowledgeagent.job.DocumentIndexJobService;
import com.example.knowledgeagent.job.entity.IndexJob;
import com.example.knowledgeagent.job.enums.IndexJobStatus;
import com.example.knowledgeagent.job.enums.IndexJobType;
import com.example.knowledgeagent.job.mapper.IndexJobMapper;
import com.example.knowledgeagent.job.vo.IndexJobVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexJobServiceImpl implements DocumentIndexJobService {
    private final IndexJobMapper indexJobMapper;
    private final DocumentIndexService documentIndexService;

    @Override
    @Transactional
    public Long createIndexJob(Long documentId, Long knowledgeBaseId, IndexJobType jobType) {
        IndexJob job = new IndexJob();
        job.setDocumentId(documentId);
        job.setKnowledgeBaseId(knowledgeBaseId);
        job.setJobType(jobType);
        job.setStatus(IndexJobStatus.PENDING);
        job.setProgress(0);
        LocalDateTime now = LocalDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        indexJobMapper.insert(job);
        return job.getId();
    }

    @Async
    @Override
    public void indexDocumentAsync(Long jobId, Long documentId) {
        updateJob(jobId, IndexJobStatus.RUNNING, 10, null, true, false);
        try {
            documentIndexService.indexDocument(documentId);
            updateJob(jobId, IndexJobStatus.SUCCESS, 100, null, false, true);
        } catch (Exception ex) {
            log.error("Document index job failed, jobId={}, documentId={}", jobId, documentId, ex);
            updateJob(jobId, IndexJobStatus.FAILED, 100, ex.getMessage(), false, true);
        }
    }

    @Override
    public IndexJobVO getJob(Long jobId) {
        IndexJob job = indexJobMapper.selectById(jobId);
        if (job == null) {
            throw BusinessException.notFound("索引任务不存在");
        }
        return IndexJobVO.from(job);
    }

    @Override
    public IndexJobVO getLatestDocumentJob(Long documentId) {
        IndexJob job = indexJobMapper.selectOne(new LambdaQueryWrapper<IndexJob>()
                .eq(IndexJob::getDocumentId, documentId)
                .orderByDesc(IndexJob::getCreatedAt)
                .last("LIMIT 1"));
        if (job == null) {
            throw BusinessException.notFound("文档暂无索引任务");
        }
        return IndexJobVO.from(job);
    }

    private void updateJob(Long jobId, IndexJobStatus status, int progress, String error, boolean started, boolean finished) {
        IndexJob job = indexJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        job.setStatus(status);
        job.setProgress(progress);
        job.setErrorMessage(error);
        LocalDateTime now = LocalDateTime.now();
        if (started) {
            job.setStartedAt(now);
        }
        if (finished) {
            job.setFinishedAt(now);
        }
        job.setUpdatedAt(now);
        indexJobMapper.updateById(job);
    }
}
