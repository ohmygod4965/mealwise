package com.mealwise.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealwise.ai.AiProperties;
import com.mealwise.ai.client.NutritionWebClient;
import com.mealwise.ai.dto.AiMealPlanRequest;
import com.mealwise.ai.vo.AiPlanVO;
import com.mealwise.common.enums.MealType;
import com.mealwise.common.util.AllergenCodec;
import com.mealwise.common.util.NutritionCalculator;
import com.mealwise.food.entity.Food;
import com.mealwise.food.mapper.FoodMapper;
import com.mealwise.meal.vo.NutritionVO;
import com.mealwise.nutrition.service.NutritionTargetService;
import com.mealwise.nutrition.vo.NutritionTargetVO;
import com.mealwise.user.entity.SysUser;
import com.mealwise.user.service.UserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 饮食推荐：大模型出方案 → 联网核验热量 → 汇总。
 *
 * <p>三段式链路：
 * <ol>
 *   <li><b>规划</b>：把用户资料、目标、忌口、偏好拼成提示词，让模型返回严格的 JSON 三餐；</li>
 *   <li><b>核验</b>：对每样食物调用公开营养库（Open Food Facts）拿每 100g 热量，命中则覆盖模型估值；</li>
 *   <li><b>兜底</b>：没有密钥 / 调用失败 / JSON 解析失败时，用本地食物库按目标热量规则搭配，标记 source=local。</li>
 * </ol>
 * 任何一个外部依赖挂掉都不应该让页面报错——降级要比失败好。
 */
@Service
public class AiMealPlanService {

    private static final Logger log = LoggerFactory.getLogger(AiMealPlanService.class);
    private static final String DEFAULT_MEAL = "MAINTAIN";

