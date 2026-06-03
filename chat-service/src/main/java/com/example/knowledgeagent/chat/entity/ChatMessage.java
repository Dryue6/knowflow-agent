package com.example.knowledgeagent.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.knowledgeagent.chat.enums.ChatRole;
import com.example.knowledgeagent.common.typehandler.JsonbStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

@Data
@TableName(value = "chat_message", autoResultMap = true)
/**
 * 定义 ChatMessage 组件，承载对应模块的业务职责。
 */
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private ChatRole role;
    private String content;
    @TableField(value = "citations_json", jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String citationsJson;
    private LocalDateTime createdAt;
}
