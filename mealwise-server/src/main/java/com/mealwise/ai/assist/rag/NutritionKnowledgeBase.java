package com.mealwise.ai.assist.rag;

import com.mealwise.food.entity.Food;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 小型营养知识库：<b>项目食物库 + 少量营养常识</b>，没有外部向量库。
 *
 * <p>一条文档 = 一个可被引用的片段。食物类文档 id 形如 {@code food-12}，
 * 常识类 id 形如 {@code kb-3}；检索结果原样回给前端做来源展示。
 */
public final class NutritionKnowledgeBase {

    /** 一条可检索的资料。 */
    public record Doc(String id, String title, String text, String kind) {
    }

    private NutritionKnowledgeBase() {
    }

    /** 把食物库转成文档；{@code enabled=false} 的不进库。 */
    public static List<Doc> fromFoods(List<Food> foods) {
        List<Doc> docs = new ArrayList<>();
        if (foods == null) {
            return docs;
        }
        for (Food food : foods) {
            if (food == null || Boolean.FALSE.equals(food.getEnabled())) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(food.getName()).append("（").append(food.getCategory()).append("）");
            sb.append("：每 100g 约 ").append(num(food.getCaloriesPer100g())).append(" kcal");
            sb.append("，蛋白质 ").append(num(food.getProteinPer100g())).append(" g");
            sb.append("，脂肪 ").append(num(food.getFatPer100g())).append(" g");
            sb.append("，碳水 ").append(num(food.getCarbPer100g())).append(" g");
            if (food.getUnitName() != null && food.getUnitWeightG() != null) {
                sb.append("；一").append(food.getUnitName()).append("约 ")
                        .append(num(food.getUnitWeightG())).append(" g");
            }
            docs.add(new Doc("food-" + food.getId(), food.getName(), sb.toString(), "food"));
        }
        return docs;
    }

    /** 营养常识片段：数量少但覆盖「减脂/晚餐/蛋白质/主食」这些高频问题。 */
    public static List<Doc> knowledgeDocs() {
        return List.of(
                new Doc("kb-1", "减脂的核心是热量缺口",
                        "减脂期建议每日热量缺口 300–500 kcal，每周体重下降 0.5–1% 比较可持续；缺口过大容易掉肌肉和反弹。", "knowledge"),
                new Doc("kb-2", "蛋白质吃多少",
                        "减脂期蛋白质建议 1.5–2.0 g/kg 体重，优先鸡胸肉、鱼虾、鸡蛋、豆制品等低脂蛋白，有助于保住肌肉和增强饱腹感。", "knowledge"),
                new Doc("kb-3", "晚餐怎么安排",
                        "晚餐建议：一半蔬菜、四分之一优质蛋白、四分之一主食；总量控制在全天热量的 30% 左右，睡前 2–3 小时吃完。", "knowledge"),
                new Doc("kb-4", "主食怎么选",
                        "优先粗细搭配：米饭、全麦面包、玉米、红薯替代部分精制主食；主食不是敌人，控制总量比完全不吃更可持续。", "knowledge"),
                new Doc("kb-5", "蔬菜与饱腹感",
                        "每餐 200–300 g 蔬菜（西兰花、菠菜、生菜等）热量低、体积大，能显著提升饱腹感；烹调少油少盐。", "knowledge"),
                new Doc("kb-6", "进餐顺序与血糖",
                        "先吃蔬菜、再吃蛋白质、最后吃主食，有助于平缓餐后血糖；细嚼慢咽、每餐 20 分钟以上更容易控制食量。", "knowledge"),
                new Doc("kb-7", "加餐与零食",
                        "两餐之间饿可以选酸奶、水果、坚果（一小把）作为加餐；奶茶、含糖饮料是液体热量，减脂期尽量换成无糖茶或水。", "knowledge"));
    }

    private static String num(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
