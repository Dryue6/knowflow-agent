package com.example.knowflow.chat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * chat-service 微服务启动入口，只扫描当前服务需要的业务包和通用基础设施包。
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.example.knowflow.contract.client")
@ConfigurationPropertiesScan(basePackages = "com.example.knowledgeagent.config")
@MapperScan(basePackages = {"com.example.knowledgeagent.chat.mapper"})
@SpringBootApplication(scanBasePackages = {"com.example.knowledgeagent.chat", "com.example.knowledgeagent.common", "com.example.knowledgeagent.config", "com.example.knowflow.contract"})
public class ChatServiceApplication {

    /**
     * 启动 chat-service 服务。
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
