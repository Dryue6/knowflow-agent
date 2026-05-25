package com.example.knowledgeagent.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.knowledgeagent.document.enums.DocumentConstraintLevel;
import com.example.knowledgeagent.document.enums.DocumentStatus;
import com.example.knowledgeagent.document.enums.FileType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document")
/**
 * 定义 Document 组件，承载对应模块的业务职责。
 */
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long knowledgeBaseId;
    private String fileName;
    private String originalFileName;
    private FileType fileType;
    private Long fileSize;
    private String filePath;
    private String title;
    private DocumentStatus status;
    private String errorMessage;
    private Integer chunkCount;
    private DocumentConstraintLevel constraintLevel;
    private Integer constraintPriority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
