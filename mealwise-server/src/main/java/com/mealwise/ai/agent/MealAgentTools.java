package com.mealwise.ai.agent;

import com.mealwise.common.api.PageResult;
import com.mealwise.common.enums.MealType;
import com.mealwise.common.exception.BizException;
import com.mealwise.food.service.FoodService;
import com.mealwise.food.vo.FoodVO;
import com.mealwise.meal.dto.AddItemRequest;
import com.mealwise.meal.dto.CreateMealRequest;
import com.mealwise.meal.service.MealService;
import com.mealwise.meal.vo.MealCaloriesVO;
import com.mealwise.meal.vo.MealItemVO;
import com.mealwise.meal.vo.MealVO;
import com.mealwise.recommendation.service.ReplacementService;
import com.mealwise.recommendation.vo.ItemReplacementVO;
import com.mealwise.recommendation.vo.ReplacementResponse;
import com.mealwise.recommendation.vo.SuggestionVO;
import com.mealwise.user.service.UserService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 注册给模型用的 5 个工具。
 *
 * <p>边界：<b>工具内部一律调用现有 Service</b>（{@link MealService} / {@link FoodService} /
 * {@link ReplacementService}），热量与替换规则仍由它们计算；Agent 只决定"先查库、再建餐、再加项、
 * 再算热量、再看替换"这个顺序。
 *
 * <p>{@code userId} 由构造注入、没有对应工具参数，模型无从伪造——归属校验仍在 Service 层做（越权抛 403）。
 * 工具抛出的业务异常会被转成一句 `错误: ...` 交给模型，让它自己决定"再问用户"还是"换个工具"，
 * 而不是把 500 抛到前端。
 */
public class MealAgentTools {

    private static final Logger log = LoggerFactory.getLogger(MealAgentTools.class);
    private static final int SEARCH_LIMIT = 8;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final long userId;
    private final FoodService foodService;
    private final MealService mealService;
    private final ReplacementService replacementService;
    private final UserService userService;

    public MealAgentTools(long userId,
                          FoodService foodService,
                          MealService mealService,
                          ReplacementService replacementService,
                          UserService userService) {
        this.userId = userId;
        this.foodService = foodService;
        this.mealService = mealService;
        this.replacementService = replacementService;
        this.userService = userService;
    }

    @Tool("按关键字搜索食物库里的可用食物，返回 foodId、名称、分类、每 100g 热量。"
            + "用户说要吃什么时，先用它拿到 foodId，再去建餐/加项。关键字为空时列出常见食物。")
    public String searchFood(
            @P("食物关键字，例如 米饭、鸡胸肉、西兰花；可为空") String keyword,
            @P("本次额外需要排除的过敏原，逗号分隔（如 peanut,seafood）；可为空") String extraAllergens) {
        try {
            Set<String> allergens = collectAllergens(extraAllergens);
            PageResult<FoodVO> page = foodService.page(keyword == null ? "" : keyword.trim(), null, 1, 20);
            List<FoodVO> foods = page.records().stream()
                    .filter(food -> food.allergens() == null
                            || food.allergens().stream().noneMatch(allergens::contains))
                    .limit(SEARCH_LIMIT)
                    .toList();

            if (foods.isEmpty()) {
                return "没有找到匹配「" + keyword + "」且不含过敏原的食物。可以换个说法，或让用户先到食物库新增。";
            }
            StringBuilder sb = new StringBuilder("找到 ").append(foods.size()).append(" 种食物：\n");
            for (FoodVO food : foods) {
                sb.append("- foodId=").append(food.id())
                        .append("，").append(food.name())
                        .append("（").append(food.category()).append("）")
                        .append("，每100g ").append(food.caloriesPer100g()).append(" kcal");
                if (food.unitName() != null && food.unitWeightG() != null) {
                    sb.append("，1").append(food.unitName()).append("≈").append(food.unitWeightG()).append("g");
                }
                sb.append('\n');
            }
            return sb.toString();
        } catch (BizException ex) {
            return "错误: " + ex.getMessage();
        } catch (Exception ex) {
            log.warn("tool searchFood failed", ex);
            return "错误: 搜索食物失败";
        }
    }

    @Tool("创建一餐并返回 mealId。餐次类型只能是 BREAKFAST/LUNCH/DINNER/SNACK；"
            + "不知道是哪一餐时先问用户，不要猜。")
    public String createMeal(
            @P("餐次类型：BREAKFAST、LUNCH、DINNER 或 SNACK") String mealType,
            @P("用餐时间，格式 yyyy-MM-dd HH:mm；为空表示现在") String eatenAt) {
        try {
            LocalDateTime time = parseTime(eatenAt);
            MealVO meal = mealService.create(userId, new CreateMealRequest(mealType, time));
            return "已创建餐次 mealId=" + meal.id() + "（" + meal.mealType() + "，" + meal.eatenAt() + "）";
        } catch (BizException ex) {
            return "错误: " + ex.getMessage();
        } catch (Exception ex) {
            log.warn("tool createMeal failed", ex);
            return "错误: 创建餐次失败";
        }
    }

