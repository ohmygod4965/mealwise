package com.mealwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 应用入口。
 *
 * <p>Mapper 采用「每个接口标注 {@code @Mapper}」的方式注册，不用 {@code @MapperScan}：
 * 后者会把包内所有接口（含 Service 接口）都当成 Mapper 注册，反而容易出错。
 *
 * <p>{@code @ConfigurationPropertiesScan} 让 {@code jwt} 配置绑定到 {@code JwtProperties} record 上。
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.mealwise")
public class MealwiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(MealwiseApplication.class, args);
    }
}
