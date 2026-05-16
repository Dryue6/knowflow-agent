package com.example.knowledgeagent.rag.prompt;

import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;
import com.example.knowledgeagent.rag.vo.RagSearchItemVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class RagPromptBuilder {
    public String build(String question, List<ChatHistoryMessage> history, List<RagSearchItemVO> chunks) {
        return """
                你是企业知识库智能助手。请基于给定的企业知识库上下文回答用户问题。

                要求：
                1. 只能依据上下文回答。
                2. 如果上下文没有答案，请说明“当前知识库中没有找到可靠依据”。
                3. 回答要清晰、准确、简洁。
                4. 涉及流程时，用步骤列出。
                5. 涉及制度、金额、时间、条件时，不要编造。
                6. 回答后保留引用来源标记。

                用户问题：
                %s

                历史对话：
                %s

                知识库上下文：
                %s

                请给出回答：
                """.formatted(question, formatHistory(history), formatContext(chunks));
    }

    private String formatHistory(List<ChatHistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        return history.stream().map(item -> item.role() + ": " + item.content()).collect(Collectors.joining("\n"));
    }

    private String formatContext(List<RagSearchItemVO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "无";
        }
        AtomicInteger index = new AtomicInteger(1);
        return chunks.stream().map(chunk -> """
                [来源%s]
                文档：%s
                片段ID：%s
                内容：%s
                """.formatted(index.getAndIncrement(), chunk.documentName(), chunk.chunkId(), chunk.content())).collect(Collectors.joining("\n"));
    }
}
