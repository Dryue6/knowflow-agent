package com.example.knowledgeagent.document.embedding;

import com.example.knowledgeagent.config.AiModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiEmbeddingServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ollamaEmbeddingKeepsReturned1024DimensionWhenConfigured1024Dimension() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startOllamaServer(200, embeddingResponse(1024), requestBody);
        OpenAiEmbeddingService service = service(ollamaBaseUrl(), "qwen3-embedding:0.6b", 1024, 3, null);

        List<Double> vector = service.embedText("Serial.cpp");

        assertThat(requestBody.get()).contains("\"model\":\"qwen3-embedding:0.6b\"");
        assertThat(vector).hasSize(1024);
        assertThat(vector.get(0)).isCloseTo(1.0 / Math.sqrt(1024), org.assertj.core.data.Offset.offset(0.000001));
        assertThat(vector.get(1023)).isCloseTo(1.0 / Math.sqrt(1024), org.assertj.core.data.Offset.offset(0.000001));
        assertThat(service.diagnosticMetadata())
                .containsEntry("embeddingProvider", "ollama")
                .containsEntry("embeddingModel", "qwen3-embedding:0.6b")
                .containsEntry("embeddingConfiguredDimension", 1024);
    }

    @Test
    void ollamaNonSuccessStatusFallsBackToDeterministicEmbedding() throws Exception {
        startOllamaServer(404, "{\"error\":\"model not found\"}", new AtomicReference<>());
        OpenAiEmbeddingService service = service(ollamaBaseUrl(), "qwen3-embedding:0.6b", 1024, 3, null);

        List<Double> vector = service.embedText("fallback text");

        assertThat(vector).hasSize(1024);
        assertThat(vector).anySatisfy(value -> assertThat(value).isNotZero());
    }

    @Test
    void ollamaTimeoutFallsBackToDeterministicEmbedding() throws Exception {
        startOllamaServerWithSlowEmbedding();
        OpenAiEmbeddingService service = service(ollamaBaseUrl(), "qwen3-embedding:0.6b", 1024, 1, null);

        List<Double> vector = service.embedText("timeout text");

        assertThat(vector).hasSize(1024);
        assertThat(vector).anySatisfy(value -> assertThat(value).isNotZero());
    }

    @Test
    void openAiCompatibleEmbeddingPathStillWorks() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startOpenAiCompatibleServer(requestBody);
        OpenAiEmbeddingService service = service("http://localhost:" + server.getAddress().getPort(),
                "external-embedding", 1024, 3, "test-key");

        List<Double> vector = service.embedText("中文检索");

        assertThat(requestBody.get()).contains("\"model\":\"external-embedding\"");
        assertThat(vector).hasSize(1024);
        assertThat(vector.get(0)).isCloseTo(1.0 / Math.sqrt(3), org.assertj.core.data.Offset.offset(0.000001));
        assertThat(vector.get(3)).isZero();
        assertThat(service.diagnosticMetadata()).containsEntry("embeddingProvider", "openai-compatible");
    }

    private OpenAiEmbeddingService service(String baseUrl, String modelName, int dimension, int timeoutSeconds, String apiKey) {
        AiModelProperties properties = new AiModelProperties(null,
                new AiModelProperties.Embedding(baseUrl, apiKey, modelName, dimension, timeoutSeconds));
        return new OpenAiEmbeddingService(properties, new ObjectMapper());
    }

    /**
     * 测试环境的 baseUrl 路径中包含 ollama，用于触发服务内的 Ollama 分支，同时避免占用宿主机 11434 端口。
     */
    private String ollamaBaseUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/ollama";
    }

    private void startOllamaServer(int embedStatus, String embedBody, AtomicReference<String> requestBody) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ollama/api/tags", exchange -> {
            byte[] response = "{\"models\":[{\"name\":\"qwen3-embedding:0.6b\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/ollama/api/embed", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = embedBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(embedStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    private void startOllamaServerWithSlowEmbedding() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ollama/api/tags", exchange -> {
            byte[] response = "{\"models\":[{\"name\":\"qwen3-embedding:0.6b\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/ollama/api/embed", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] response = embeddingResponse(8).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    private void startOpenAiCompatibleServer(AtomicReference<String> requestBody) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embeddings", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":[{\"embedding\":[1,1,1]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    private String embeddingResponse(int dimension) {
        StringBuilder builder = new StringBuilder("{\"embeddings\":[[");
        for (int i = 0; i < dimension; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('1');
        }
        builder.append("]]}");
        return builder.toString();
    }
}
