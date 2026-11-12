package com.mealwise.food.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 新增/编辑食物的请求体。
 *
 * <p>新增与编辑共用同一结构（全量 PUT）：字段少、语义清晰，
 * 比"部分更新 + 大量 Optional 判断"更不容易出空指针。
 */
public record FoodSaveRequest(

        @NotBlank(message = "食物名称不能为空")
        @Size(max = 64, message = "食物名称最长 64 字")
        String name,

        @NotBlank(message = "类别不能为空")
        @Size(max = 32, message = "类别最长 32 字")
        String category,

        @NotNull(message = "每 100g 热量不能为空")
        @DecimalMin(value = "0", message = "热量不能为负")
        @Digits(integer = 6, fraction = 1, message = "热量最多 6 位整数、1 位小数")
        BigDecimal caloriesPer100g,

        @NotNull(message = "每 100g 蛋白质不能为空")
        @DecimalMin(value = "0", message = "蛋白质不能为负")
        @Digits(integer = 5, fraction = 1, message = "蛋白质最多 5 位整数、1 位小数")
        BigDecimal proteinPer100g,

        @NotNull(message = "每 100g 脂肪不能为空")
        @DecimalMin(value = "0", message = "脂肪不能为负")
        @Digits(integer = 5, fraction = 1, message = "脂肪最多 5 位整数、1 位小数")
        BigDecimal fatPer100g,

        @NotNull(message = "每 100g 碳水不能为空")
        @DecimalMin(value = "0", message = "碳水不能为负")
        @Digits(integer = 5, fraction = 1, message = "碳水最多 5 位整数、1 位小数")
        BigDecimal carbPer100g,

        @Size(max = 16, message = "单位名最长 16 字")
        String unitName,

        @DecimalMin(value = "0.1", message = "单位重量需大于 0")
        @Digits(integer = 6, fraction = 1, message = "单位重量最多 6 位整数、1 位小数")
        BigDecimal unitWeightG,

        List<String> allergens,

        Boolean enabled
) {
}
