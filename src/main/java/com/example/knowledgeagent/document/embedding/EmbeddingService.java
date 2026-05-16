package com.example.knowledgeagent.document.embedding;

import java.util.List;

public interface EmbeddingService {
    List<Double> embedText(String text);

    List<List<Double>> embedTexts(List<String> texts);
}
