package com.example.knowledgeagent.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * 定义 JacksonConfig 组件，承载对应模块的业务职责。
 */
public class JacksonConfig {

    /**
     * 配置 Jackson 日期输出格式，避免 LocalDateTime 被序列化成时间戳数组。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
