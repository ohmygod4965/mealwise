package com.mealwise.recommendation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mealwise.common.enums.ReplacementStrategy;
import com.mealwise.common.util.AllergenCodec;
import com.mealwise.common.util.NutritionCalculator;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import com.mealwise.meal.entity.MealItem;
import com.mealwise.meal.service.MealService;
import com.mealwise.recommendation.service.ReplacementService;
import com.mealwise.recommendation.vo.FoodBriefVO;
import com.mealwise.recommendation.vo.ItemReplacementVO;
import com.mealwise.recommendation.vo.ReplacementResponse;
import com.mealwise.recommendation.vo.SuggestionVO;
import com.mealwise.user.service.UserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 替换推荐实现。
 *
 * <p>算法（逐食物项独立计算）：
 * <pre>
 * 1. 校验策略合法（higher/lower），否则 400
 * 2. 取餐次并校验归属：不存在 404，不属于当前用户 403
 * 3. 取当前用户过敏原集合
 * 4. 对每个食物项：
 *    a. 同 category、enabled=1、id≠原食物
 *    b. higher: caloriesPer100g > 原值；lower: &lt; 原值
 *    c. 过敏原交集为空（food.allergens ∩ user.allergens = ∅）
 *    d. 计算 delta = 替换后热量 - 原热量（按同一克数换算）
 *    e. 按 |delta| 升序（差异温和的排前面），取前 3
 * 5. 汇总：每项取第一条建议的 delta 相加
 * 6. 全部项都没有候选 ⇒ suggestions 空 + message 友好提示（仍是 200）
 * </pre>
 *
 * <p>为什么按 |delta| 升序而不是直接取"热量最高/最低"：
 * 直接取极值等于给用户一张热量排行榜，推荐结果往往变成"换成五花肉"这种不可执行的建议；
 * 按差异温和度排序，用户更容易接受、也更容易解释"为什么是它"。
 */
@Service
public class ReplacementServiceImpl implements ReplacementService {

    private static final Logger log = LoggerFactory.getLogger(ReplacementServiceImpl.class);

    private static final int TOP_N = 3;
    private static final int CANDIDATE_LIMIT = 200;

    private final MealService mealService;
    private final FoodMapper foodMapper;
    private final UserService userService;

    public ReplacementServiceImpl(MealService mealService, FoodMapper foodMapper, UserService userService) {
        this.mealService = mealService;
        this.foodMapper = foodMapper;
        this.userService = userService;
    }

    @Override
    public ReplacementResponse recommend(long userId, long mealId, String strategyRaw) {
        ReplacementStrategy strategy = ReplacementStrategy.parse(strategyRaw);

        // 权限校验放在最前面：无权访问的餐次不应该消耗后续查询
        mealService.requireOwned(userId, mealId);
        List<MealItem> items = mealService.itemsOf(mealId);
        Set<String> userAllergens = new HashSet<>(userService.allergensOf(userId));

        List<ItemReplacementVO> itemResults = new ArrayList<>(items.size());
        BigDecimal totalDelta = NutritionCalculator.zero();
        boolean anySuggestion = false;

        for (MealItem item : items) {
            Food original = foodMapper.selectById(item.getFoodId());
            if (original == null) {
                // 食物被物理删除（正常不会发生，外键 RESTRICT 挡着），跳过而不是让整单失败
                continue;
            }

            BigDecimal quantity = item.getQuantityG();
            BigDecimal originalCalories = NutritionCalculator.scale(original.getCaloriesPer100g(), quantity);

            List<SuggestionVO> suggestions = buildSuggestions(strategy, original, quantity, originalCalories, userAllergens);
            if (!suggestions.isEmpty()) {
                anySuggestion = true;
                totalDelta = NutritionCalculator.plus(totalDelta, suggestions.get(0).deltaCalories());
            }

            itemResults.add(new ItemReplacementVO(
                    item.getId(),
                    new FoodBriefVO(original.getId(), original.getName(), original.getCategory(), original.getCaloriesPer100g()),
                    quantity,
                    originalCalories,
                    suggestions));
        }

        String message = null;
        if (!anySuggestion) {
            message = strategy.isHigher()
                    ? "暂无合适的替换建议（同类目中没有热量更高、且不包含你的过敏原的食物）"
                    : "暂无合适的替换建议（同类目中没有热量更低、且不包含你的过敏原的食物）";
        } else if (itemResults.size() > 0 && countSuggested(itemResults) < itemResults.size()) {
            message = "部分食物没有合适的替换候选，已跳过";
        }

        log.debug("replacement mealId={} strategy={} items={} totalDelta={}",
                mealId, strategy, itemResults.size(), totalDelta);

        return new ReplacementResponse(mealId, strategy.name().toLowerCase(), itemResults, totalDelta, message);
    }

