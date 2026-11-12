package com.mealwise.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.mealwise.user.entity.SysUser;
import com.mealwise.user.mapper.SysUserMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 解析 {@code Authorization: Bearer <token>}，把主体放进 SecurityContext。
 *
 * <p>这里**不抛异常**：token 缺失/非法时只清空上下文并放行，
 * 由 Security 的 entryPoint 统一返回 401。这样既保证 401 语义，
 * 又不会把「没带 token 的公开接口」误伤。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;
    private final JwtProperties properties;
    private final SysUserMapper userMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, JwtProperties properties, SysUserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.properties = properties;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtUtil.parse(token);
                long userId = Long.parseLong(claims.getSubject());

                // 角色与状态以库为准，而不是 token 里的快照：
                // 管理员改了角色/禁用账号后立即生效，不用等 token 过期。
                SysUser dbUser = userMapper.selectById(userId);
                if (dbUser == null || (dbUser.getStatus() != null && dbUser.getStatus() == 0)) {
                    SecurityContextHolder.clearContext();
                    log.debug("user {} missing or disabled, token rejected", userId);
                } else {
                    String role = dbUser.getRole() == null ? "USER" : dbUser.getRole().toUpperCase();
                    UserPrincipal principal = new UserPrincipal(userId, dbUser.getUsername(), role);

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    MDC.put("userId", String.valueOf(userId));
                }
                // 让日志能按用户维度检索：并发场景下只有 traceId 不够用
                MDC.put("userId", String.valueOf(userId));
            } catch (JwtException | IllegalArgumentException ex) {
                // 过期/伪造/格式错误：清空上下文，交给 entryPoint 返回 401
                SecurityContextHolder.clearContext();
                log.debug("jwt rejected: {}", ex.getMessage());
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 线程池复用，必须清理，否则下一个请求会带上别人的 userId
            MDC.remove("userId");
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(properties.header());
        if (header == null || header.isBlank()) {
            return null;
        }
        String prefix = properties.prefix();
        if (prefix != null && !prefix.isBlank()) {
            if (!header.startsWith(prefix)) {
                return null;
            }
            return header.substring(prefix.length()).trim();
        }
        return header.trim();
    }
}
