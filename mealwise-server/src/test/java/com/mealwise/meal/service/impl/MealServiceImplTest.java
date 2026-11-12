package com.mealwise.meal.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.mealwise.common.exception.BizException;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import com.mealwise.food.service.FoodService;
import com.mealwise.meal.entity.Meal;
import com.mealwise.meal.entity.MealItem;
import com.mealwise.meal.mapper.MealItemMapper;
import com.mealwise.meal.mapper.MealMapper;
import com.mealwise.meal.vo.MealCaloriesVO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 餐次服务单测：重点是「归属校验」和「热量换算」。
 * 纯 Mockito，不启动 Spring、不连库。
 */
@ExtendWith(MockitoExtension.class)
class MealServiceImplTest {

    @Mock
    private MealMapper mealMapper;
    @Mock
    private MealItemMapper itemMapper;
    @Mock
    private FoodMapper foodMapper;
    @Mock
    private FoodService foodService;

    @InjectMocks
    private MealServiceImpl mealService;

    @Test
    @DisplayName("餐次不存在 → 404")
    void caloriesMealNotFound() {
        when(mealMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> mealService.calories(1L, 99L))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(404));
    }

    @Test
    @DisplayName("访问他人餐次 → 403（越权）")
    void caloriesForbiddenForOtherUser() {
        // meal 属于 userId=7，但调用方是 userId=1
        when(mealMapper.selectById(10L)).thenReturn(meal(10L, 7L));

        assertThatThrownBy(() -> mealService.calories(1L, 10L))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));
    }

    @Test
    @DisplayName("热量合计：按每 100g 换算并对三项求和")
    void caloriesSumsItems() {
        when(mealMapper.selectById(10L)).thenReturn(meal(10L, 1L));
        when(itemMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                item(1L, 10L, 1L, "200"),   // 米饭 116/100g → 232.0
                item(2L, 10L, 2L, "150")));  // 鸡胸肉 133/100g → 199.5
        when(foodMapper.selectBatchIds(anyList())).thenReturn(List.of(
                food(1L, "米饭", "主食", "116.0"),
                food(2L, "鸡胸肉", "肉蛋", "133.0")));

        MealCaloriesVO result = mealService.calories(1L, 10L);

        assertThat(result.items()).hasSize(2);
        assertThat(result.total().calories()).isEqualByComparingTo("431.5");
        assertThat(result.totalGrams()).isEqualByComparingTo("350.0");
        assertThat(result.itemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("改他人餐次里的项 → 403（先校验餐次归属）")
    void updateItemForbidden() {
        when(mealMapper.selectById(10L)).thenReturn(meal(10L, 7L));

        assertThatThrownBy(() -> mealService.deleteItem(1L, 10L, 55L))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));
    }

    // ---------------------------------------------------------------- helpers

    private Meal meal(long id, long userId) {
        Meal meal = new Meal();
        meal.setId(id);
        meal.setUserId(userId);
        meal.setMealType("LUNCH");
        return meal;
    }

    private MealItem item(long id, long mealId, long foodId, String qty) {
        MealItem item = new MealItem();
        item.setId(id);
        item.setMealId(mealId);
        item.setFoodId(foodId);
        item.setQuantityG(new BigDecimal(qty));
        return item;
    }

    private Food food(long id, String name, String category, String calories) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setCategory(category);
        food.setCaloriesPer100g(new BigDecimal(calories));
        food.setProteinPer100g(BigDecimal.ONE);
        food.setFatPer100g(BigDecimal.ONE);
        food.setCarbPer100g(BigDecimal.ONE);
        food.setEnabled(true);
        return food;
    }
}
