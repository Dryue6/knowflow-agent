package com.example.knowledgeagent.chat.vo;

import com.example.knowledgeagent.rag.vo.CitationVO;

import java.util.List;

public record ChatReplyVO(Long userMessageId, Long assistantMessageId, String answer, List<CitationVO> citations) {
}
