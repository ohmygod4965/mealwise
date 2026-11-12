package com.mealwise.meal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mealwise.meal.entity.Meal;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MealMapper extends BaseMapper<Meal> {
}
