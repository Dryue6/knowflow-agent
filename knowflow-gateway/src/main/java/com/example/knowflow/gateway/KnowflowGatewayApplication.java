package com.example.knowflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Knowflow 网关启动入口，负责统一接收前端请求并转发到各业务微服务。
 */
@EnableDiscoveryClient
@SpringBootApplication
public class KnowflowGatewayApplication {

    /**
     * 启动 Spring Cloud Gateway。
     */
    public static void main(String[] args) {
        SpringApplication.run(KnowflowGatewayApplication.class, args);
    }
}
