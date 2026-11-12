package com.mealwise.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件：只装分页拦截器。
 *
 * <p>不装乐观锁/多租户插件——本项目没有并发写冲突场景，也不做多租户
 * （数据隔离靠 {@code meal.user_id} 显式条件，见阶段 4）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限 500，防止 size=100000 打爆 DB
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        // 乐观锁：meal_item.version，防止多端同时改同一行时静默覆盖
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
