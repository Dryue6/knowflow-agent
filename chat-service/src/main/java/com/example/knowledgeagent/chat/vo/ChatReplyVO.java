package com.example.knowledgeagent.chat.vo;

import com.example.knowflow.contract.dto.CitationItem;

import java.util.List;

/**
 * 定义 ChatReplyVO 数据结构，用于在层间传递结构化数据。
 */
public record ChatReplyVO(Long userMessageId, Long assistantMessageId, String answer, List<CitationItem> citations) {
}
