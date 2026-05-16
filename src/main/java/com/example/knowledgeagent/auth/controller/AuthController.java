package com.example.knowledgeagent.auth.controller;

import com.example.knowledgeagent.auth.dto.LoginRequest;
import com.example.knowledgeagent.auth.dto.RegisterRequest;
import com.example.knowledgeagent.auth.service.AuthService;
import com.example.knowledgeagent.auth.vo.AuthUserVO;
import com.example.knowledgeagent.auth.vo.LoginVO;
import com.example.knowledgeagent.common.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ApiResult<AuthUserVO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResult.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResult<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok(authService.login(request));
    }
}
