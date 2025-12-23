package com.example.seckill.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class GatewaySentinelConfig {

    @PostConstruct
    public void initBlockHandlers() {
        // 自定义限流后的返回逻辑
        BlockRequestHandler blockRequestHandler = new BlockRequestHandler() {
            @Override
            public Mono<ServerResponse> handleRequest(ServerWebExchange serverWebExchange, Throwable throwable) {

                // 封装返回结果，必须和 seckill-common 里的 Result 结构一致
                Map<String, Object> map = new HashMap<>();
                map.put("code", 429); // 429 Too Many Requests
                map.put("message", "服务器繁忙，请稍后再试 (限流)");
                map.put("data", null);

                return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(map));
            }
        };

        // 注册给 Sentinel
        GatewayCallbackManager.setBlockHandler(blockRequestHandler);
    }
}