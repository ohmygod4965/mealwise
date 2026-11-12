package com.mealwise.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * 用户身体资料：用于估算每日推荐摄入。
 *
 * <p>年龄可选——缺省按 30 岁估算（Mifflin-St Jeor 公式需要年龄，但产品上不想强人所难）。
 */
public record UserProfileRequest(

        @NotBlank(message = "性别不能为空")
        @Pattern(regexp = "^(MALE|FEMALE)$", message = "性别只能是 MALE 或 FEMALE")
        String gender,

        @NotNull(message = "身高不能为空")
        @DecimalMin(value = "100.0", message = "身高需在 100-250 cm 之间")
        @DecimalMax(value = "250.0", message = "身高需在 100-250 cm 之间")
        BigDecimal heightCm,

        @NotNull(message = "体重不能为空")
        @DecimalMin(value = "30.0", message = "体重需在 30-300 kg 之间")
        @DecimalMax(value = "300.0", message = "体重需在 30-300 kg 之间")
        BigDecimal weightKg,

        @Min(value = 10, message = "年龄需在 10-100 岁之间")
        @Max(value = 100, message = "年龄需在 10-100 岁之间")
        Integer age
) {
}
