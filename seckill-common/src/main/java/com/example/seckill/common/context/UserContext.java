package com.example.seckill.common.context;

public class UserContext {
    // ThreadLocal 保证线程安全，每个请求线程独享自己的 userId
    private static final ThreadLocal<Long> USER_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_HOLDER.get();
    }

    // 务必记得清理，防止内存泄漏 (尤其在线程池环境下)
    public static void remove() {
        USER_HOLDER.remove();
    }
}