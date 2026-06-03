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
        if (!hasKnowledgeContext(pinnedContext, retrievedContext)) {
            return buildGeneralAnswerPrompt(question, history);
        }
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
     * 判断是否存在可用于回答问题的知识库资料。
     * <p>
     * 系统约束只用于限制模型行为，不代表当前问题已经命中业务资料。
     * 因此普通检索资料和固定资料都为空时，应进入本地模型通用回答分支。
     */
    private boolean hasKnowledgeContext(List<RagSearchItemVO> pinnedContext, List<RagSearchItemVO> retrievedContext) {
        return hasItems(pinnedContext) || hasItems(retrievedContext);
    }

    /**
     * 构建无知识库命中时的通用回答提示词。
     * <p>
     * 该分支明确告知模型不要伪造知识库来源；如果问题依赖企业私有资料，则说明资料缺失，
     * 否则可以使用本地模型的通用知识给出直接回答。这里不再注入数据库中的系统约束文档，
     * 避免“无相关资料”场景仍被知识库文档改写为带来源的回答。
     */
    private String buildGeneralAnswerPrompt(String question, List<ChatHistoryMessage> history) {
        return """
                你是企业知识库智能助手。当前没有检索到可用的知识库上下文。
                要求：
                1. 可以使用本地模型的通用能力回答用户问题，但不能编造任何知识库来源。
                2. 如果问题依赖企业内部文档、制度、数据或用户指定知识库内容，请说明当前知识库未提供相关资料，并给出可执行的通用建议。
                3. 回答要清晰、准确、简洁。

                历史对话：
                %s

                用户问题：
                %s

                请给出回答：
                """.formatted(formatHistory(history), question);
    }

    /**
     * 判断上下文列表是否包含实际资料。
     */
    private boolean hasItems(List<RagSearchItemVO> chunks) {
        return chunks != null && !chunks.isEmpty();
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
