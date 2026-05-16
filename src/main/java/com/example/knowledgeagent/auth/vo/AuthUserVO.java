package com.example.knowledgeagent.auth.vo;

import com.example.knowledgeagent.auth.entity.UserAccount;

public record AuthUserVO(Long id, String username, String displayName, String status) {
    public static AuthUserVO from(UserAccount user) {
        return new AuthUserVO(user.getId(), user.getUsername(), user.getDisplayName(), user.getStatus());
    }
}
