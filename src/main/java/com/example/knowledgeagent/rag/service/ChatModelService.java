package com.example.knowledgeagent.rag.service;

import java.util.function.Consumer;

public interface ChatModelService {
    /**
     * 根据 Prompt 生成完整文本回答。
     */
    String chat(String prompt);

    /**
     * 根据 Prompt 生成回答，并通过回调输出流式片段。
     */
    void streamChat(String prompt, Consumer<String> tokenConsumer);
}
