package com.example.seckill.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
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
     * 生成 JWT Token
     * @param userId 当前登录用户的唯一标识
     * @return 生成后的 JWT 字符串
     */
    public String createToken(Long userId) {

        // 用于存放 JWT 中的自定义声明（Claims）
        // Claims 本质上是一个 Map，用来保存需要写入 Token 的业务数据
        Map<String, Object> claims = new HashMap<>();

        // 向 Claims 中放入用户 ID
        // 该数据在解析 Token 时可以直接获取，用于识别当前用户
        claims.put("userId", userId);

        // 使用 JJWT 提供的 Builder 构建 JWT
        return Jwts.builder()

                // 设置自定义声明（Claims）
                // 注意：一旦调用 setClaims，后续如果再设置标准声明（如 subject），
                // 底层会合并到 Claims 中
                .setClaims(claims)

                // 设置 JWT 的主题（Subject）
                // 通常用于表示 Token 的“主体”，这里使用 userId 的字符串形式
                .setSubject(String.valueOf(userId))

                // 设置 Token 的签发时间（Issued At）
                // 使用当前系统时间，单位为毫秒
                .setIssuedAt(new Date(System.currentTimeMillis()))

                // 设置 Token 的过期时间（Expiration）
                // 当前时间 + expiration（通常是配置的过期毫秒数）
                // Token 在该时间之后将被视为无效
                .setExpiration(new Date(System.currentTimeMillis() + expiration))

                // 使用指定的密钥和签名算法对 Token 进行签名
                // HS256 是基于 HMAC-SHA256 的对称加密算法
                .signWith(key, SignatureAlgorithm.HS256)

                // 生成最终的 JWT 字符串
                .compact();
    }

    /**
     * 解析 JWT Token
     * @param token 前端或客户端传递过来的 JWT 字符串
     * @return Claims，包含 Token 中携带的所有声明信息
     * @throws io.jsonwebtoken.JwtException
     *         当 Token 无效、过期、签名不正确时会抛出异常
     */
    public Claims parseToken(String token) {

        // 使用 JJWT 提供的解析器构建器
        // parserBuilder 是线程安全的推荐写法
        return Jwts.parserBuilder()

                // 设置用于验证签名的密钥
                // 该 key 必须与生成 Token 时使用的 key 完全一致
                // 否则会抛出 SignatureException
                .setSigningKey(key)

                // 构建真正的 JWT 解析器实例
                .build()

                // 解析并校验 JWT
                // 该方法会同时执行以下操作：
                // 1. 校验 Token 的格式是否合法
                // 2. 校验签名是否正确
                // 3. 校验 Token 是否已过期（exp）
                // 4. 校验 Token 是否在生效时间之后（nbf，如存在）
                .parseClaimsJws(token)

                // 获取 JWT 的 Payload（即 Claims）
                // Claims 中包含自定义声明和标准声明（sub、iat、exp 等）
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