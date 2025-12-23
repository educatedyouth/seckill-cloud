package com.example.seckill.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT 工具类 (生产级改造版)
 * 1. 移除 static，改为 Spring Bean 管理，支持依赖注入
 * 2. 密钥和过期时间从 Nacos/配置文件读取
 */
// @Slf4j
@Component // 【关键】交给 Spring 容器管理
public class JwtUtil {
    // 手动定义日志对象
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    // 【关键】从配置文件读取 secret，不再硬编码
    @Value("${seckill.jwt.secret}")
    private String secret;

    // 过期时间也支持配置，默认 24小时 (单位毫秒)
    @Value("${seckill.jwt.expiration:86400000}")
    private Long expiration;

    private Key key;

    // Spring 注入完属性后，自动执行初始化方法
    @PostConstruct
    public void init() {
        if (secret == null || secret.length() < 32) {
            // 生产环境必须强制检查密钥强度，防止启动弱密钥服务
            throw new IllegalArgumentException("❌ JWT Secret 配置错误！长度必须至少 32 位，请在 Nacos/YAML 中配置 seckill.jwt.secret");
        }
        // 使用配置的字符串生成 Key
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        log.info(">>> JwtUtil 初始化完成，Token 有效期: {} ms", expiration);
    }

    /**
     * 生成 Token
     */
    public String createToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key) // 使用初始化的 key
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 校验 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            // 生产环境建议打 debug 日志，不要 printStackTrace 刷屏
            log.debug("JWT 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 Token 中获取 UserId
     */
    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }
}