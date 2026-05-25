package com.example.knowledgeagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@MapperScan("com.example.knowledgeagent.**.mapper")
@ConfigurationPropertiesScan
@SpringBootApplication
/**
 * 定义 KnowledgeAgentApplication 组件，承载对应模块的业务职责。
 */
public class KnowledgeAgentApplication {

    /**
     * 应用启动入口。
     */
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAgentApplication.class, args);
    }
}
