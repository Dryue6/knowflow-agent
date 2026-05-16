package com.example.knowledgeagent.job;

import com.example.knowledgeagent.job.enums.IndexJobType;
import com.example.knowledgeagent.job.vo.IndexJobVO;

public interface DocumentIndexJobService {
    /**
     * 创建文档索引任务记录。
     */
    Long createIndexJob(Long documentId, Long knowledgeBaseId, IndexJobType jobType);

    /**
     * 异步执行文档索引任务。
     */
    void indexDocumentAsync(Long jobId, Long documentId);

    /**
     * 查询索引任务详情。
     */
    IndexJobVO getJob(Long jobId);

    /**
     * 查询文档最近一次索引任务。
     */
    IndexJobVO getLatestDocumentJob(Long documentId);
}