    @Tool("把食物加入指定餐次，返回这一项的热量。需要 foodId（先 searchFood 拿到）和克数；"
            + "用户没说克数时先追问，不要自己编。")
    public String addMealItem(
            @P("餐次 id") long mealId,
            @P("食物 id") long foodId,
            @P("克数，必须大于 0") double grams) {
        try {
            if (grams <= 0) {
                return "错误: 克数必须大于 0，请向用户确认重量";
            }
            MealItemVO item = mealService.addItem(userId, mealId,
                    new AddItemRequest(foodId, BigDecimal.valueOf(grams)));
            return "已加入：" + item.foodName() + " " + item.quantityG() + "g，热量 "
                    + item.calories() + " kcal（蛋白质 " + item.protein() + "g，脂肪 "
                    + item.fat() + "g，碳水 " + item.carb() + "g）";
        } catch (BizException ex) {
            return "错误: " + ex.getMessage();
        } catch (Exception ex) {
            log.warn("tool addMealItem failed", ex);
            return "错误: 加入食物项失败";
        }
    }

    @Tool("计算某餐的总热量与三大营养素。加完食物后用它给出结果，不要自己算。")
    public String calculateCalories(@P("餐次 id") long mealId) {
        try {
            MealCaloriesVO calories = mealService.calories(userId, mealId);
            if (calories.itemCount() == 0) {
                return "这一餐还没有食物项。";
            }
            return "这一餐共 " + calories.itemCount() + " 项、合计 " + calories.totalGrams() + "g："
                    + calories.total().calories() + " kcal，蛋白质 " + calories.total().protein()
                    + "g，脂肪 " + calories.total().fat() + "g，碳水 " + calories.total().carb() + "g。";
        } catch (BizException ex) {
            return "错误: " + ex.getMessage();
        } catch (Exception ex) {
            log.warn("tool calculateCalories failed", ex);
            return "错误: 计算热量失败";
        }
    }

    @Tool("给出某餐的替换建议（规则引擎算好的 Top3，含热量差与理由）。"
            + "strategy=higher 表示换热量更高的候选，lower 表示换更低的（减脂通常用 lower）。")
    public String getReplacements(
            @P("餐次 id") long mealId,
            @P("higher 或 lower") String strategy) {
        try {
            ReplacementResponse response = replacementService.recommend(userId, mealId, strategy);
            if (response.items().isEmpty()) {
                return response.message() == null ? "这一餐没有可替换的食物。" : response.message();
            }
            StringBuilder sb = new StringBuilder();
            for (ItemReplacementVO item : response.items()) {
                sb.append("「").append(item.originalFood().name()).append("」").append(item.quantityG()).append("g");
                List<SuggestionVO> suggestions = item.suggestions();
                if (suggestions.isEmpty()) {
                    sb.append("：没有合适的替换候选\n");
                    continue;
                }
                sb.append("可换成：");
                List<String> parts = new ArrayList<>();
                for (SuggestionVO s : suggestions) {
                    parts.add(s.name() + "（热量差 " + s.deltaCalories() + " kcal，" + s.reason() + "）");
                }
                sb.append(String.join("；", parts)).append('\n');
            }
            return sb.toString().trim();
        } catch (BizException ex) {
            return "错误: " + ex.getMessage();
        } catch (Exception ex) {
            log.warn("tool getReplacements failed", ex);
            return "错误: 获取替换建议失败";
        }
    }

    /** 用户过敏原（库里）× 本次对话额外声明的过敏原。 */
    private Set<String> collectAllergens(String extraAllergens) {
        Set<String> allergens = userService.allergensOf(userId).stream()
                .map(a -> a.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (extraAllergens != null && !extraAllergens.isBlank()) {
            Arrays.stream(extraAllergens.split("[,，\\s]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .forEach(allergens::add);
        }
        return allergens;
    }

    private static LocalDateTime parseTime(String eatenAt) {
        if (eatenAt == null || eatenAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(eatenAt.trim(), TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            throw BizException.badRequest("时间格式应为 yyyy-MM-dd HH:mm");
        }
    }

    /** 让 MealType 的合法值出现在提示里，避免模型编 "午饭"。 */
    static String allowedMealTypes() {
        return Arrays.stream(MealType.values()).map(Enum::name).collect(Collectors.joining("/"));
    }
}
