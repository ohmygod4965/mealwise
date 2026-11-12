package com.mealwise.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Knife4j 元信息。
 *
 * <p>文档地址：{@code /doc.html}（Knife4j）与 {@code /swagger-ui.html}（原生）。
 * 全局声明 bearer 鉴权，页面右上角「Authorize」填入登录返回的 token 即可调试。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI mealwiseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mealwise · 一餐热量与替换推荐 API")
                        .version("1.0.0")
                        .description("记录一餐食物与克数，计算热量与三大营养素，并给出同类别替换建议。"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token，直接粘贴，无需加 Bearer 前缀")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
