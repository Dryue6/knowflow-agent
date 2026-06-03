package com.example.knowledgeagent.rag.service.impl;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.config.RagProperties;
import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;
import com.example.knowledgeagent.rag.dto.RagAskRequest;
import com.example.knowledgeagent.rag.prompt.RagPromptBuilder;
import com.example.knowledgeagent.rag.service.ChatModelService;
import com.example.knowledgeagent.rag.service.QueryRewriteService;
import com.example.knowledgeagent.rag.service.RerankService;
import com.example.knowledgeagent.rag.vo.RagSearchItemVO;
import com.example.knowflow.contract.client.DocumentClient;
import com.example.knowflow.contract.dto.DocumentAdjacentContextCommand;
import com.example.knowflow.contract.dto.DocumentFixedContextCommand;
import com.example.knowflow.contract.dto.DocumentKeywordSearchCommand;
import com.example.knowflow.contract.dto.DocumentVectorSearchCommand;
import com.example.knowflow.contract.dto.RagSearchItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RagServiceImplTest {

    @Test
    void askAddsAdjacentChunksToPromptContext() {
        RagProperties properties = properties();
        CapturingPromptBuilder promptBuilder = new CapturingPromptBuilder();
        RagServiceImpl service = new RagServiceImpl(new FakeDocumentClient(), queryRewriteService(properties),
                passthroughRerank(), properties, promptBuilder, new StubChatModelService(),
                new LocalFallbackChatService(properties, new ObjectMapper()));

        var answer = service.ask(new RagAskRequest(1L, "Serial.cpp", null), List.of());

        assertThat(promptBuilder.retrievedContext)
                .extracting(RagSearchItemVO::chunkId)
                .containsExactly(101L, 102L, 103L);
        assertThat(answer.citations())
                .extracting(item -> item.chunkId())
                .containsExactly(101L, 102L, 103L);
    }

    private static QueryRewriteService queryRewriteService(RagProperties properties) {
        return new QueryRewriteService(properties, new ObjectMapper(), null);
    }

    private static RerankService passthroughRerank() {
        return (query, rewrite, candidates) -> candidates.stream()
                .filter(item -> item.chunkId().equals(102L))
                .toList();
    }

    private static RagProperties properties() {
        return new RagProperties(1800, 300, 5, 0.8, 6, 8, 12, 8, 30, 0.2, 0.6,
                new RagProperties.QueryRewrite(false, "ollama", "http://localhost:11434", "qwen2.5:7b",
                        0.0, 90, 5, 8, 96, 2048, 0, true, 60, 512,
                        "knowflow:rag:query-rewrite:", true, 12, "怎么,如何,为什么,规则,计算,占比,分配,要求,流程",
                        false, "成绩怎么计算", "conservative"),
                new RagProperties.Rerank(true, "local", null, null, null, null));
    }

    private static class CapturingPromptBuilder extends RagPromptBuilder {
        private List<RagSearchItemVO> retrievedContext = List.of();

        @Override
        public String build(String question,
                            List<ChatHistoryMessage> history,
                            List<RagSearchItemVO> systemConstraints,
                            List<RagSearchItemVO> pinnedContext,
                            List<RagSearchItemVO> retrievedContext) {
            this.retrievedContext = retrievedContext;
            return "prompt";
        }
    }

    private static class FakeDocumentClient implements DocumentClient {
        @Override
        public ApiResult<List<RagSearchItem>> vectorSearch(DocumentVectorSearchCommand request) {
            return ApiResult.ok(List.of());
        }

        @Override
        public ApiResult<List<RagSearchItem>> keywordSearch(DocumentKeywordSearchCommand request) {
            return ApiResult.ok(List.of(new RagSearchItem(94L, "demo.docx", 102L, 1,
                    "【图片OCR DOCX 第 1 张 / 正文第1段】Serial.cpp", 0.92)));
        }

        @Override
        public ApiResult<List<RagSearchItem>> fixedContext(DocumentFixedContextCommand request) {
            return ApiResult.ok(List.of());
        }

        @Override
        public ApiResult<List<RagSearchItem>> adjacentContext(DocumentAdjacentContextCommand request) {
            return ApiResult.ok(List.of(
                    new RagSearchItem(94L, "demo.docx", 101L, 0, "物联网环境监测系统", 0.6),
                    new RagSearchItem(94L, "demo.docx", 103L, 2, "SensorModule.cpp 负责解析传感器数据", 0.6)
            ));
        }
    }

    private static class StubChatModelService implements ChatModelService {
        @Override
        public String chat(String prompt) {
            return "answer";
        }

        @Override
        public void streamChat(String prompt, Consumer<String> tokenConsumer) {
            tokenConsumer.accept("answer");
        }
    }
}
