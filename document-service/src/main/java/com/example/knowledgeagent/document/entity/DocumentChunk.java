package com.example.knowledgeagent.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.knowledgeagent.common.typehandler.JsonbStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

@Data
@TableName(value = "document_chunk", autoResultMap = true)
/**
 * 定义 DocumentChunk 组件，承载对应模块的业务职责。
 */
public class DocumentChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long knowledgeBaseId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String contentHash;
    private Integer tokenCount;
    private String vectorId;
    private Integer pageNumber;
    private String sectionTitle;
    private Integer paragraphIndex;
    private String locationText;
    @TableField(value = "metadata_json", jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
