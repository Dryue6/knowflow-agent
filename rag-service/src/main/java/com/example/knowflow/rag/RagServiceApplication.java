package com.example.knowflow.rag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * rag-service 微服务启动入口，只扫描当前服务需要的业务包和通用基础设施包。
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.example.knowflow.contract.client")
@ConfigurationPropertiesScan(basePackages = "com.example.knowledgeagent.config")
@SpringBootApplication(scanBasePackages = {"com.example.knowledgeagent.rag", "com.example.knowledgeagent.common", "com.example.knowledgeagent.config", "com.example.knowflow.contract", "com.example.knowflow.rag"})
public class RagServiceApplication {

    /**
     * 启动 rag-service 服务。
     */
    public static void main(String[] args) {
        SpringApplication.run(RagServiceApplication.class, args);
    }
}
