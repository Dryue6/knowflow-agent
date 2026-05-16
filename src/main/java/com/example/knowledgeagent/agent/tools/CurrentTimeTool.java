package com.example.knowledgeagent.agent.tools;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CurrentTimeTool {
    /**
     * Agent 工具：返回服务器当前时间。
     */
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
