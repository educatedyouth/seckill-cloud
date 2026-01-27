package com.example.seckill.common.config;

import com.example.seckill.common.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 1. 搬运 User ID (注意：必须和 UserInterceptor 里取的名字一致！)
                Long userId = UserContext.getUserId();
                if (userId != null) {
                    // ❌ 之前是 "user-id"，导致接收端匹配不上
                    // ✅ 改为 "X-User-Id"，与 UserInterceptor 保持一致
                    template.header("X-User-Id", String.valueOf(userId));
                    log.info("Feign 拦截器已透传 X-User-Id: {}", userId);
                }

                // 2. 搬运 Token (部分鉴权可能需要)
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    String token = request.getHeader("Authorization");
                    if (token != null) {
                        template.header("Authorization", token);
                    }
                }
            }
        };
    }
}