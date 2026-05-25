package com.example.knowledgeagent.chat.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowledgeagent.chat.entity.ChatMessage;
import com.example.knowledgeagent.chat.mapper.ChatMessageMapper;
import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 定义 DbChatMemoryService 组件，承载对应模块的业务职责。
 */
public class DbChatMemoryService implements ChatMemoryService {
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 查询最近的会话消息并恢复为时间正序。
     * <p>
     * 数据库查询为了效率按倒序取最近 N 条，返回给模型前再排序为正序，
     * 确保历史对话阅读顺序符合自然对话。
     */
    @Override
    public List<ChatHistoryMessage> recentHistory(Long sessionId, int limit) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .last("LIMIT " + limit))
                .stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(message -> new ChatHistoryMessage(message.getRole().getValue(), message.getContent()))
                .toList();
    }
}
