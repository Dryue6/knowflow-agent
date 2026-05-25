package com.example.knowledgeagent.rag.prompt;

import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;
import com.example.knowledgeagent.rag.vo.RagSearchItemVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
/**
 * 定义 RagPromptBuilder 组件，承载对应模块的业务职责。
 */
public class RagPromptBuilder {
    public String build(String question,
                        List<ChatHistoryMessage> history,
                        List<RagSearchItemVO> systemConstraints,
                        List<RagSearchItemVO> pinnedContext,
                        List<RagSearchItemVO> retrievedContext) {
        return """
                你是企业知识库智能助手。请基于给定的知识库上下文回答用户问题。
                要求：
                1. 只能依据下方上下文回答。
                2. 系统级约束优先级最高；如果系统级约束与固定参考资料、普通检索资料或用户临时要求冲突，必须以系统级约束为准。
                3. 固定参考资料优先级高于普通检索资料。
                4. 如果上下文没有答案，请说明“当前知识库中没有找到可靠依据”。
                5. 回答要清晰、准确、简洁；涉及制度、金额、时间、条件时不要编造。
                6. 回答后保留引用来源标记。

                系统级约束：
                %s

                固定参考资料：
                %s

                普通检索资料：
                %s

                历史对话：
                %s

                用户问题：
                %s

                请给出回答：
                """.formatted(
                formatContext(systemConstraints, "系统约束"),
                formatContext(pinnedContext, "固定资料"),
                formatContext(retrievedContext, "检索资料"),
                formatHistory(history),
                question
        );
    }

    /**
     * 构建模型调用所需的提示词或业务响应结构。
     */
    public String build(String question, List<ChatHistoryMessage> history, List<RagSearchItemVO> chunks) {
        return build(question, history, List.of(), List.of(), chunks);
    }

    /**
     * 转换或构建 formatHistory 所需的数据结构。
     */
    private String formatHistory(List<ChatHistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        return history.stream().map(item -> item.role() + ": " + item.content()).collect(Collectors.joining("\n"));
    }

    /**
     * 转换或构建 formatContext 所需的数据结构。
     */
    private String formatContext(List<RagSearchItemVO> chunks, String label) {
        if (chunks == null || chunks.isEmpty()) {
            return "无";
        }
        AtomicInteger index = new AtomicInteger(1);
        return chunks.stream().map(chunk -> """
                [%s%s]
                文档：%s
                片段ID：%s
                内容：%s
                """.formatted(label, index.getAndIncrement(), chunk.documentName(), chunk.chunkId(), chunk.content())).collect(Collectors.joining("\n"));
    }
}
