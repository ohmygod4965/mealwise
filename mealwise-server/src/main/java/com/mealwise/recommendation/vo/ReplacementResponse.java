package com.mealwise.recommendation.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 整餐替换建议响应。
 *
 * @param totalDeltaIfAllApplied 若每项都采纳「第一条」建议，整餐合计变化
 * @param message                无候选时的友好提示；有候选时为 null
 */
public record ReplacementResponse(
        Long mealId,
        String strategy,
        List<ItemReplacementVO> items,
        BigDecimal totalDeltaIfAllApplied,
        String message
) {
}
