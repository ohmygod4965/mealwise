package com.mealwise.security;

/**
 * 认证通过后放进 SecurityContext 的主体。
 *
 * <p>带上角色，但不带其他业务字段：角色在每次请求时从库里读（见 {@code JwtAuthFilter}），
 * 保证「改库即生效」——管理员把某人降级后，不需要等 token 过期。
 *
 * @param id       用户 id
 * @param username 用户名
 * @param role     ADMIN / USER
 */
public record UserPrincipal(long id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
