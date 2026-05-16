package com.example.knowledgeagent.auth.service;

import com.example.knowledgeagent.auth.dto.LoginRequest;
import com.example.knowledgeagent.auth.dto.RegisterRequest;
import com.example.knowledgeagent.auth.vo.AuthUserVO;
import com.example.knowledgeagent.auth.vo.LoginVO;

public interface AuthService {
    AuthUserVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);
}
