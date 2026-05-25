package com.example.knowledgeagent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.knowledgeagent.knowledge.enums.KnowledgeBaseStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_base")
/**
 * 定义 KnowledgeBase 组件，承载对应模块的业务职责。
 */
public class KnowledgeBase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private KnowledgeBaseStatus status;
    private Integer documentCount;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
