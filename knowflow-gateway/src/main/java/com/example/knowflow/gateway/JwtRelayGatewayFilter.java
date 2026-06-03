package com.example.knowflow.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 网关 JWT 透传过滤器。
 * <p>
 * 当前练习版不做强签名校验，只从 Bearer token 的 payload 中尝试解析用户字段并透传给下游服务。
 * 后续接入正式 JWT 密钥后，可在这里增加验签、过期时间校验和黑名单校验。
 */
@Component
public class JwtRelayGatewayFilter implements GlobalFilter, Ordered {

    /**
     * 解析请求头中的 Bearer token，并将用户信息透传为内部请求头。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        String payload = decodePayload(authorization.substring("Bearer ".length()));
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header("X-User-Token-Payload", payload)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 从 JWT 三段式 token 中解码 payload；临时 token 或格式异常时返回空字符串。
     */
    private String decodePayload(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return "";
        }
        try {
            return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    /**
     * 尽早执行用户信息透传，确保后续过滤器和下游路由都能读取。
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
