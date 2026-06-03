package com.example.knowledgeagent.job.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 文档索引异步线程池配置。
 *
 * <p>索引任务会占用解析、embedding、向量写入等重资源，使用专用线程池可以限制并发，
 * 避免默认异步执行器无限扩张导致 Gateway、Ollama 或数据库被瞬时上传任务拖慢。</p>
 */
@Configuration
public class DocumentIndexAsyncConfig {
    @Bean("documentIndexTaskExecutor")
    public Executor documentIndexTaskExecutor(
            @Value("${document.index.executor.core-size:1}") int coreSize,
            @Value("${document.index.executor.max-size:1}") int maxSize,
            @Value("${document.index.executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int safeCoreSize = Math.max(1, coreSize);
        executor.setCorePoolSize(safeCoreSize);
        executor.setMaxPoolSize(Math.max(safeCoreSize, maxSize));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setThreadNamePrefix("document-index-");
        executor.initialize();
        return executor;
    }
}
