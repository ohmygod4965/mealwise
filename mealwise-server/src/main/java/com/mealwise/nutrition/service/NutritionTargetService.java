package com.mealwise.nutrition.service;

import com.mealwise.nutrition.vo.NutritionTargetVO;
import com.mealwise.user.entity.SysUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 每日推荐摄入计算。
 *
 * <p>公式：Mifflin-St Jeor
 * <ul>
 *   <li>男：BMR = 10×体重kg + 6.25×身高cm − 5×年龄 + 5</li>
 *   <li>女：BMR = 10×体重kg + 6.25×身高cm − 5×年龄 − 161</li>
 * </ul>
 * TDEE = BMR × 活动系数（本项目固定 1.375 轻度活动）。
 *
 * <p>三大营养素：蛋白质 1.5 g/kg、脂肪占热量 25%、剩余热量给碳水。
 * 这是"维持体重"口径，不内置减脂/增肌目标——产品上要的话再加参数，而不是偷偷改系数。
 */
@Service
public class NutritionTargetService {

    private static final BigDecimal ACTIVITY_FACTOR = new BigDecimal("1.375");
    private static final int DEFAULT_AGE = 30;
    private static final BigDecimal PROTEIN_PER_KG = new BigDecimal("1.5");
    private static final BigDecimal FAT_RATIO = new BigDecimal("0.25");

    private static final BigDecimal KCAL_PER_G_PROTEIN = new BigDecimal("4");
    private static final BigDecimal KCAL_PER_G_FAT = new BigDecimal("9");
    private static final BigDecimal KCAL_PER_G_CARB = new BigDecimal("4");

    /**
     * @return 目标值；用户资料不全（缺性别/身高/体重）时返回 null，由上层决定如何提示
     */
    public NutritionTargetVO compute(SysUser user) {
        if (user == null || user.getGender() == null || user.getHeightCm() == null || user.getWeightKg() == null) {
            return null;
        }

        boolean male = "MALE".equalsIgnoreCase(user.getGender());
        int age = user.getAge() != null ? user.getAge() : DEFAULT_AGE;
        BigDecimal height = user.getHeightCm();
        BigDecimal weight = user.getWeightKg();

        BigDecimal base = weight.multiply(BigDecimal.TEN)
                .add(height.multiply(new BigDecimal("6.25")))
                .subtract(new BigDecimal(5L * age))
                .add(male ? new BigDecimal("5") : new BigDecimal("-161"));
        // 只在出口取整：先用未截断的 BMR 算 TDEE，否则 1648.75→1648.8 会让 TDEE 差 0.1
        BigDecimal bmr = scale1(base);
        BigDecimal tdee = scale1(base.multiply(ACTIVITY_FACTOR));
        BigDecimal calories = tdee;

        BigDecimal protein = scale1(weight.multiply(PROTEIN_PER_KG));
        BigDecimal fat = scale1(calories.multiply(FAT_RATIO).divide(KCAL_PER_G_FAT, 4, RoundingMode.HALF_UP));
        BigDecimal remaining = calories
                .subtract(protein.multiply(KCAL_PER_G_PROTEIN))
                .subtract(fat.multiply(KCAL_PER_G_FAT));
        BigDecimal carb = scale1(remaining.max(BigDecimal.ZERO).divide(KCAL_PER_G_CARB, 4, RoundingMode.HALF_UP));

        List<String> assumptions = new ArrayList<>();
        if (user.getAge() == null) {
            assumptions.add("年龄未填写，按 " + DEFAULT_AGE + " 岁估算");
        }
        assumptions.add("活动水平按轻度活动（系数 1.375）估算");
        assumptions.add("目标为维持体重：蛋白质 1.5 g/kg、脂肪占总热量 25%、其余由碳水补齐");

        return new NutritionTargetVO(
                bmr, tdee, calories, protein, fat, carb,
                "Mifflin-St Jeor 基础代谢 × 1.375 活动系数",
                assumptions);
    }

    private BigDecimal scale1(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}
