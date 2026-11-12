package com.mealwise.meal.service;

import com.mealwise.meal.dto.AddItemRequest;
import com.mealwise.meal.dto.CreateMealRequest;
import com.mealwise.meal.dto.UpdateItemRequest;
import com.mealwise.meal.entity.Meal;
import com.mealwise.meal.entity.MealItem;
import com.mealwise.meal.vo.MealCaloriesVO;
import com.mealwise.meal.vo.MealItemVO;
import com.mealwise.meal.vo.MealVO;
import java.util.List;

/**
 * 餐次服务。所有方法第一个参数都是 {@code userId}，越权判定集中在 {@link #requireOwned}。
 */
public interface MealService {

    MealVO create(long userId, CreateMealRequest request);

    MealItemVO addItem(long userId, long mealId, AddItemRequest request);

    MealItemVO updateItem(long userId, long mealId, long itemId, UpdateItemRequest request);

    void deleteItem(long userId, long mealId, long itemId);

    /** 删除整餐（连带食物项由外键级联）；用于"撤销刚才的 AI 入账"。 */
    void delete(long userId, long mealId);

    MealCaloriesVO calories(long userId, long mealId);

    /** 取餐次并校验归属：不存在 404，不属于当前用户 403。 */
    Meal requireOwned(long userId, long mealId);

    /** 按 id 升序返回某餐的全部食物项。 */
    List<MealItem> itemsOf(long mealId);
}
