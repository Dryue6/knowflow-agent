package com.example.knowledgeagent.agent.tools;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
/**
 * 定义 CurrentTimeTool 组件，承载对应模块的业务职责。
 */
public class CurrentTimeTool {
    /**
     * Agent 工具：返回服务器当前时间。
     */
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
