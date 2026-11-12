package com.mealwise.food.controller;

import com.mealwise.common.api.PageResult;
import com.mealwise.common.api.Result;
import com.mealwise.food.dto.FoodSaveRequest;
import com.mealwise.food.service.FoodService;
import com.mealwise.food.vo.FoodVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 食物库接口。Controller 只做参数接收与结果包装。
 */
@Tag(name = "02. 食物库", description = "食物的增删改查与搜索")
@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    @Operation(summary = "分页查询", description = "支持按名称模糊搜索、按类别精确过滤；默认只返回上架食物")
    @GetMapping
    public Result<PageResult<FoodVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(foodService.page(keyword, category, page, size));
    }

    @Operation(summary = "新增食物")
    @PostMapping
    public Result<FoodVO> create(@Valid @RequestBody FoodSaveRequest request) {
        return Result.ok(foodService.create(request));
    }

    @Operation(summary = "编辑食物", description = "全量更新")
    @PutMapping("/{id}")
    public Result<FoodVO> update(@PathVariable long id, @Valid @RequestBody FoodSaveRequest request) {
        return Result.ok(foodService.update(id, request));
    }

    @Operation(summary = "删除食物", description = "软删除：仅置 enabled=false，历史餐次不受影响")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        foodService.disable(id);
        return Result.ok();
    }
}
