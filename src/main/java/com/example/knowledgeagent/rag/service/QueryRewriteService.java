package com.example.knowledgeagent.rag.service;

import com.example.knowledgeagent.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * 定义 QueryRewriteService 组件，承载对应模块的业务职责。
 */
public class QueryRewriteService {
    private static final List<String> TEXT_FIELD_NAMES = List.of("query", "variant", "text", "term", "keyword", "phrase", "value");

    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final QueryRewriteCacheService cacheService;
    private volatile ChatLanguageModel ollamaModel;

    /**
     * 对原始查询进行规范化、缓存命中、快路径判断和模型重写。
     */
    public QueryRewriteResult rewrite(String query) {
        String normalizedQuery = compact(query);
        if (!StringUtils.hasText(normalizedQuery)) {
            return new QueryRewriteResult("", List.of(), List.of(), List.of(), List.of());
        }
        RagProperties.QueryRewrite config = ragProperties.queryRewrite();
        if (config == null || !config.enabled()) {
            return conservativeFallback(query);
        }

        QueryRewriteResult cached = cacheService.get(normalizedQuery, config).orElse(null);
        if (cached != null) {
            return cached;
        }

        if (shouldUseFastPath(normalizedQuery, config)) {
            // 短且意图明确的问题不必调用本地大模型，直接用轻量分词结果降低延迟。
            QueryRewriteResult result = conservativeFallback(query);
            cacheService.put(normalizedQuery, config, result);
            return result;
        }

        if (!"ollama".equalsIgnoreCase(nullToBlank(config.provider()))) {
            // 目前只实现 Ollama provider，其他配置值全部降级，避免错误配置阻断检索主链路。
            log.warn("Unsupported query rewrite provider '{}', use conservative fallback", config.provider());
            QueryRewriteResult result = conservativeFallback(query);
            cacheService.put(normalizedQuery, config, result);
            return result;
        }

        try {
            String response = model(config).generate(buildPrompt(query, config));
            QueryRewriteResult result = parseModelResponse(query, response, config);
            if (result.allTerms().isEmpty() && result.queryVariants().size() <= 1) {
                // 模型如果只回传原问题且没有扩展术语，说明重写没有产生增益，转为本地兜底。
                result = conservativeFallback(query);
            }
            cacheService.put(normalizedQuery, config, result);
            return result;
        } catch (RuntimeException ex) {
            log.warn("Ollama query rewrite failed, use conservative fallback: {}", ex.getMessage());
            QueryRewriteResult result = conservativeFallback(query);
            cacheService.put(normalizedQuery, config, result);
            return result;
        } catch (Exception ex) {
            log.warn("Invalid Ollama query rewrite response, use conservative fallback: {}", ex.getMessage());
            QueryRewriteResult result = conservativeFallback(query);
            cacheService.put(normalizedQuery, config, result);
            return result;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    /**
     * 应用启动后异步预热本地 Ollama 模型，降低首次复杂查询的冷启动等待。
     */
    public void warmup() {
        RagProperties.QueryRewrite config = ragProperties.queryRewrite();
        if (config == null || !config.enabled() || !config.warmupEnabled()) {
            return;
        }
        String warmupQuery = defaultString(config.warmupQuery(), "成绩怎么计算");
        try {
            rewrite(warmupQuery);
            log.info("Ollama query rewrite warmup finished");
        } catch (RuntimeException ex) {
            log.warn("Ollama query rewrite warmup failed: {}", ex.getMessage());
        }
    }

    /**
     * 懒加载并复用 Ollama ChatLanguageModel，避免每次重写都重复创建 HTTP 客户端和模型配置。
     */
    private ChatLanguageModel model(RagProperties.QueryRewrite config) {
        ChatLanguageModel current = ollamaModel;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (ollamaModel == null) {
                ollamaModel = OllamaChatModel.builder()
                        .baseUrl(defaultString(config.baseUrl(), "http://localhost:11434"))
                        .modelName(defaultString(config.modelName(), "qwen2.5:7b"))
                        .temperature(config.temperature())
                        .timeout(Duration.ofSeconds(defaultPositive(config.timeoutSeconds(), 20)))
                        .numPredict(defaultPositive(config.numPredict(), 160))
                        .numCtx(defaultPositive(config.numCtx(), 2048))
                        .maxRetries(Math.max(0, config.maxRetries()))
                        .format("json")
                        .build();
            }
            return ollamaModel;
        }
    }

    /**
     * 构建查询重写提示词，要求模型只返回稳定 JSON，便于后端做强约束解析。
     */
    private String buildPrompt(String query, RagProperties.QueryRewrite config) {
        int maxVariants = defaultPositive(config.maxQueryVariants(), 8);
        int maxTerms = defaultPositive(config.maxTermsPerField(), 12);
        return """
                你是中文知识库RAG查询重写器。只返回JSON，不要解释。
                字段：queryVariants最多%d条，coreTerms/phraseTerms/expandedTerms各最多%d个。
                四个字段都必须是字符串数组，数组元素不能是对象。
                不要编造问题没有指向的主题。
                格式：{"queryVariants":[],"coreTerms":[],"phraseTerms":[],"expandedTerms":[]}
                问题：%s
                """.formatted(maxVariants, maxTerms, query);
    }

    /**
     * 解析模型返回的 JSON，并转换为查询重写结果。
     */
    QueryRewriteResult parseModelResponse(String query, String response, RagProperties.QueryRewrite config) throws Exception {
        RewritePayload payload = objectMapper.readValue(extractJson(response), RewritePayload.class);
        return toResult(query, payload, config);
    }

    /**
     * 把模型 payload 转成业务可用的查询变体和术语集合。
     */
    private QueryRewriteResult toResult(String query, RewritePayload payload, RagProperties.QueryRewrite config) {
        int maxVariants = defaultPositive(config.maxQueryVariants(), 8);
        int maxTerms = defaultPositive(config.maxTermsPerField(), 12);
        List<String> coreTerms = sanitize(payload.coreTerms, maxTerms);
        List<String> phraseTerms = sanitize(payload.phraseTerms, maxTerms);
        List<String> expandedTerms = sanitize(payload.expandedTerms, maxTerms);

        LinkedHashSet<String> queryVariants = new LinkedHashSet<>();
        // 原始问题必须排在第一位，保证模型改写异常时仍会覆盖用户真实表达。
        queryVariants.add(query);
        queryVariants.addAll(sanitize(payload.queryVariants, maxVariants));
        queryVariants.addAll(coreTerms);
        queryVariants.addAll(phraseTerms);
        queryVariants.addAll(expandedTerms);

        return new QueryRewriteResult(
                query,
                queryVariants.stream().filter(StringUtils::hasText).limit(maxVariants).toList(),
                coreTerms,
                phraseTerms,
                expandedTerms
        );
    }

    /**
     * 在模型不可用或输出无效时生成保守兜底查询结果。
     */
    private QueryRewriteResult conservativeFallback(String query) {
        List<String> coreTerms = fallbackTerms(query);
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(query);
        variants.addAll(coreTerms);
        return new QueryRewriteResult(query, variants.stream().toList(), coreTerms, List.of(), List.of());
    }

    /**
     * 判断是否走本地快路径，避免简单短查询额外消耗 Ollama 推理时间。
     */
    private boolean shouldUseFastPath(String normalizedQuery, RagProperties.QueryRewrite config) {
        if (!config.fastPathEnabled()) {
            return false;
        }
        if (normalizedQuery.length() > defaultPositive(config.fastPathMaxLength(), 12)) {
            return false;
        }
        return complexIntentKeywords(config).stream().noneMatch(normalizedQuery::contains);
    }

    /**
     * 解析复杂意图关键词配置，用于决定短查询是否仍需要模型重写。
     */
    private List<String> complexIntentKeywords(RagProperties.QueryRewrite config) {
        String keywords = defaultString(config.complexIntentKeywords(), "怎么,如何,为什么,规则,计算,占比,分配,要求,流程");
        return Arrays.stream(keywords.split("[,，]"))
                .map(this::compact)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * 处理 fallbackTerms 对应的兜底、清洗或默认值逻辑。
     */
    private List<String> fallbackTerms(String value) {
        String normalized = compact(value);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String word : normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+")) {
            if (word.length() >= 2) {
                terms.add(word);
            }
        }
        for (String segment : normalized.split("[^\\p{IsHan}]+")) {
            if (segment.length() >= 2) {
                terms.add(segment);
                int maxGram = Math.min(4, segment.length());
                // 中文没有天然空格分词，这里生成 2 到 4 字的 n-gram，给关键词召回提供保底命中项。
                for (int size = 2; size <= maxGram; size++) {
                    for (int i = 0; i <= segment.length() - size; i++) {
                        terms.add(segment.substring(i, i + size));
                    }
                }
            }
        }
        return terms.stream().limit(maxTermsForFallback()).toList();
    }

    /**
     * 读取兜底分词最大数量，配置缺失时使用保守默认值。
     */
    private int maxTermsForFallback() {
        RagProperties.QueryRewrite config = ragProperties.queryRewrite();
        return config == null ? 12 : defaultPositive(config.maxTermsPerField(), 12);
    }

    /**
     * 处理 sanitize 对应的兜底、清洗或默认值逻辑。
     */
    private List<String> sanitize(JsonNode values, int limit) {
        if (values == null || values.isNull() || values.isMissingNode()) {
            return List.of();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values.isArray()) {
            // 兼容模型偶发返回对象数组的情况，逐项提取可识别文本字段。
            values.forEach(node -> result.add(extractText(node)));
        } else {
            result.add(extractText(values));
        }

        return result.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(limit)
                .toList();
    }

    /**
     * 转换或构建 extractText 所需的数据结构。
     */
    private String extractText(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        String scalarText = scalarText(value);
        if (StringUtils.hasText(scalarText)) {
            return clean(scalarText);
        }
        if (!value.isObject()) {
            return "";
        }
        for (String fieldName : TEXT_FIELD_NAMES) {
            scalarText = scalarText(value.get(fieldName));
            if (StringUtils.hasText(scalarText)) {
                return clean(scalarText);
            }
        }
        return "";
    }

    /**
     * 只从标量 JSON 节点提取文本，复杂对象交给上层按候选字段处理。
     */
    private String scalarText(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        return value.isTextual() || value.isNumber() || value.isBoolean() ? value.asText() : "";
    }

    /**
     * 转换或构建 extractJson 所需的数据结构。
     */
    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            throw new IllegalArgumentException("empty response");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("response is not a JSON object");
        }
        return response.substring(start, end + 1);
    }

    /**
     * 处理 compact 对应的兜底、清洗或默认值逻辑。
     */
    private String compact(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    /**
     * 处理 clean 对应的兜底、清洗或默认值逻辑。
     */
    private String clean(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /**
     * 处理 defaultString 对应的兜底、清洗或默认值逻辑。
     */
    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 将空字符串配置统一归一为空白，便于后续忽略大小写比较。
     */
    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    /**
     * 处理 defaultPositive 对应的兜底、清洗或默认值逻辑。
     */
    private int defaultPositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static class RewritePayload {
        public JsonNode queryVariants;
        public JsonNode coreTerms;
        public JsonNode phraseTerms;
        public JsonNode expandedTerms;
    }
}
