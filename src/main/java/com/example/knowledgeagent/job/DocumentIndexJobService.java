package com.example.knowledgeagent.job;

import com.example.knowledgeagent.job.enums.IndexJobType;
import com.example.knowledgeagent.job.vo.IndexJobVO;

public interface DocumentIndexJobService {
    Long createIndexJob(Long documentId, Long knowledgeBaseId, IndexJobType jobType);

    void indexDocumentAsync(Long jobId, Long documentId);

    IndexJobVO getJob(Long jobId);

    IndexJobVO getLatestDocumentJob(Long documentId);
}
