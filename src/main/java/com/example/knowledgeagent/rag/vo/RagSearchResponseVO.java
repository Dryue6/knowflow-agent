package com.example.knowledgeagent.rag.vo;

import java.util.List;

public record RagSearchResponseVO(String query, List<RagSearchItemVO> chunks) {
}
