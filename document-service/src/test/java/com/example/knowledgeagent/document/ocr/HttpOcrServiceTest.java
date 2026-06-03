package com.example.knowledgeagent.document.ocr;

import com.example.knowledgeagent.config.OcrProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpOcrServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsSuccessfulTextWhenConfidencePassesThreshold() throws Exception {
        startServer(200, "{\"success\":true,\"text\":\"识别文本\",\"confidence\":0.91}");
        HttpOcrService service = service(0.5);

        OcrResult result = service.recognize("image".getBytes(StandardCharsets.UTF_8), "a.png");

        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("识别文本");
        assertThat(result.confidence()).isEqualTo(0.91);
    }

    @Test
    void filtersLowConfidenceText() throws Exception {
        startServer(200, "{\"success\":true,\"text\":\"噪声\",\"confidence\":0.2}");
        HttpOcrService service = service(0.8);

        OcrResult result = service.recognize("image".getBytes(StandardCharsets.UTF_8), "a.png");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("low confidence");
    }

    @Test
    void degradesWhenServerReturnsErrorStatus() throws Exception {
        startServer(500, "broken");
        HttpOcrService service = service(0.5);

        OcrResult result = service.recognize("image".getBytes(StandardCharsets.UTF_8), "a.png");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("HTTP 500");
    }

    @Test
    void sendsSidecarRequestWithoutHttp2UpgradeHeader() throws Exception {
        AtomicReference<String> upgradeHeader = new AtomicReference<>();
        AtomicReference<String> contentTypeHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ocr", exchange -> {
            // OCR sidecar 基于 Uvicorn/FastAPI，不能处理 JDK HttpClient 的 h2c 升级探测。
            upgradeHeader.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            contentTypeHeader.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] response = "{\"success\":true,\"text\":\"识别文本\",\"confidence\":0.91}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        HttpOcrService service = service(0.5);

        OcrResult result = service.recognize("image".getBytes(StandardCharsets.UTF_8), "a.png");

        assertThat(result.success()).isTrue();
        assertThat(upgradeHeader.get()).isNull();
        assertThat(contentTypeHeader.get()).startsWith("multipart/form-data; boundary=");
    }

    @Test
    void degradesWhenServerTimesOut() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ocr", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] response = "{\"success\":true,\"text\":\"late\",\"confidence\":0.9}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        HttpOcrService service = new HttpOcrService(new OcrProperties(true, "http://localhost:" + server.getAddress().getPort(),
                1, 0.5, 20, 120), new ObjectMapper());

        OcrResult result = service.recognize("image".getBytes(StandardCharsets.UTF_8), "a.png");

        assertThat(result.success()).isFalse();
    }

    private HttpOcrService service(double minConfidence) {
        return new HttpOcrService(new OcrProperties(true, "http://localhost:" + server.getAddress().getPort(),
                3, minConfidence, 20, 120), new ObjectMapper());
    }

    private void startServer(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ocr", exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }
}
