package com.mealwise.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置（{@code jwt.*}）。
 *
 * @param secret        HMAC 密钥，UTF-8 编码后至少 32 字节（HS256 要求 256 bit）
 * @param expireSeconds 有效期（秒），默认 24 小时
 * @param header        请求头名，默认 Authorization
 * @param prefix        token 前缀，默认 "Bearer "
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expireSeconds, String header, String prefix) {

    public JwtProperties {
        if (header == null || header.isBlank()) {
            header = "Authorization";
        }
        if (prefix == null || prefix.isBlank()) {
            prefix = "Bearer ";
        }
    }
}
