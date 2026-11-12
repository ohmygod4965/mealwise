package com.mealwise.food.service;

import com.mealwise.common.api.PageResult;
import com.mealwise.food.dto.FoodSaveRequest;
import com.mealwise.food.entity.Food;
import com.mealwise.food.vo.FoodVO;

/**
 * 食物库服务。
 */
public interface FoodService {

    /** 分页查询，默认只返回上架食物。 */
    PageResult<FoodVO> page(String keyword, String category, long page, long size);

    FoodVO create(FoodSaveRequest request);

    FoodVO update(long id, FoodSaveRequest request);

    /** 软删除（enabled=false），历史餐次仍可计算。 */
    void disable(long id);

    /** 取食物，不存在抛 404。 */
    Food requireById(long id);

    /** 取「可用」食物：存在且 enabled=true，否则 404/400。 */
    Food requireEnabled(long id);
}
