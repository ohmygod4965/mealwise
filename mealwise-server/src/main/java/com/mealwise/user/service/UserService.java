package com.mealwise.user.service;

import com.mealwise.user.dto.LoginRequest;
import com.mealwise.user.dto.RegisterRequest;
import com.mealwise.user.entity.SysUser;
import com.mealwise.user.vo.AuthResponse;
import com.mealwise.user.vo.UserVO;
import java.util.List;

/**
 * 用户领域服务。Controller 只做参数接收与结果包装，业务规则全在这里。
 */
public interface UserService {

    /** 注册：用户名唯一、密码 BCrypt 哈希后落库。 */
    UserVO register(RegisterRequest request);

    /** 登录：校验密码，签发 JWT。 */
    AuthResponse login(LoginRequest request);

    /** 按 id 取用户，不存在抛 404（供内部调用）。 */
    SysUser requireById(long userId);

    /** 取用户过敏原 token 列表（空库字段返回空列表）。 */
    List<String> allergensOf(long userId);

    /** 当前登录用户信息（含身体资料）。 */
    UserVO me(long userId);

    /** 更新身体资料（性别/身高/体重/年龄），返回更新后的视图。 */
    UserVO updateProfile(long userId, com.mealwise.user.dto.UserProfileRequest request);
}
