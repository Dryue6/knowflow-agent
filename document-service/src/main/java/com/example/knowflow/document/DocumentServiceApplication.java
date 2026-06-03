package com.example.knowflow.document;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * document-service 微服务启动入口，只扫描当前服务需要的业务包和通用基础设施包。
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.example.knowflow.contract.client")
// 启用 Spring 异步代理，确保上传完成后的索引任务不会阻塞 complete-upload 请求线程。
@EnableAsync
@ConfigurationPropertiesScan(basePackages = "com.example.knowledgeagent.config")
@MapperScan(basePackages = {"com.example.knowledgeagent.document.mapper", "com.example.knowledgeagent.job.mapper"})
@SpringBootApplication(scanBasePackages = {"com.example.knowledgeagent.document", "com.example.knowledgeagent.job", "com.example.knowledgeagent.storage", "com.example.knowledgeagent.common", "com.example.knowledgeagent.config", "com.example.knowflow.contract", "com.example.knowflow.document"})
public class DocumentServiceApplication {

    /**
     * 启动 document-service 服务。
     */
    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}
