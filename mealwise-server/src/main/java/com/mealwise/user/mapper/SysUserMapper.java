package com.mealwise.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mealwise.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。MyBatis-Plus 提供单表 CRUD，复杂查询再手写 XML/注解。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
