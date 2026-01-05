package com.example.seckill.common.config;

import com.example.seckill.common.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 远程调用增强
 * 作用：在 Feign 发起请求前，自动把当前线程的 userId 塞入请求头
 */
@Slf4j
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 1. 获取当前微服务的上下文用户 ID
                Long userId = UserContext.getUserId();
                if (userId != null) {
                    // 搬运 user-id
                    template.header("user-id", String.valueOf(userId));
                    log.info("Feign 拦截器已透传 user-id: {}", userId);
                }

                // 2. (可选) 如果你还需要透传 Token，可以在这里从 RequestContextHolder 获取
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