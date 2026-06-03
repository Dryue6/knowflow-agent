package com.example.knowflow.gateway;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Gateway Sentinel 默认规则配置。
 * <p>
 * Nacos 规则加载前先提供一组本地保底规则，避免练习环境空配置时没有任何入口保护。
 */
@Configuration
public class GatewaySentinelConfig {

    /**
     * 注册网关 API 分组和默认限流规则。
     */
    @PostConstruct
    public void initGatewayRules() {
        ApiDefinition ragApi = new ApiDefinition("rag-api")
                .setPredicateItems(Set.of(new ApiPathPredicateItem().setPattern("/api/rag/**")
                        .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)));
        ApiDefinition chatApi = new ApiDefinition("chat-api")
                .setPredicateItems(Set.of(new ApiPathPredicateItem().setPattern("/api/chat/**")
                        .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)));
        GatewayApiDefinitionManager.loadApiDefinitions(Set.of(ragApi, chatApi));

        GatewayFlowRule ragRule = new GatewayFlowRule("rag-api").setCount(30).setIntervalSec(1);
        GatewayFlowRule chatRule = new GatewayFlowRule("chat-api").setCount(50).setIntervalSec(1);
        GatewayRuleManager.loadRules(Set.of(ragRule, chatRule));
    }
}
