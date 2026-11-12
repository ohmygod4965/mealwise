package com.mealwise.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mealwise.common.exception.BizException;
import com.mealwise.common.util.AllergenCodec;
import com.mealwise.security.JwtUtil;
import com.mealwise.user.dto.LoginRequest;
import com.mealwise.user.dto.RegisterRequest;
import com.mealwise.user.dto.UserProfileRequest;
import com.mealwise.user.entity.SysUser;
import com.mealwise.user.mapper.SysUserMapper;
import com.mealwise.user.service.UserService;
import com.mealwise.user.vo.AuthResponse;
import com.mealwise.user.vo.UserVO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务实现。
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional
    public UserVO register(RegisterRequest request) {
        String username = request.username().trim();
        if (existsByUsername(username)) {
            throw BizException.conflict("用户名已被占用");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAllergens(AllergenCodec.encode(request.allergens()));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 并发注册撞唯一索引：先查后插仍有窗口，这里兜住并转成业务语义
            throw BizException.conflict("用户名已被占用");
        }
        log.info("registered userId={} username={}", user.getId(), user.getUsername());
        return toVO(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        SysUser user = findByUsername(request.username().trim());
        // 用户不存在与密码错误返回同一文案，避免用户名枚举
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw BizException.forbidden("账号已被禁用，请联系管理员");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        log.info("login success userId={} role={}", user.getId(), user.getRole());
        return new AuthResponse(token, "Bearer", jwtUtil.getExpireSeconds(), toVO(user));
    }

    @Override
    public SysUser requireById(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    @Override
    public List<String> allergensOf(long userId) {
        SysUser user = userMapper.selectById(userId);
        return user == null ? List.of() : AllergenCodec.decode(user.getAllergens());
    }

    @Override
    public UserVO me(long userId) {
        return toVO(requireById(userId));
    }

    @Override
    @Transactional
    public UserVO updateProfile(long userId, UserProfileRequest request) {
        SysUser user = requireById(userId);
        user.setGender(request.gender().trim().toUpperCase());
        user.setHeightCm(request.heightCm());
        user.setWeightKg(request.weightKg());
        user.setAge(request.age());
        userMapper.updateById(user);
        log.info("profile updated userId={} gender={} height={} weight={}",
                userId, user.getGender(), user.getHeightCm(), user.getWeightKg());
        return toVO(user);
    }

    private boolean existsByUsername(String username) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        return count != null && count > 0;
    }

    private SysUser findByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("LIMIT 1"));
    }

    private UserVO toVO(SysUser user) {
        boolean complete = user.getGender() != null
                && user.getHeightCm() != null
                && user.getWeightKg() != null;
        return new UserVO(
                user.getId(),
                user.getUsername(),
                AllergenCodec.decode(user.getAllergens()),
                user.getGender(),
                user.getHeightCm(),
                user.getWeightKg(),
                user.getAge(),
                complete,
                user.getRole() == null ? "USER" : user.getRole());
    }
}
