package com.example.knowledgeagent.rag.service;

import com.example.knowledgeagent.config.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRewriteServiceTest {

    private final QueryRewriteService service = new QueryRewriteService(
            new RagProperties(800, 120, 5, 0.8, 6, 8, 12, 8, 30, 0.2, 0.6, config(), rerank()),
            new ObjectMapper(),
            null
    );

    @Test
    void parsesStringArrayPayload() throws Exception {
        QueryRewriteResult result = service.parseModelResponse("成绩怎么计算", """
                {
                  "queryVariants": ["成绩计算", "考核占比"],
                  "coreTerms": ["成绩", "计算"],
                  "phraseTerms": ["成绩计算"],
                  "expandedTerms": ["评分", "分值"]
                }
                """, config());

        assertThat(result.queryVariants()).containsExactly("成绩怎么计算", "成绩计算", "考核占比", "成绩", "计算");
        assertThat(result.coreTerms()).containsExactly("成绩", "计算");
        assertThat(result.phraseTerms()).containsExactly("成绩计算");
        assertThat(result.expandedTerms()).containsExactly("评分", "分值");
    }

    @Test
    void extractsTextFromObjectArrayPayload() throws Exception {
        QueryRewriteResult result = service.parseModelResponse("成绩怎么计算", """
                {
                  "queryVariants": [{"query": "成绩如何计算"}, {"variant": "考核成绩算法"}],
                  "coreTerms": [{"term": "成绩"}, {"keyword": "计算"}],
                  "phraseTerms": [{"phrase": "成绩计算"}],
                  "expandedTerms": [{"text": "评分"}, {"value": "占比"}]
                }
                """, config());

        assertThat(result.queryVariants()).containsExactly("成绩怎么计算", "成绩如何计算", "考核成绩算法", "成绩", "计算");
        assertThat(result.coreTerms()).containsExactly("成绩", "计算");
        assertThat(result.phraseTerms()).containsExactly("成绩计算");
        assertThat(result.expandedTerms()).containsExactly("评分", "占比");
    }

    @Test
    void ignoresUnrecognizedObjectElementsWithoutDiscardingPayload() throws Exception {
        QueryRewriteResult result = service.parseModelResponse("成绩怎么计算", """
                {
                  "queryVariants": [{"unknown": "不要提取"}, "成绩计算"],
                  "coreTerms": [{"unknown": "不要提取"}, {"term": "成绩"}],
                  "phraseTerms": [],
                  "expandedTerms": [{"value": "分值"}]
                }
                """, config());

        assertThat(result.queryVariants()).containsExactly("成绩怎么计算", "成绩计算", "成绩", "分值");
        assertThat(result.coreTerms()).containsExactly("成绩");
        assertThat(result.expandedTerms()).containsExactly("分值");
    }

    @Test
    void treatsSingleStringFieldAsOneElement() throws Exception {
        QueryRewriteResult result = service.parseModelResponse("成绩怎么计算", """
                {
                  "queryVariants": "成绩计算",
                  "coreTerms": "成绩",
                  "phraseTerms": "成绩计算",
                  "expandedTerms": "考核"
                }
                """, config());

        assertThat(result.queryVariants()).containsExactly("成绩怎么计算", "成绩计算", "成绩", "考核");
        assertThat(result.coreTerms()).containsExactly("成绩");
        assertThat(result.phraseTerms()).containsExactly("成绩计算");
        assertThat(result.expandedTerms()).containsExactly("考核");
    }

    private static RagProperties.QueryRewrite config() {
        return new RagProperties.QueryRewrite(
                true,
                "ollama",
                "http://localhost:11434",
                "qwen2.5:7b",
                0.0,
                90,
                5,
                8,
                96,
                2048,
                0,
                true,
                60,
                512,
                "knowflow:rag:query-rewrite:",
                true,
                12,
                "怎么,如何,为什么,规则,计算,占比,分配,要求,流程",
                true,
                "成绩怎么计算",
                "conservative"
        );
    }

    private static RagProperties.Rerank rerank() {
        return new RagProperties.Rerank(true, "local", null, null, null, null);
    }
}
