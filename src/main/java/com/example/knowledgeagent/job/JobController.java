package com.example.knowledgeagent.job;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.job.vo.IndexJobVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class JobController {
    private final DocumentIndexJobService documentIndexJobService;

    @GetMapping("/index-jobs/{jobId}")
    public ApiResult<IndexJobVO> getJob(@PathVariable Long jobId) {
        return ApiResult.ok(documentIndexJobService.getJob(jobId));
    }

    @GetMapping("/documents/{documentId}/index-job")
    public ApiResult<IndexJobVO> latestJob(@PathVariable Long documentId) {
        return ApiResult.ok(documentIndexJobService.getLatestDocumentJob(documentId));
    }
}
