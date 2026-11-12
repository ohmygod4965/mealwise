package com.mealwise.recommendation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.mealwise.common.exception.BizException;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import com.mealwise.meal.entity.Meal;
import com.mealwise.meal.entity.MealItem;
import com.mealwise.meal.service.MealService;
import com.mealwise.recommendation.vo.ReplacementResponse;
import com.mealwise.recommendation.vo.SuggestionVO;
import com.mealwise.user.service.UserService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 替换推荐单测：策略校验、过敏原过滤、Top3 排序、无候选。
 */
@ExtendWith(MockitoExtension.class)
class ReplacementServiceImplTest {

    @Mock
    private MealService mealService;
    @Mock
    private FoodMapper foodMapper;
    @Mock
    private UserService userService;

    @InjectMocks
    private ReplacementServiceImpl service;

    @Test
    @DisplayName("非法策略 → 400，且不应该触碰数据库")
    void rejectsIllegalStrategy() {
        assertThatThrownBy(() -> service.recommend(1L, 10L, "desc"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(400));
    }

    @Test
    @DisplayName("没有候选 → 空列表 + 友好提示（仍是成功）")
    void emptyCandidates() {
        when(mealService.requireOwned(1L, 10L)).thenReturn(new Meal());
        when(mealService.itemsOf(10L)).thenReturn(List.of(item(5L, 100L, "100")));
        when(foodMapper.selectById(100L)).thenReturn(food(100L, "独家食物", "测试类别", "100.0", null));
        when(foodMapper.selectList(any())).thenReturn(List.of());
        when(userService.allergensOf(1L)).thenReturn(List.of());

        ReplacementResponse resp = service.recommend(1L, 10L, "higher");

        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).suggestions()).isEmpty();
        assertThat(resp.message()).contains("暂无合适的替换建议");
    }

    @Test
    @DisplayName("同类别内按 |delta| 升序取 Top3，并过滤掉用户过敏原")
    void top3SortedAndAllergenFiltered() {
        when(mealService.requireOwned(1L, 10L)).thenReturn(new Meal());
        // 米饭 200g → 原热量 232.0
        when(mealService.itemsOf(10L)).thenReturn(List.of(item(5L, 1L, "200")));
        when(foodMapper.selectById(1L)).thenReturn(food(1L, "米饭", "主食", "116.0", null));
        // 候选中含鸡蛋（应被过滤）与三种主食
        when(foodMapper.selectList(any())).thenReturn(List.of(
                food(2L, "蛋炒饭", "主食", "180.0", "egg"),
                food(3L, "意大利面(煮)", "主食", "158.0", "gluten"),
                food(4L, "花卷", "主食", "217.0", "gluten"),
                food(5L, "馒头", "主食", "223.0", "gluten")));
        when(userService.allergensOf(1L)).thenReturn(List.of("egg"));

        ReplacementResponse resp = service.recommend(1L, 10L, "higher");
        List<SuggestionVO> suggestions = resp.items().get(0).suggestions();

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions).extracting(SuggestionVO::name)
                .containsExactly("意大利面(煮)", "花卷", "馒头");   // delta: 84 < 202 < 214
        assertThat(suggestions).noneMatch(s -> "蛋炒饭".equals(s.name()));
        assertThat(suggestions).allMatch(s -> s.category().equals("主食"));
        assertThat(suggestions).allMatch(s -> s.deltaCalories().signum() > 0);
        assertThat(suggestions.get(0).reason()).contains("同为「主食」").contains("多摄入");
        assertThat(resp.totalDeltaIfAllApplied()).isEqualByComparingTo("84.0");
    }

    @Test
    @DisplayName("lower 策略：只找热量更低的，delta 为负")
    void lowerStrategyProducesNegativeDelta() {
        when(mealService.requireOwned(1L, 10L)).thenReturn(new Meal());
        when(mealService.itemsOf(10L)).thenReturn(List.of(item(5L, 1L, "200")));
        when(foodMapper.selectById(1L)).thenReturn(food(1L, "馒头", "主食", "223.0", null));
        when(foodMapper.selectList(any())).thenReturn(List.of(
                food(1L + 1, "米饭", "主食", "116.0", null)));
        when(userService.allergensOf(anyLong())).thenReturn(List.of());

        ReplacementResponse resp = service.recommend(1L, 10L, "lower");
        List<SuggestionVO> suggestions = resp.items().get(0).suggestions();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).deltaCalories().signum()).isNegative();
        assertThat(suggestions.get(0).name()).isEqualTo("米饭");
    }

    // ---------------------------------------------------------------- helpers

    private MealItem item(long id, long foodId, String qty) {
        MealItem item = new MealItem();
        item.setId(id);
        item.setMealId(10L);
        item.setFoodId(foodId);
        item.setQuantityG(new BigDecimal(qty));
        return item;
    }

    private Food food(long id, String name, String category, String calories, String allergens) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setCategory(category);
        food.setCaloriesPer100g(new BigDecimal(calories));
        food.setProteinPer100g(BigDecimal.ZERO);
        food.setFatPer100g(BigDecimal.ZERO);
        food.setCarbPer100g(BigDecimal.ZERO);
        food.setAllergens(allergens);
        food.setEnabled(true);
        return food;
    }
}
