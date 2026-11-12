package com.mealwise.nutrition.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealwise.nutrition.vo.NutritionTargetVO;
import com.mealwise.user.entity.SysUser;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 每日建议摄入的计算口径单测（纯函数，无 Spring）。
 */
class NutritionTargetServiceTest {

    private final NutritionTargetService service = new NutritionTargetService();

    @Test
    @DisplayName("男性 70kg/175cm/30岁：BMR、TDEE 与三大营养素")
    void maleTargets() {
        SysUser user = user("MALE", "175.0", "70.0", 30);

        NutritionTargetVO t = service.compute(user);

        // BMR = 10*70 + 6.25*175 - 5*30 + 5 = 1648.75
        assertThat(t.bmr()).isEqualByComparingTo("1648.8");
        // TDEE = 1648.75 * 1.375 = 2267.03
        assertThat(t.calories()).isEqualByComparingTo("2267.0");
        assertThat(t.protein()).isEqualByComparingTo("105.0");
        // 脂肪 = 2267 * 25% / 9 = 62.97 -> 63.0
        assertThat(t.fat()).isEqualByComparingTo("63.0");
        // 碳水 = (2267 - 105*4 - 63*9) / 4 = 320.0
        assertThat(t.carb()).isEqualByComparingTo("320.0");
    }

    @Test
    @DisplayName("女性公式系数不同（-161 而非 +5）")
    void femaleBmrLower() {
        SysUser male = user("MALE", "165.0", "55.0", 25);
        SysUser female = user("FEMALE", "165.0", "55.0", 25);

        BigDecimal maleBmr = service.compute(male).bmr();
        BigDecimal femaleBmr = service.compute(female).bmr();

        // 差值是 5 - (-161) = 166
        assertThat(maleBmr.subtract(femaleBmr)).isEqualByComparingTo("166.0");
    }

    @Test
    @DisplayName("资料不全 → 返回 null，不猜")
    void incompleteProfileReturnsNull() {
        assertThat(service.compute(user(null, "175.0", "70.0", 30))).isNull();
        assertThat(service.compute(user("MALE", null, "70.0", 30))).isNull();
        assertThat(service.compute(user("MALE", "175.0", null, 30))).isNull();
    }

    @Test
    @DisplayName("年龄缺省按 30 岁，并在假设里说明")
    void defaultAgeIsDisclosed() {
        NutritionTargetVO t = service.compute(user("MALE", "175.0", "70.0", null));

        assertThat(t).isNotNull();
        assertThat(t.assumptions()).anyMatch(a -> a.contains("年龄未填写"));
    }

    private SysUser user(String gender, String height, String weight, Integer age) {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("demo");
        u.setGender(gender);
        u.setHeightCm(height == null ? null : new BigDecimal(height));
        u.setWeightKg(weight == null ? null : new BigDecimal(weight));
        u.setAge(age);
        return u;
    }
}
