package com.mealwise.meal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mealwise.meal.entity.MealItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MealItemMapper extends BaseMapper<MealItem> {
}
