package com.example.knowledgeagent.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.knowledgeagent.job.enums.IndexJobStatus;
import com.example.knowledgeagent.job.enums.IndexJobType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("index_job")
/**
 * 定义 IndexJob 组件，承载对应模块的业务职责。
 */
public class IndexJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Long knowledgeBaseId;
    private IndexJobType jobType;
    private IndexJobStatus status;
    private Integer progress;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
