package com.example.knowflow.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * agent-service 微服务启动入口，只扫描当前服务需要的业务包和通用基础设施包。
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.example.knowflow.contract.client")
@ConfigurationPropertiesScan(basePackages = "com.example.knowledgeagent.config")
@SpringBootApplication(scanBasePackages = {"com.example.knowledgeagent.agent", "com.example.knowledgeagent.common", "com.example.knowledgeagent.config", "com.example.knowflow.contract"})
public class AgentServiceApplication {

    /**
     * 启动 agent-service 服务。
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentServiceApplication.class, args);
    }
}
