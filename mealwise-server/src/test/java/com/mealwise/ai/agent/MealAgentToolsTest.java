package com.mealwise.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.mealwise.common.api.PageResult;
import com.mealwise.common.exception.BizException;
import com.mealwise.food.service.FoodService;
import com.mealwise.food.vo.FoodVO;
import com.mealwise.meal.service.MealService;
import com.mealwise.meal.vo.MealCaloriesVO;
import com.mealwise.meal.vo.MealItemVO;
import com.mealwise.meal.vo.MealVO;
import com.mealwise.meal.vo.NutritionVO;
import com.mealwise.recommendation.service.ReplacementService;
import com.mealwise.recommendation.vo.FoodBriefVO;
import com.mealwise.recommendation.vo.ItemReplacementVO;
import com.mealwise.recommendation.vo.ReplacementResponse;
import com.mealwise.recommendation.vo.SuggestionVO;
import com.mealwise.user.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 5 个 @Tool 的行为测试：只验证"工具把现有 Service 的结果整理成模型能读的话"，
 * 不启动模型、不连库。
 */
@ExtendWith(MockitoExtension.class)
class MealAgentToolsTest {

    @Mock
    private FoodService foodService;
    @Mock
    private MealService mealService;
    @Mock
    private ReplacementService replacementService;
    @Mock
    private UserService userService;

    private MealAgentTools tools;

    @BeforeEach
    void setUp() {
        tools = new MealAgentTools(1L, foodService, mealService, replacementService, userService);
    }

    @Test
    @DisplayName("searchFood：按用户过敏原过滤，并给出 foodId")
    void searchFoodFiltersAllergens() {
        when(userService.allergensOf(1L)).thenReturn(List.of("peanut"));
        when(foodService.page(eq("坚果"), any(), eq(1L), anyLong())).thenReturn(PageResult.of(List.of(
                food(1L, "花生", "坚果", "567", List.of("peanut")),
                food(2L, "核桃", "坚果", "646", List.of("nut")),
                food(3L, "巴旦木", "坚果", "578", List.of("nut"))), 3, 1, 20));

        String result = tools.searchFood("坚果", null);

        assertThat(result).contains("foodId=2", "核桃");
        assertThat(result).doesNotContain("花生");
    }

    @Test
    @DisplayName("searchFood：本次对话新提到的过敏原也能排除")
    void searchFoodHonoursExtraAllergens() {
        when(userService.allergensOf(1L)).thenReturn(List.of());
        when(foodService.page(any(), any(), anyLong(), anyLong())).thenReturn(PageResult.of(List.of(
                food(4L, "三文鱼", "肉蛋", "139", List.of("seafood")),
                food(5L, "鸡胸肉", "肉蛋", "133", List.of())), 2, 1, 20));

        String result = tools.searchFood("肉", "seafood");

        assertThat(result).contains("鸡胸肉").doesNotContain("三文鱼");
    }

    @Test
    @DisplayName("createMeal：返回 mealId 供后续工具使用")
    void createMealReturnsId() {
        when(mealService.create(eq(1L), any())).thenReturn(
                new MealVO(42L, "LUNCH", LocalDateTime.of(2026, 1, 1, 12, 0), List.of()));

        String result = tools.createMeal("LUNCH", "2026-01-01 12:00");

        assertThat(result).contains("mealId=42");
    }

    @Test
    @DisplayName("createMeal：时间格式错误 → 返回错误文本而不是抛异常")
    void createMealHandlesBadTime() {
        String result = tools.createMeal("LUNCH", "昨天中午");

        assertThat(result).startsWith("错误:").contains("yyyy-MM-dd HH:mm");
    }

    @Test
    @DisplayName("addMealItem：克数 <= 0 时拒绝，不写库")
    void addMealItemRejectsNonPositive() {
        String result = tools.addMealItem(42L, 1L, 0);

        assertThat(result).startsWith("错误:").contains("克数");
    }

    @Test
    @DisplayName("addMealItem：正常返回这一项的热量")
    void addMealItemFormatsResult() {
        when(mealService.addItem(eq(1L), eq(42L), any())).thenReturn(new MealItemVO(
                7L, 1L, "米饭", new BigDecimal("200.0"),
                new BigDecimal("232.0"), new BigDecimal("5.2"),
                new BigDecimal("0.6"), new BigDecimal("51.8"), 0));

        String result = tools.addMealItem(42L, 1L, 200);

        assertThat(result).contains("米饭", "200", "232.0");
    }

    @Test
    @DisplayName("calculateCalories：汇总热量与三大营养素")
    void calculateCaloriesFormats() {
        when(mealService.calories(1L, 42L)).thenReturn(new MealCaloriesVO(
                42L, "LUNCH", LocalDateTime.now(), List.of(),
                new NutritionVO(new BigDecimal("465.5"), new BigDecimal("46.2"),
                        new BigDecimal("5.0"), new BigDecimal("57.0")),
                3, new BigDecimal("450.0")));

        String result = tools.calculateCalories(42L);

        assertThat(result).contains("465.5", "46.2", "450.0");
    }

    @Test
    @DisplayName("getReplacements：把 Top3 与 delta 原样转述")
    void getReplacementsFormats() {
        when(replacementService.recommend(1L, 42L, "lower")).thenReturn(new ReplacementResponse(
                42L, "lower", List.of(new ItemReplacementVO(
                1L, new FoodBriefVO(1L, "米饭", "主食", new BigDecimal("116.0")),
                new BigDecimal("200.0"), new BigDecimal("232.0"),
                List.of(new SuggestionVO(9L, "蒸红薯", "主食", new BigDecimal("86.0"),
                        new BigDecimal("172.0"), new BigDecimal("-60.0"), "同为「主食」，更低热量")))),
                new BigDecimal("-60.0"), null));

        String result = tools.getReplacements(42L, "lower");

        assertThat(result).contains("米饭", "蒸红薯", "-60.0");
    }

    @Test
    @DisplayName("越权/业务异常：转成错误文本，不抛给模型")
    void businessErrorBecomesText() {
        when(mealService.calories(1L, 99L)).thenThrow(BizException.forbidden("无权访问该餐次"));

        String result = tools.calculateCalories(99L);

        assertThat(result).startsWith("错误:").contains("无权访问");
    }

    private FoodVO food(Long id, String name, String category, String kcal, List<String> allergens) {
        return new FoodVO(id, name, category, new BigDecimal(kcal), BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, "份", new BigDecimal("100.0"), allergens, true);
    }
}
