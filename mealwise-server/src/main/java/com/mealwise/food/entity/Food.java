package com.mealwise.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 食物库实体。营养值均以「每 100g」为基准，字段名尾部带单位，避免歧义。
 */
@TableName("food")
public class Food {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String category;

    @TableField("calories_per_100g")
    private BigDecimal caloriesPer100g;

    @TableField("protein_per_100g")
    private BigDecimal proteinPer100g;

    @TableField("fat_per_100g")
    private BigDecimal fatPer100g;

    @TableField("carb_per_100g")
    private BigDecimal carbPer100g;

    private String unitName;

    private BigDecimal unitWeightG;

    /** 逗号分隔 token，见 AllergenCodec。 */
    private String allergens;

    /** true = 上架；false = 软删除。 */
    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getCaloriesPer100g() {
        return caloriesPer100g;
    }

    public void setCaloriesPer100g(BigDecimal caloriesPer100g) {
        this.caloriesPer100g = caloriesPer100g;
    }

    public BigDecimal getProteinPer100g() {
        return proteinPer100g;
    }

    public void setProteinPer100g(BigDecimal proteinPer100g) {
        this.proteinPer100g = proteinPer100g;
    }

    public BigDecimal getFatPer100g() {
        return fatPer100g;
    }

    public void setFatPer100g(BigDecimal fatPer100g) {
        this.fatPer100g = fatPer100g;
    }

    public BigDecimal getCarbPer100g() {
        return carbPer100g;
    }

    public void setCarbPer100g(BigDecimal carbPer100g) {
        this.carbPer100g = carbPer100g;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public BigDecimal getUnitWeightG() {
        return unitWeightG;
    }

    public void setUnitWeightG(BigDecimal unitWeightG) {
        this.unitWeightG = unitWeightG;
    }

    public String getAllergens() {
        return allergens;
    }

    public void setAllergens(String allergens) {
        this.allergens = allergens;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
