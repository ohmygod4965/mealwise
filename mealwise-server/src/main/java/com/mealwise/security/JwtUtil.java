package com.mealwise.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 生成与解析（jjwt 0.12.x API）。
 *
 * <p>载荷只放 {@code sub=userId} 与 {@code username}：不塞角色、不塞过敏原。
 * 角色/过敏原这类会变的数据随请求从库里查，避免「token 没到期但权限已变」的脏读。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtUtil(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret 至少需要 32 字节（HS256 密钥长度要求）");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = properties.expireSeconds() > 0 ? properties.expireSeconds() : 86400L;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    /** 签发 token。 */
    public String generate(long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签。签名错误 / 过期 / 格式错误都会抛 {@link io.jsonwebtoken.JwtException}。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
