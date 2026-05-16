package com.example.knowledgeagent.rag.vo;

import java.util.List;

public record RagAnswerVO(String answer, List<CitationVO> citations) {
}
