package com.example.seckill.common.interceptor;

import com.example.seckill.common.context.UserContext;
import lombok.extern.slf4j.Slf4j; // 确保 pom 有 lombok
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
public class UserInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从 Header 中获取网关透传过来的 userId
        String userIdStr = request.getHeader("X-User-Id");

        if (userIdStr != null) {
            // 存入 ThreadLocal
            UserContext.setUserId(Long.parseLong(userIdStr));
            log.debug("当前线程: {}, 绑定用户ID: {}", Thread.currentThread().getId(), userIdStr);
        }
        // 如果是部分不需要登录的接口，这里可能没有 userId，也放行，由业务层自己判断 check
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束，必须清理 ThreadLocal
        UserContext.remove();
    }
}