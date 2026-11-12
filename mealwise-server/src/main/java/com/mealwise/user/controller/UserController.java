package com.mealwise.user.controller;

import com.mealwise.common.api.Result;
import com.mealwise.user.dto.UserProfileRequest;
import com.mealwise.user.service.UserService;
import com.mealwise.user.vo.UserVO;
import com.mealwise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户资料。userId 只从 token 取，路径里不出现用户 id。
 */
@Tag(name = "04. 用户资料", description = "查看/更新性别、身高、体重、年龄，用于计算每日建议摄入")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "当前用户信息", description = "profileComplete=false 表示还没填身体资料")
    @GetMapping("/me")
    public Result<UserVO> me(@AuthenticationPrincipal UserPrincipal principal) {
        return Result.ok(userService.me(principal.id()));
    }

    @Operation(summary = "更新身体资料", description = "身高 100-250cm、体重 30-300kg、年龄可选（缺省 30）")
    @PutMapping("/me/profile")
    public Result<UserVO> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody UserProfileRequest request) {
        return Result.ok(userService.updateProfile(principal.id(), request));
    }
}
