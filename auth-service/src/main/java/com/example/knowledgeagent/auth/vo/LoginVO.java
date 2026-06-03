package com.example.knowledgeagent.auth.vo;

/**
 * 定义 LoginVO 数据结构，用于在层间传递结构化数据。
 */
public record LoginVO(String token, AuthUserVO user) {
}
