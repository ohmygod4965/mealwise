package com.mealwise.recommendation.service;

import com.mealwise.recommendation.vo.ReplacementResponse;

/**
 * 替换推荐服务（只读，不改任何数据）。
 */
public interface ReplacementService {

    /**
     * 为一餐生成替换建议。
     *
     * @param userId   当前登录用户（用于权限校验与过敏原过滤）
     * @param mealId   餐次 id
     * @param strategy higher / lower，其余值抛 400
     */
    ReplacementResponse recommend(long userId, long mealId, String strategy);
}
