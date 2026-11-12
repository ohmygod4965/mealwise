package com.mealwise.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 序列化约定：
 * <ul>
 *   <li>所有 {@link BigDecimal} 输出固定 1 位小数（HALF_UP），前端不用做四舍五入；</li>
 *   <li>内部计算仍用 BigDecimal 全精度，只在出口截断，避免误差累积。</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer mealwiseBigDecimalCustomizer() {
        return builder -> builder.serializerByType(BigDecimal.class, new BigDecimalOneDecimalSerializer());
    }

    static class BigDecimalOneDecimalSerializer extends JsonSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeNumber(value.setScale(1, RoundingMode.HALF_UP));
        }
    }
}
