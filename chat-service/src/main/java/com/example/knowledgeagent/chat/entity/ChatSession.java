package com.example.knowledgeagent.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
/**
 * 定义 ChatSession 组件，承载对应模块的业务职责。
 */
public class ChatSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long knowledgeBaseId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
