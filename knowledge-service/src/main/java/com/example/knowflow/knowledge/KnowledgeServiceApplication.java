package com.example.knowflow.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * knowledge-service 微服务启动入口，只扫描当前服务需要的业务包和通用基础设施包。
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.example.knowflow.contract.client")
@ConfigurationPropertiesScan(basePackages = "com.example.knowledgeagent.config")
@MapperScan(basePackages = {"com.example.knowledgeagent.knowledge.mapper"})
@SpringBootApplication(scanBasePackages = {"com.example.knowledgeagent.knowledge", "com.example.knowledgeagent.common", "com.example.knowledgeagent.config", "com.example.knowflow.contract", "com.example.knowflow.knowledge"})
public class KnowledgeServiceApplication {

    /**
     * 启动 knowledge-service 服务。
     */
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeServiceApplication.class, args);
    }
}
