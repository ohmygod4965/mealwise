package com.mealwise.admin.controller;

import com.mealwise.admin.dto.ResetPasswordRequest;
import com.mealwise.admin.dto.UpdateUserRoleRequest;
import com.mealwise.admin.dto.UpdateUserStatusRequest;
import com.mealwise.admin.service.AdminUserService;
import com.mealwise.admin.vo.AdminUserVO;
import com.mealwise.common.api.PageResult;
import com.mealwise.common.api.Result;
import com.mealwise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号管理接口（仅 ADMIN）。
 *
 * <p>双重保护：SecurityConfig 里配了 {@code /api/admin/** hasRole('ADMIN')}，
 * 方法上再加 {@code @PreAuthorize}，避免以后调路由白名单时误放行。
 */
@Tag(name = "07. 账号管理（管理员）", description = "列出用户、启用/禁用、改角色、重置密码、删除账号")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "用户列表（分页 + 用户名模糊）")
    @GetMapping("/users")
    public Result<PageResult<AdminUserVO>> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String keyword) {
        return Result.ok(adminUserService.list(page, size, keyword));
    }

    @Operation(summary = "启用/禁用账号")
    @PatchMapping("/users/{id}/status")
    public Result<AdminUserVO> updateStatus(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable long id,
                                            @Valid @RequestBody UpdateUserStatusRequest request) {
        return Result.ok(adminUserService.updateStatus(principal.id(), id, request));
    }

    @Operation(summary = "修改角色")
    @PatchMapping("/users/{id}/role")
    public Result<AdminUserVO> updateRole(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable long id,
                                          @Valid @RequestBody UpdateUserRoleRequest request) {
        return Result.ok(adminUserService.updateRole(principal.id(), id, request));
    }

    @Operation(summary = "重置密码")
    @PostMapping("/users/{id}/reset-password")
    public Result<Void> resetPassword(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable long id,
                                      @Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(principal.id(), id, request);
        return Result.ok();
    }

    @Operation(summary = "删除账号（连带删除其餐次）")
    @DeleteMapping("/users/{id}")
    public Result<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable long id) {
        adminUserService.delete(principal.id(), id);
        return Result.ok();
    }
}
