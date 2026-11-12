package com.mealwise.user.controller;

import com.mealwise.common.api.Result;
import com.mealwise.user.dto.LoginRequest;
import com.mealwise.user.dto.RegisterRequest;
import com.mealwise.user.service.UserService;
import com.mealwise.user.vo.AuthResponse;
import com.mealwise.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。Controller 只做「收参数 → 调 Service → 包 Result」，不含业务判断。
 */
@Tag(name = "01. 认证", description = "注册与登录")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "注册", description = "用户名唯一；密码 BCrypt 存储；可选设置过敏原")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(request));
    }

    @Operation(summary = "登录", description = "返回 JWT，有效期见 expiresIn（秒）")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }
}