    private List<SuggestionVO> buildSuggestions(ReplacementStrategy strategy,
                                                Food original,
                                                BigDecimal quantity,
                                                BigDecimal originalCalories,
                                                Set<String> userAllergens) {
        LambdaQueryWrapper<Food> query = new LambdaQueryWrapper<Food>()
                .eq(Food::getEnabled, true)
                .eq(Food::getCategory, original.getCategory())
                .ne(Food::getId, original.getId());

        if (strategy.isHigher()) {
            query.gt(Food::getCaloriesPer100g, original.getCaloriesPer100g())
                    .orderByAsc(Food::getCaloriesPer100g);
        } else {
            query.lt(Food::getCaloriesPer100g, original.getCaloriesPer100g())
                    .orderByDesc(Food::getCaloriesPer100g);
        }
        query.last("LIMIT " + CANDIDATE_LIMIT);

        List<Food> candidates = foodMapper.selectList(query);
        List<SuggestionVO> suggestions = new ArrayList<>(TOP_N);

        for (Food candidate : candidates) {
            if (!allergenSafe(candidate, userAllergens)) {
                continue;
            }
            BigDecimal replaced = NutritionCalculator.scale(candidate.getCaloriesPer100g(), quantity);
            BigDecimal delta = replaced.subtract(originalCalories).setScale(1, RoundingMode.HALF_UP);
            suggestions.add(new SuggestionVO(
                    candidate.getId(),
                    candidate.getName(),
                    candidate.getCategory(),
                    candidate.getCaloriesPer100g(),
                    replaced,
                    delta,
                    buildReason(original, candidate, quantity, delta, strategy)));
        }

        // 差异温和优先：|delta| 升序；同 delta 时按 id 稳定排序，保证结果可复现
        suggestions.sort((a, b) -> {
            int byDelta = a.deltaCalories().abs().compareTo(b.deltaCalories().abs());
            return byDelta != 0 ? byDelta : a.foodId().compareTo(b.foodId());
        });

        return suggestions.size() > TOP_N ? new ArrayList<>(suggestions.subList(0, TOP_N)) : suggestions;
    }

    /** 过敏原交集为空才安全；用户没设过敏原时全部通过。 */
    private boolean allergenSafe(Food food, Set<String> userAllergens) {
        if (userAllergens.isEmpty()) {
            return true;
        }
        List<String> foodAllergens = AllergenCodec.decode(food.getAllergens());
        for (String token : foodAllergens) {
            if (userAllergens.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private String buildReason(Food original, Food candidate, BigDecimal quantity,
                               BigDecimal delta, ReplacementStrategy strategy) {
        BigDecimal gapPer100 = candidate.getCaloriesPer100g()
                .subtract(original.getCaloriesPer100g())
                .setScale(1, RoundingMode.HALF_UP);

        String gapText;
        if (strategy.isHigher()) {
            gapText = "每 100g 比「" + original.getName() + "」高 " + gapPer100.abs().toPlainString() + " kcal";
        } else {
            gapText = "每 100g 比「" + original.getName() + "」低 " + gapPer100.abs().toPlainString() + " kcal";
        }

        String deltaText = strategy.isHigher()
                ? "按 " + quantity.stripTrailingZeros().toPlainString() + "g 计，替换后多摄入约 " + delta.abs().toPlainString() + " kcal"
                : "按 " + quantity.stripTrailingZeros().toPlainString() + "g 计，替换后少摄入约 " + delta.abs().toPlainString() + " kcal";

        return "同为「" + original.getCategory() + "」，" + gapText + "；" + deltaText;
    }

    private long countSuggested(List<ItemReplacementVO> items) {
        return items.stream().filter(item -> !item.suggestions().isEmpty()).count();
    }
}
