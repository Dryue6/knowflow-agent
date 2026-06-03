package com.example.knowledgeagent.rag.service;

import com.example.knowledgeagent.config.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * 定义 QueryRewriteCacheService 组件，承载对应模块的业务职责。
 */
public class QueryRewriteCacheService {
    private static final String PROMPT_VERSION = "prompt-v4";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 查询 get 对应的数据或业务结果。
     */
    public Optional<QueryRewriteResult> get(String normalizedQuery, RagProperties.QueryRewrite config) {
        if (!cacheEnabled(config)) {
            return Optional.empty();
        }
        try {
            String payload = redisTemplate.opsForValue().get(cacheKey(normalizedQuery, config));
            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, QueryRewriteResult.class));
        } catch (Exception ex) {
            log.warn("Read query rewrite cache from Redis failed, continue without cache: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 处理 put 方法对应的业务逻辑。
     */
    public void put(String normalizedQuery, RagProperties.QueryRewrite config, QueryRewriteResult result) {
        if (!cacheEnabled(config) || result == null) {
            return;
        }
        try {
            String key = cacheKey(normalizedQuery, config);
            enforceMaxSize(config, key);
            String payload = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(
                    key,
                    payload,
                    Duration.ofMinutes(defaultPositive(config.cacheTtlMinutes(), 60))
            );
        } catch (Exception ex) {
            log.warn("Write query rewrite cache to Redis failed, ignore cache write: {}", ex.getMessage());
        }
    }

    /**
     * 处理 cacheEnabled 方法对应的业务逻辑。
     */
    private boolean cacheEnabled(RagProperties.QueryRewrite config) {
        return config != null && config.cacheEnabled();
    }

    /**
     * 处理 cacheKey 方法对应的业务逻辑。
     */
    private String cacheKey(String normalizedQuery, RagProperties.QueryRewrite config) {
        String prefix = defaultString(config.cacheKeyPrefix(), "knowflow:rag:query-rewrite:");
        return prefix + normalizedQuery + ":" + configFingerprint(config);
    }

    /**
     * 处理 enforceMaxSize 方法对应的业务逻辑。
     */
    private void enforceMaxSize(RagProperties.QueryRewrite config, String key) {
        int maxSize = defaultPositive(config.cacheMaxSize(), 512);
        if (maxSize <= 0) {
            return;
        }
        String prefix = defaultString(config.cacheKeyPrefix(), "knowflow:rag:query-rewrite:");
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys == null || keys.size() < maxSize || keys.contains(key)) {
            return;
        }
        keys.stream().findFirst().ifPresent(redisTemplate::delete);
    }

    /**
     * 处理 configFingerprint 方法对应的业务逻辑。
     */
    private String configFingerprint(RagProperties.QueryRewrite config) {
        return String.join(":",
                PROMPT_VERSION,
                defaultString(config.modelName(), "qwen2.5:7b"),
                String.valueOf(defaultPositive(config.maxQueryVariants(), 5)),
                String.valueOf(defaultPositive(config.maxTermsPerField(), 8)),
                String.valueOf(defaultPositive(config.numPredict(), 96)),
                String.valueOf(defaultPositive(config.numCtx(), 2048))
        );
    }

    /**
     * 处理 defaultString 对应的兜底、清洗或默认值逻辑。
     */
    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 处理 defaultPositive 对应的兜底、清洗或默认值逻辑。
     */
    private int defaultPositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
