package com.example.knowledgeagent.auth.vo;

import com.example.knowledgeagent.auth.entity.UserAccount;

/**
 * 定义 AuthUserVO 数据结构，用于在层间传递结构化数据。
 */
public record AuthUserVO(Long id, String username, String displayName, String status) {
    /**
     * 将用户实体转换为前端安全视图对象，不暴露密码哈希。
     */
    public static AuthUserVO from(UserAccount user) {
        return new AuthUserVO(user.getId(), user.getUsername(), user.getDisplayName(), user.getStatus());
    }
}