    private final AiProperties properties;
    private final NutritionWebClient webClient;
    private final UserService userService;
    private final NutritionTargetService targetService;
    private final FoodMapper foodMapper;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public AiMealPlanService(AiProperties properties,
                             NutritionWebClient webClient,
                             UserService userService,
                             NutritionTargetService targetService,
                             FoodMapper foodMapper,
                             ObjectMapper mapper) {
        this.properties = properties;
        this.webClient = webClient;
        this.userService = userService;
        this.targetService = targetService;
        this.foodMapper = foodMapper;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public AiPlanVO plan(long userId, AiMealPlanRequest request) {
        SysUser user = userService.requireById(userId);
        List<String> allergens = mergeAllergens(userService.allergensOf(userId), request.excludeAllergens());
        NutritionTargetVO target = targetService.compute(user);
        BigDecimal targetCalories = request.targetCalories() != null
                ? request.targetCalories()
                : (target != null ? target.calories() : new BigDecimal("2000").setScale(1, RoundingMode.HALF_UP));
        List<String> mealTypes = request.mealTypesOrDefault();

        if (!properties.usable()) {
            return localPlan(user, allergens, targetCalories, mealTypes, request,
                    "未配置大模型密钥，已使用本地食物库规则搭配");
        }

        try {
            Draft draft = askModel(user, request, allergens, targetCalories, mealTypes, target);
            return enrich(draft, targetCalories, mealTypes, request, "llm", properties.model());
        } catch (Exception ex) {
            log.warn("AI plan failed, fallback to local rules: {}", ex.toString());
            return localPlan(user, allergens, targetCalories, mealTypes, request,
                    "大模型调用失败（" + brief(ex) + "），已降级为本地食物库规则搭配");
        }
    }

    // ---------------------------------------------------------------- 大模型

    /** 模型返回的草稿结构，营养值为模型估值，后续会被联网结果覆盖。 */
    private record DraftItem(String name, String nameEn, BigDecimal grams,
                             BigDecimal kcalPer100g, BigDecimal proteinPer100g,
                             BigDecimal fatPer100g, BigDecimal carbPer100g) {
    }

    private record DraftMeal(String mealType, List<DraftItem> items) {
    }

    private record Draft(String summary, List<DraftMeal> meals) {
    }

    private Draft askModel(SysUser user, AiMealPlanRequest request, List<String> allergens,
                           BigDecimal targetCalories, List<String> mealTypes, NutritionTargetVO target) throws Exception {
        String system = """
                你是专业营养师。只输出 JSON，不要 markdown、不要解释文字。
                JSON 必须符合：
                {"summary":"一句话方案说明",
                 "meals":[{"mealType":"BREAKFAST|LUNCH|DINNER|SNACK",
                           "items":[{"name":"中文食物名","nameEn":"simple english noun phrase",
                                     "grams":数字,
                                     "kcalPer100g":数字,"proteinPer100g":数字,"fatPer100g":数字,"carbPer100g":数字}]}]}
                营养值统一按每 100g 填写，必须与常识一致（米饭约116、鸡胸约133、西兰花约34、牛奶约60）。
                grams 是建议摄入克数；每餐 3-4 样食物。
                每餐热量必须控制在给定"本餐预算"的 ±10% 以内，全天合计不得超过目标热量 ±10%。
                nameEn 用最简单的英文名词（如 oatmeal / chicken breast / broccoli），不要形容词和菜名。
                严禁出现用户忌口的食物。
                """;

        StringBuilder profile = new StringBuilder();
        profile.append("性别: ").append(user.getGender() == null ? "未知" : user.getGender()).append('\n');
        profile.append("身高cm: ").append(user.getHeightCm()).append('\n');
        profile.append("体重kg: ").append(user.getWeightKg()).append('\n');
        profile.append("年龄: ").append(user.getAge() == null ? 30 : user.getAge()).append('\n');
        profile.append("目标: ").append(goalText(request.goalOrDefault())).append('\n');
        profile.append("每日目标热量: ").append(targetCalories).append(" kcal\n");
        if (target != null) {
            profile.append("参考宏量: 蛋白质 ").append(target.protein()).append("g, 脂肪 ")
                    .append(target.fat()).append("g, 碳水 ").append(target.carb()).append("g\n");
        }
        profile.append("需要安排的餐次与预算:\n");
        // 早餐 30% / 午餐 40% / 晚餐 30%，剩余按餐数均分
        BigDecimal[] weights = mealBudgetWeights(mealTypes);
        for (int i = 0; i < mealTypes.size(); i++) {
            BigDecimal budget = targetCalories.multiply(weights[i]).setScale(0, RoundingMode.HALF_UP);
            profile.append("  ").append(mealTypes.get(i)).append(" 本餐预算 ")
                    .append(budget).append(" kcal\n");
        }
        if (!allergens.isEmpty()) {
            profile.append("必须排除的过敏原食物: ").append(String.join(",", allergens)).append('\n');
        }
        if (request.preferences() != null && !request.preferences().isBlank()) {
            profile.append("口味偏好: ").append(request.preferences().trim()).append('\n');
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.model());
        payload.put("temperature", 0.7);
        payload.put("max_tokens", 1600);
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", profile.toString())));

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(trimSlash(properties.baseUrl()) + "/chat/completions"))
                .timeout(Duration.ofMillis(properties.timeoutMs()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("模型返回 HTTP " + response.statusCode());
        }

        String content = mapper.readTree(response.body())
                .path("choices").path(0).path("message").path("content").asText("");
        JsonNode root = mapper.readTree(stripFence(content));

        List<DraftMeal> meals = new ArrayList<>();
        for (JsonNode mealNode : root.path("meals")) {
            String type = MealType.parse(mealNode.path("mealType").asText("LUNCH")).name();
            List<DraftItem> items = new ArrayList<>();
            for (JsonNode item : mealNode.path("items")) {
                BigDecimal grams = decimal(item.path("grams"), new BigDecimal("100"));
                if (grams.signum() <= 0) {
                    continue;
                }
                items.add(new DraftItem(
                        item.path("name").asText("食物"),
                        item.path("nameEn").asText(item.path("name").asText("")),
                        grams.setScale(0, RoundingMode.HALF_UP),
                        decimal(item.path("kcalPer100g"), BigDecimal.ZERO),
                        decimal(item.path("proteinPer100g"), BigDecimal.ZERO),
                        decimal(item.path("fatPer100g"), BigDecimal.ZERO),
                        decimal(item.path("carbPer100g"), BigDecimal.ZERO)));
            }
            if (!items.isEmpty()) {
                meals.add(new DraftMeal(type, items));
            }
        }
        if (meals.isEmpty()) {
            throw new IllegalStateException("模型未返回有效餐次");
        }
        return new Draft(root.path("summary").asText(""), meals);
    }

    // ---------------------------------------------------------------- 核验 + 汇总

    private AiPlanVO enrich(Draft draft, BigDecimal targetCalories, List<String> mealTypes,
                            AiMealPlanRequest request, String source, String model) {
        int checked = 0;
        int matched = 0;
        int webMatched = 0;
        int dbMatched = 0;
        List<AiPlanVO.MealPlan> meals = new ArrayList<>();
        BigDecimal sumCal = NutritionCalculator.zero();
        BigDecimal sumPro = NutritionCalculator.zero();
        BigDecimal sumFat = NutritionCalculator.zero();
        BigDecimal sumCarb = NutritionCalculator.zero();

        List<Food> localFoods = foodMapper.selectList(new LambdaQueryWrapper<Food>().eq(Food::getEnabled, true));
        BigDecimal[] weights = mealBudgetWeights(mealTypes);
        Map<String, BigDecimal> budgets = new LinkedHashMap<>();
        for (int i = 0; i < mealTypes.size(); i++) {
            budgets.put(mealTypes.get(i), targetCalories.multiply(weights[i]).setScale(0, RoundingMode.HALF_UP));
        }

        // 先并发发起所有联网核验，避免串行等待
        Map<DraftItem, CompletableFuture<Optional<NutritionWebClient.Nutrition>>> futures = new LinkedHashMap<>();
        for (DraftMeal meal : draft.meals()) {
            for (DraftItem item : meal.items()) {
                futures.put(item, webClient.lookupAsync(item.nameEn(), item.name()));
            }
        }

        for (DraftMeal meal : draft.meals()) {
            checked += meal.items().size();

            // 第一遍：确定营养来源（本地食物库 > 联网 > 模型估值）
            List<Resolved> resolved = new ArrayList<>();
            for (DraftItem item : meal.items()) {
                Optional<Food> local = findLocalFood(localFoods, item.name());
                if (local.isPresent()) {
                    Food food = local.get();
                    resolved.add(new Resolved(item, food.getCaloriesPer100g(), food.getProteinPer100g(),
                            food.getFatPer100g(), food.getCarbPer100g(), true, "food-db", ""));
                    matched++;
                    dbMatched++;
                    continue;
                }
                Optional<NutritionWebClient.Nutrition> found = futures.get(item).join();
                // 双源一致性闸门：公开库常有同名不同物的错配（「小白菜」命中薯片 489 kcal），
                // 与模型估值偏差超过 60% 时判定为错配，宁可用估值也不用错数据。
                if (found.isPresent() && plausible(found.get().kcalPer100g(), item.kcalPer100g())) {
                    NutritionWebClient.Nutrition nutrition = found.get();
                    resolved.add(new Resolved(item, nutrition.kcalPer100g(), nutrition.proteinPer100g(),
                            nutrition.fatPer100g(), nutrition.carbPer100g(), true, "web", nutrition.url()));
                    matched++;
                    webMatched++;
                } else {
                    resolved.add(new Resolved(item, item.kcalPer100g(), item.proteinPer100g(),
                            item.fatPer100g(), item.carbPer100g(), false, "estimate", ""));
                }
            }

            // 第二遍：按本餐预算缩放克数——模型只负责"吃什么"，热量由后端拉回预算
            BigDecimal rawCal = NutritionCalculator.zero();
            for (Resolved r : resolved) {
                rawCal = NutritionCalculator.plus(rawCal, NutritionCalculator.scale(r.kcal100(), r.item().grams()));
            }
            BigDecimal budget = budgets.getOrDefault(meal.mealType(), BigDecimal.ZERO);
            BigDecimal factor = (rawCal.signum() > 0 && budget.signum() > 0)
                    ? budget.divide(rawCal, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ONE;

            List<AiPlanVO.PlanItem> planItems = new ArrayList<>();
            BigDecimal mealCal = NutritionCalculator.zero();
            BigDecimal mealPro = NutritionCalculator.zero();
            BigDecimal mealFat = NutritionCalculator.zero();
            BigDecimal mealCarb = NutritionCalculator.zero();

            for (Resolved r : resolved) {
                BigDecimal grams = r.item().grams().multiply(factor).setScale(0, RoundingMode.HALF_UP);
                if (grams.compareTo(BigDecimal.ONE) < 0) {
                    grams = BigDecimal.ONE;
                }
                BigDecimal calories = NutritionCalculator.scale(r.kcal100(), grams);
                BigDecimal protein = NutritionCalculator.scale(r.pro100(), grams);
                BigDecimal fat = NutritionCalculator.scale(r.fat100(), grams);
                BigDecimal carb = NutritionCalculator.scale(r.carb100(), grams);

                planItems.add(new AiPlanVO.PlanItem(r.item().name(), r.item().nameEn(), grams,
                        r.kcal100(), calories, protein, fat, carb, r.verified(), r.verifySource(), r.url()));

                mealCal = NutritionCalculator.plus(mealCal, calories);
                mealPro = NutritionCalculator.plus(mealPro, protein);
                mealFat = NutritionCalculator.plus(mealFat, fat);
                mealCarb = NutritionCalculator.plus(mealCarb, carb);
            }

            meals.add(new AiPlanVO.MealPlan(meal.mealType(), planItems,
                    new NutritionVO(mealCal, mealPro, mealFat, mealCarb)));

            sumCal = NutritionCalculator.plus(sumCal, mealCal);
            sumPro = NutritionCalculator.plus(sumPro, mealPro);
            sumFat = NutritionCalculator.plus(sumFat, mealFat);
            sumCarb = NutritionCalculator.plus(sumCarb, mealCarb);
        }
        meals.sort(Comparator.comparingInt(m -> mealTypes.indexOf(m.mealType())));

        List<String> notes = new ArrayList<>();
        if (matched > 0) {
            notes.add("已核验 " + matched + " 项（本地食物库 " + dbMatched + " 项、联网 " + webMatched + " 项）");
        }
        if (matched < checked) {
            notes.add("其余 " + (checked - matched) + " 项为模型估算值");
        }
        BigDecimal deviation = sumCal.subtract(targetCalories)
                .divide(targetCalories, 4, RoundingMode.HALF_UP).abs();
        if (deviation.compareTo(new BigDecimal("0.15")) > 0) {
            notes.add("总热量与目标偏差超过 15%，可调整偏好后重新生成");
        }

        return new AiPlanVO(request.goalOrDefault(), targetCalories, source, model,
                draft.summary(), meals,
                new NutritionVO(sumCal, sumPro, sumFat, sumCarb),
                new AiPlanVO.Verification(checked, matched), notes);
    }

    /**
     * 联网值与模型估值是否量级一致。
     *
     * <p>公开营养库没有中文别名映射，模糊搜索容易命中"同名不同物"的商品；
     * 模型估值虽然不精确，但同一种食物的量级通常不会差 60% 以上。
     * 两个独立来源互相印证，比单独信任任何一个都可靠。
     */
    private boolean plausible(BigDecimal webKcal, BigDecimal modelKcal) {
        if (webKcal == null || modelKcal == null || modelKcal.signum() <= 0) {
            return true; // 模型没给估值时无从比对，放行
        }
        BigDecimal gap = webKcal.subtract(modelKcal).abs()
                .divide(modelKcal, 4, RoundingMode.HALF_UP);
        return gap.compareTo(new BigDecimal("0.60")) <= 0;
    }
    /** 核验后的食物（克数尚未按预算缩放）。 */
    private record Resolved(DraftItem item, BigDecimal kcal100, BigDecimal pro100,
                            BigDecimal fat100, BigDecimal carb100, boolean verified,
                            String verifySource, String url) {
    }

    /**
     * 本地食物库匹配：食物名互相包含即视为同一种（「糙米饭」命中「米饭」）。
     *
     * <p>命中时优先用本地库的营养值——它是人工维护的中文数据，比众包英文库更准。
     */
    private Optional<Food> findLocalFood(List<Food> foods, String name) {
        if (name == null || name.isBlank() || foods == null) {
            return Optional.empty();
        }
        String target = name.trim();
        Food best = null;
        for (Food food : foods) {
            if (food.getName() == null) {
                continue;
            }
            String foodName = food.getName().trim();
            if (foodName.isEmpty()) {
                continue;
            }
            if (target.equals(foodName) || target.contains(foodName) || foodName.contains(target)) {
                if (best == null || foodName.length() > best.getName().trim().length()) {
                    best = food;
                }
            }
        }
        return Optional.ofNullable(best);
    }
    // ---------------------------------------------------------------- 本地兜底

    private AiPlanVO localPlan(SysUser user, List<String> allergens, BigDecimal targetCalories,
                               List<String> mealTypes, AiMealPlanRequest request, String note) {
        List<Food> foods = foodMapper.selectList(new LambdaQueryWrapper<Food>().eq(Food::getEnabled, true));
        List<Food> usable = foods.stream()
                .filter(f -> !intersects(AllergenCodec.decode(f.getAllergens()), allergens))
                .toList();

        Map<String, List<Food>> byCategory = usable.stream()
                .collect(Collectors.groupingBy(f -> f.getCategory() == null ? "其他" : f.getCategory()));

        List<String> preferred = List.of("主食", "肉蛋", "蔬菜", "水果", "乳制品");
        BigDecimal perMeal = targetCalories.divide(new BigDecimal(mealTypes.size()), 1, RoundingMode.HALF_UP);

        List<AiPlanVO.MealPlan> meals = new ArrayList<>();
        BigDecimal sumCal = NutritionCalculator.zero();
        BigDecimal sumPro = NutritionCalculator.zero();
        BigDecimal sumFat = NutritionCalculator.zero();
        BigDecimal sumCarb = NutritionCalculator.zero();
        int checked = 0;
        int matched = 0;

        for (String mealType : mealTypes) {
            List<Food> picked = new ArrayList<>();
            for (String category : preferred) {
                byCategory.getOrDefault(category, List.of()).stream()
                        .findFirst().ifPresent(picked::add);
                if (picked.size() >= 3) {
                    break;
                }
            }
            if (picked.isEmpty()) {
                picked.addAll(usable.stream().limit(3).toList());
            }
            if (picked.isEmpty()) {
                continue;
            }

            List<AiPlanVO.PlanItem> items = new ArrayList<>();
            BigDecimal share = perMeal.divide(new BigDecimal(picked.size()), 1, RoundingMode.HALF_UP);
            BigDecimal mealCal = NutritionCalculator.zero();
            BigDecimal mealPro = NutritionCalculator.zero();
            BigDecimal mealFat = NutritionCalculator.zero();
            BigDecimal mealCarb = NutritionCalculator.zero();

            for (Food food : picked) {
                checked++;
                BigDecimal kcal100 = food.getCaloriesPer100g();
                BigDecimal grams = kcal100.signum() == 0
                        ? new BigDecimal("100")
                        : share.multiply(new BigDecimal("100")).divide(kcal100, 0, RoundingMode.HALF_UP);
                if (grams.signum() <= 0) {
                    grams = new BigDecimal("100");
                }

                BigDecimal calories = NutritionCalculator.scale(kcal100, grams);
                BigDecimal protein = NutritionCalculator.scale(food.getProteinPer100g(), grams);
                BigDecimal fat = NutritionCalculator.scale(food.getFatPer100g(), grams);
                BigDecimal carb = NutritionCalculator.scale(food.getCarbPer100g(), grams);

                items.add(new AiPlanVO.PlanItem(food.getName(), "", grams, kcal100,
                        calories, protein, fat, carb, true, "food-db", ""));
                matched++;

                mealCal = NutritionCalculator.plus(mealCal, calories);
                mealPro = NutritionCalculator.plus(mealPro, protein);
                mealFat = NutritionCalculator.plus(mealFat, fat);
                mealCarb = NutritionCalculator.plus(mealCarb, carb);
            }

            meals.add(new AiPlanVO.MealPlan(mealType, items, new NutritionVO(mealCal, mealPro, mealFat, mealCarb)));
            sumCal = NutritionCalculator.plus(sumCal, mealCal);
            sumPro = NutritionCalculator.plus(sumPro, mealPro);
            sumFat = NutritionCalculator.plus(sumFat, mealFat);
            sumCarb = NutritionCalculator.plus(sumCarb, mealCarb);
        }

        List<String> notes = new ArrayList<>();
        notes.add(note);
        notes.add("本地规则搭配：按目标热量从已上架食物中按类别各取一样，仅作演示兜底");

        return new AiPlanVO(request.goalOrDefault(), targetCalories, "local", null,
                "按 " + goalText(request.goalOrDefault()) + " 目标，从本地食物库为你搭配 " + meals.size() + " 餐",
                meals, new NutritionVO(sumCal, sumPro, sumFat, sumCarb),
                new AiPlanVO.Verification(checked, matched), notes);
    }

    // ---------------------------------------------------------------- 工具

    private List<String> mergeAllergens(List<String> userAllergens, List<String> extra) {
        List<String> merged = new ArrayList<>(userAllergens == null ? List.of() : userAllergens);
        if (extra != null) {
            for (String token : extra) {
                if (token == null || token.isBlank()) {
                    continue;
                }
                String normalized = token.trim().toLowerCase();
                if (!merged.contains(normalized)) {
                    merged.add(normalized);
                }
            }
        }
        return merged;
    }

    private boolean intersects(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        for (String token : a) {
            if (b.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String goalText(String goal) {
        return switch (goal == null ? DEFAULT_MEAL : goal) {
            case "FAT_LOSS" -> "减脂";
            case "MUSCLE_GAIN" -> "增肌";
            default -> "维持体重";
        };
    }

    /**
     * 每餐热量预算权重：早 30% / 午 40% / 晚 30% / 加餐 10%，再按实际餐数归一化。
     *
     * <p>把预算显式告诉模型，比只说"总共 2267 kcal"有效得多——否则模型容易一顿写 2000 kcal。
     */
    private BigDecimal[] mealBudgetWeights(List<String> mealTypes) {
        Map<String, BigDecimal> preset = Map.of(
                "BREAKFAST", new BigDecimal("0.30"),
                "LUNCH", new BigDecimal("0.40"),
                "DINNER", new BigDecimal("0.30"),
                "SNACK", new BigDecimal("0.10"));
        BigDecimal[] weights = new BigDecimal[mealTypes.size()];
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < mealTypes.size(); i++) {
            BigDecimal weight = preset.getOrDefault(mealTypes.get(i), BigDecimal.ONE);
            weights[i] = weight;
            sum = sum.add(weight);
        }
        if (sum.signum() == 0) {
            sum = BigDecimal.ONE;
        }
        for (int i = 0; i < weights.length; i++) {
            weights[i] = weights[i].divide(sum, 4, RoundingMode.HALF_UP);
        }
        return weights;
    }

    private String brief(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String stripFence(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            int firstBreak = text.indexOf('\n');
            if (firstBreak > 0) {
                text = text.substring(firstBreak + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return text;
    }

    private BigDecimal decimal(JsonNode node, BigDecimal fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            return BigDecimal.valueOf(node.asDouble());
        }
        try {
            return new BigDecimal(node.asText().replaceAll("[^0-9.]", ""));
        } catch (Exception ex) {
            return fallback;
        }
    }
}
