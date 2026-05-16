package com.example.knowledgeagent.agent.tools;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CurrentTimeTool {
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
