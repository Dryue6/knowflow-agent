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
/**
 * 定义 AuthServiceImpl 组件，承载对应模块的业务职责。
 */
public class AuthServiceImpl implements AuthService {
    private final UserAccountMapper userAccountMapper;

    /**
     * 注册用户账号。
     * <p>
     * 当前阶段只做最小账号体系：用户名唯一、密码哈希保存、默认状态 ACTIVE。
     * 后续接入 Spring Security 后可以替换密码算法和 token 生成逻辑。
     */
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

    /**
     * 校验用户名密码并返回登录结果。
     * <p>
     * token 目前是临时随机字符串，主要服务前端联调；后续应替换为 JWT 或服务端会话。
     */
    @Override
    public LoginVO login(LoginRequest request) {
        UserAccount user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, request.username()));
        if (user == null || !user.getPasswordHash().equals(hashPassword(request.password()))) {
            throw BusinessException.badRequest("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        return new LoginVO(token, AuthUserVO.from(user));
    }

    /**
     * 计算密码哈希。
     * <p>
     * 这里使用固定前缀作为轻量 salt，避免明文入库；生产环境建议切换 BCrypt/Argon2。
     */
    private String hashPassword(String password) {
        return HashUtils.sha256("knowflow:" + password);
    }
}
