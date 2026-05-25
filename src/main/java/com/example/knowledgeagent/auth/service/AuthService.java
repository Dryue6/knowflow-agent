package com.example.knowledgeagent.auth.service;

import com.example.knowledgeagent.auth.dto.LoginRequest;
import com.example.knowledgeagent.auth.dto.RegisterRequest;
import com.example.knowledgeagent.auth.vo.AuthUserVO;
import com.example.knowledgeagent.auth.vo.LoginVO;

/**
 * 定义 AuthService 接口，约定该模块对外提供的能力。
 */
public interface AuthService {
    /**
     * 注册用户账号。
     */
    AuthUserVO register(RegisterRequest request);

    /**
     * 校验用户名密码并返回登录结果。
     */
    LoginVO login(LoginRequest request);
}
