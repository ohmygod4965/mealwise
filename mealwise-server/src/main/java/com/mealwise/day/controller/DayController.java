package com.mealwise.day.controller;

import com.mealwise.common.api.Result;
import com.mealwise.day.service.DayService;
import com.mealwise.day.vo.DayRecordVO;
import com.mealwise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日记录：把一天的早餐/午餐/晚餐聚合成一条记录，并对比建议摄入。
 */
@Tag(name = "05. 日记录", description = "按天查看早/午/晚餐与营养达成情况")
@RestController
@RequestMapping("/api/days")
public class DayController {

    private final DayService dayService;

    public DayController(DayService dayService) {
        this.dayService = dayService;
    }

    @Operation(summary = "今日记录")
    @GetMapping("/today")
    public Result<DayRecordVO> today(@AuthenticationPrincipal UserPrincipal principal) {
        return Result.ok(dayService.getDay(principal.id(), LocalDate.now()));
    }

    @Operation(summary = "指定日期记录", description = "date 格式 yyyy-MM-dd")
    @GetMapping("/{date}")
    public Result<DayRecordVO> byDate(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(dayService.getDay(principal.id(), date));
    }
}
