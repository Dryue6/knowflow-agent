package com.example.knowflow.rag.internal;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;
import com.example.knowledgeagent.rag.dto.RagAskRequest;
import com.example.knowledgeagent.rag.dto.RagSearchRequest;
import com.example.knowledgeagent.rag.service.RagService;
import com.example.knowledgeagent.rag.vo.CitationVO;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import com.example.knowledgeagent.rag.vo.RagSearchItemVO;
import com.example.knowledgeagent.rag.vo.RagSearchResponseVO;
import com.example.knowflow.contract.dto.ChatHistoryItem;
import com.example.knowflow.contract.dto.CitationItem;
import com.example.knowflow.contract.dto.RagAnswerResult;
import com.example.knowflow.contract.dto.RagAskCommand;
import com.example.knowflow.contract.dto.RagSearchCommand;
import com.example.knowflow.contract.dto.RagSearchItem;
import com.example.knowflow.contract.dto.RagSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RAG 服务内部接口，供 chat-service 和 agent-service 通过 OpenFeign 调用。
 */
@RestController
@RequestMapping("/internal/rag")
public class InternalRagController {
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    /**
     * 注入 RAG 领域服务。
     */
    public InternalRagController(RagService ragService, ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    /**
     * 内部检索接口，使用 contract DTO 作为服务边界。
     */
    @PostMapping("/search")
    public ApiResult<RagSearchResult> search(@RequestBody RagSearchCommand request) {
        RagSearchResponseVO response = ragService.retrieve(new RagSearchRequest(request.knowledgeBaseId(), request.query(), request.topK(), request.minScore()));
        return ApiResult.ok(new RagSearchResult(response.query(), response.chunks().stream().map(this::toContract).toList()));
    }

    /**
     * 内部非流式问答接口，调用方可携带聊天历史。
     */
    @PostMapping("/ask")
    public ApiResult<RagAnswerResult> ask(@RequestBody RagAskCommand request) {
        List<ChatHistoryMessage> history = request.history() == null ? List.of() : request.history().stream().map(this::toDomain).toList();
        RagAnswerVO answer = ragService.ask(new RagAskRequest(request.knowledgeBaseId(), request.question(), request.sessionId()), history);
        return ApiResult.ok(new RagAnswerResult(answer.answer(), answer.citations().stream().map(this::toContract).toList()));
    }

    /**
     * 内部流式问答接口，供 chat-service 逐段转发模型输出到前端 SSE。
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody RagAskCommand request) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                List<ChatHistoryMessage> history = request.history() == null ? List.of() : request.history().stream().map(this::toDomain).toList();
                var result = ragService.askStream(
                        new RagAskRequest(request.knowledgeBaseId(), request.question(), request.sessionId()),
                        history,
                        token -> send(emitter, "message", token));
                send(emitter, "citations", result.citations().stream().map(this::toContract).toList());
                emitter.complete();
            } catch (Exception ex) {
                send(emitter, "error", Map.of("code", "470", "message", ex.getMessage() == null ? "RAG 流式问答服务异常" : ex.getMessage()));
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * 转换聊天历史 DTO。
     */
    private ChatHistoryMessage toDomain(ChatHistoryItem item) {
        return new ChatHistoryMessage(item.role(), item.content());
    }

    /**
     * 转换检索命中 DTO。
     */
    private RagSearchItem toContract(RagSearchItemVO item) {
        return new RagSearchItem(item.documentId(), item.documentName(), item.chunkId(), item.chunkIndex(), item.content(), item.score());
    }

    /**
     * 转换引用来源 DTO。
     *
     * <p>score 表示当前引用切片与用户问题的最终相似度/重排分数，必须透传给 chat-service，
     * 供前端在每个引用文档旁展示相关性。</p>
     */
    private CitationItem toContract(CitationVO item) {
        return new CitationItem(item.documentId(), item.documentName(), item.chunkId(), item.chunkIndex(), item.contentPreview(),
                item.score(), item.pageNumber(), item.sectionTitle(), item.paragraphIndex());
    }

    /**
     * 统一发送内部 SSE 事件；对象类数据提前序列化，便于 chat-service 按文本流稳定解析。
     */
    private void send(SseEmitter emitter, String name, Object data) {
        try {
            Object payload = data instanceof String ? data : objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(name).data(payload));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
