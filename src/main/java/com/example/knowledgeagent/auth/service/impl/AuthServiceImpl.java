package com.example.knowledgeagent.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowledgeagent.auth.dto.LoginRequest;
import com.example.knowledgeagent.auth.dto.RegisterRequest;
import com.example.knowledgeagent.auth.entity.UserAccount;
import com.example.knowledgeagent.auth.mapper.UserAccountMapper;
import com.example.knowledgeagent.auth.service.AuthService;
import com.example.knowledgeagent.auth.vo.AuthUserVO;
import com.example.knowledgeagent.auth.vo.LoginVO;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.common.util.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserAccountMapper userAccountMapper;

    @Override
    @Transactional
    public AuthUserVO register(RegisterRequest request) {
        Long exists = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, request.username()));
        if (exists > 0) {
            throw BusinessException.badRequest("用户名已存在");
        }
        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setPasswordHash(hashPassword(request.password()));
        user.setDisplayName(StringUtils.hasText(request.displayName()) ? request.displayName() : request.username());
        user.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);
        return AuthUserVO.from(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        UserAccount user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, request.username()));
        if (user == null || !user.getPasswordHash().equals(hashPassword(request.password()))) {
            throw BusinessException.badRequest("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        return new LoginVO(token, AuthUserVO.from(user));
    }

    private String hashPassword(String password) {
        return HashUtils.sha256("knowflow:" + password);
    }
}
