package com.example.seckill.gateway.filter;

import com.example.seckill.common.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    // 白名单路径 (不需要 Token 就能访问)
    private static final List<String> WHITE_LIST = List.of(
            "/auth/login",
            "/auth/register",
            "/doc.html" // 如果有 Swagger
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 🔥 1. 第一步：必须先判断白名单！如果是注册或登录，直接放行，不要碰 Token
        if (path.contains("/auth/login") || path.contains("/auth/register")) {
            return chain.filter(exchange); // 直接放行
        }

        // 2. 第二步：白名单之外的接口，再去拿 Token
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        // 3. 第三步：判空（防止乱发请求导致的 500）
        if (token == null || token.isEmpty()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        ServerHttpRequest request = exchange.getRequest();

        // 3. 校验 Token
        if (!jwtUtil.validateToken(token)) {
            log.warn("拦截非法请求，路径: {}", path);
            return makeResponse(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "未登录或 Token 失效");
        }

        // 4. 解析 Token 获取 UserId
        Long userId = jwtUtil.getUserId(token);
        log.info("Token 校验通过，用户ID: {}", userId);

        // 5. 【关键】将 UserId 放入 Request Header 传给下游
        // 这里的 mutate() 方法会创建一个新的 Request Builder
        ServerHttpRequest newRequest = request.mutate()
                .header("X-User-Id", String.valueOf(userId))
                .build();

        // 放行，并使用新的 Request
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    // 辅助方法：返回 JSON 错误提示
    private Mono<Void> makeResponse(ServerHttpResponse response, HttpStatus status, String msg) {
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        String json = "{\"code\": " + status.value() + ", \"msg\": \"" + msg + "\", \"data\": null}";
        DataBuffer dataBuffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(dataBuffer));
    }

    @Override
    public int getOrder() {
        // 优先级，数字越小越靠前
        return -1;
    }
}