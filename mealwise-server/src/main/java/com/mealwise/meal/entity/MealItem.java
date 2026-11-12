package com.mealwise.meal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 餐次食物项：只存 foodId + 克数。
 *
 * <p>刻意不冗余存热量/营养：那些是派生值，一旦冗余就要在"改克数、换食物、
 * 修正食物库"三条路径同步，任何一条漏写都会永久错账。计算成本是单餐几次乘法，不值得换一致性。
 */
@TableName("meal_item")
public class MealItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mealId;

    private Long foodId;

    private BigDecimal quantityG;

    /** 乐观锁版本号：更新时作为 where 条件，冲突则影响行数为 0 */
    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMealId() {
        return mealId;
    }

    public void setMealId(Long mealId) {
        this.mealId = mealId;
    }

    public Long getFoodId() {
        return foodId;
    }

    public void setFoodId(Long foodId) {
        this.foodId = foodId;
    }

    public BigDecimal getQuantityG() {
        return quantityG;
    }

    public void setQuantityG(BigDecimal quantityG) {
        this.quantityG = quantityG;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
