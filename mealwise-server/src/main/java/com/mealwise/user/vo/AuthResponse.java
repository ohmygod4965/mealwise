package com.mealwise.user.vo;

/**
 * 登录响应。
 *
 * @param token     JWT
 * @param tokenType 固定 Bearer，前端拼 Authorization 头用
 * @param expiresIn 有效期（秒）
 * @param user      当前用户信息（含过敏原，前端可直接展示）
 */
public record AuthResponse(String token, String tokenType, long expiresIn, UserVO user) {
}
