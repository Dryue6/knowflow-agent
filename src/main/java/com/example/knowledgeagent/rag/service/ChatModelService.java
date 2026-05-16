package com.example.knowledgeagent.rag.service;

import java.util.function.Consumer;

public interface ChatModelService {
    String chat(String prompt);

    void streamChat(String prompt, Consumer<String> tokenConsumer);
}
