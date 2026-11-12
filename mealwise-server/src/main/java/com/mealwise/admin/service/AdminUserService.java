package com.mealwise.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mealwise.admin.dto.ResetPasswordRequest;
import com.mealwise.admin.dto.UpdateUserRoleRequest;
import com.mealwise.admin.dto.UpdateUserStatusRequest;
import com.mealwise.admin.vo.AdminUserVO;
import com.mealwise.common.api.PageResult;
import com.mealwise.common.exception.BizException;
import com.mealwise.common.util.AllergenCodec;
import com.mealwise.meal.entity.Meal;
import com.mealwise.meal.entity.MealItem;
import com.mealwise.meal.mapper.MealItemMapper;
import com.mealwise.meal.mapper.MealMapper;
import com.mealwise.user.entity.SysUser;
import com.mealwise.user.mapper.SysUserMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号管理（仅管理员）。
 *
 * <p>三个安全约束，任何一条都不允许绕过：
 * <ol>
 *   <li>不能改自己的状态/角色（防止把自己锁在门外）；</li>
 *   <li>不能删除自己；</li>
 *   <li>不能禁用/降级<b>最后一个</b>管理员——否则系统再也没人能管理。</li>
 * </ol>
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final String ROLE_ADMIN = "ADMIN";

    private final SysUserMapper userMapper;
    private final MealMapper mealMapper;
    private final MealItemMapper mealItemMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(SysUserMapper userMapper, MealMapper mealMapper,
                            MealItemMapper mealItemMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.mealMapper = mealMapper;
        this.mealItemMapper = mealItemMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<AdminUserVO> list(int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SysUser::getUsername, keyword.trim());
        }

        IPage<SysUser> result = userMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<AdminUserVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), safePage, safeSize);
    }

    @Transactional
    public AdminUserVO updateStatus(long operatorId, long targetId, UpdateUserStatusRequest request) {
        SysUser target = requireUser(targetId);
        assertNotSelf(operatorId, targetId, "不能修改自己的账号状态");
        if (request.status() == 0) {
            assertNotLastAdmin(target, "不能禁用最后一个管理员");
        }
        target.setStatus(request.status());
        userMapper.updateById(target);
        log.info("admin {} set user {} status={}", operatorId, targetId, request.status());
        return toVO(target);
    }

    @Transactional
    public AdminUserVO updateRole(long operatorId, long targetId, UpdateUserRoleRequest request) {
        SysUser target = requireUser(targetId);
        assertNotSelf(operatorId, targetId, "不能修改自己的角色");
        String newRole = request.role() == null ? "USER" : request.role().trim().toUpperCase();
        if (!newRole.equals(ROLE_ADMIN)) {
            assertNotLastAdmin(target, "不能降级最后一个管理员");
        }
        target.setRole(newRole);
        userMapper.updateById(target);
        log.info("admin {} set user {} role={}", operatorId, targetId, newRole);
        return toVO(target);
    }

    @Transactional
    public void resetPassword(long operatorId, long targetId, ResetPasswordRequest request) {
        SysUser target = requireUser(targetId);
        target.setPasswordHash(passwordEncoder.encode(request.password()));
        userMapper.updateById(target);
        log.info("admin {} reset password for user {}", operatorId, targetId);
    }

    @Transactional
    public void delete(long operatorId, long targetId) {
        SysUser target = requireUser(targetId);
        assertNotSelf(operatorId, targetId, "不能删除自己的账号");
        assertNotLastAdmin(target, "不能删除最后一个管理员");

        // 先删明细再删餐次，最后删账号：meal_item -> meal -> sys_user
        List<Long> mealIds = mealMapper.selectList(new LambdaQueryWrapper<Meal>()
                        .eq(Meal::getUserId, targetId))
                .stream().map(Meal::getId).toList();
        if (!mealIds.isEmpty()) {
            mealItemMapper.delete(new LambdaQueryWrapper<MealItem>().in(MealItem::getMealId, mealIds));
        }
        mealMapper.delete(new LambdaQueryWrapper<Meal>().eq(Meal::getUserId, targetId));
        userMapper.deleteById(targetId);
        log.info("admin {} deleted user {} ({} meals)", operatorId, targetId, mealIds.size());
    }

    // ---------------------------------------------------------------- 内部

    private SysUser requireUser(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private void assertNotSelf(long operatorId, long targetId, String message) {
        if (operatorId == targetId) {
            throw BizException.badRequest(message);
        }
    }

    private void assertNotLastAdmin(SysUser target, String message) {
        if (!ROLE_ADMIN.equalsIgnoreCase(target.getRole())) {
            return;
        }
        Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, ROLE_ADMIN)
                .eq(SysUser::getStatus, 1));
        if (adminCount != null && adminCount <= 1) {
            throw BizException.badRequest(message);
        }
    }

    private AdminUserVO toVO(SysUser user) {
        String role = user.getRole() == null ? "USER" : user.getRole();
        int status = user.getStatus() == null ? 1 : user.getStatus();
        List<String> allergens = AllergenCodec.decode(user.getAllergens());
        boolean profileComplete = user.getGender() != null && !user.getGender().isBlank()
                && user.getHeightCm() != null && user.getWeightKg() != null;
        return new AdminUserVO(user.getId(), user.getUsername(), role, status,
                allergens, user.getGender(), profileComplete, user.getCreatedAt());
    }
}
