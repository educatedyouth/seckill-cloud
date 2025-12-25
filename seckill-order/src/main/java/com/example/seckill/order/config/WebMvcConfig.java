package com.example.seckill.order.config;

import com.example.seckill.common.interceptor.UserInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册我们刚才写的拦截器，拦截所有路径
        registry.addInterceptor(new UserInterceptor()).addPathPatterns("/**");
    }
}