package com.mealwise.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JwtUtil 纯单元测试：不启动 Spring，不连数据库。
 */
class JwtUtilTest {

    private static final String SECRET = "mealwise-test-secret-key-0123456789-abcdefghij";

    private JwtUtil newUtil(long expireSeconds) {
        return new JwtUtil(new JwtProperties(SECRET, expireSeconds, "Authorization", "Bearer "));
    }

    @Test
    @DisplayName("签发的 token 能解析出 userId 与 username")
    void generateAndParse() {
        JwtUtil util = newUtil(3600);
        String token = util.generate(42L, "demo");

        Claims claims = util.parse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("username", String.class)).isEqualTo("demo");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("过期 token 解析时抛 ExpiredJwtException")
    void expiredTokenRejected() {
        JwtUtil util = newUtil(1);
        String token = util.generate(1L, "demo");

        // 有效期 1 秒，睡 1.2 秒确保过期
        assertThatThrownBy(() -> {
            sleep(1200);
            util.parse(token);
        }).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("被篡改的 token 验签失败")
    void tamperedTokenRejected() {
        JwtUtil util = newUtil(3600);
        String token = util.generate(1L, "demo");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> util.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("密钥短于 32 字节时启动即失败")
    void shortSecretRejected() {
        assertThatThrownBy(() -> new JwtUtil(new JwtProperties("too-short", 3600, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
