# Apifox 集合执行报告

- 执行时间：2026/11/8 13:00:43
- 目标服务：http://127.0.0.1:8080
- 结果：**21 passed / 0 failed**（共 21 个请求）

| # | 请求 | 方法 | 路径 | 期望 | 实际 | 结论 |
|---|---|---|---|---|---|---|
| 1 | OpenAPI 可访问 | GET | `/v3/api-docs` | 200 | 200 | ✅ |
| 2 | 登录（保存 token） | POST | `/api/auth/login` | 200 | 200 | ✅ |
| 3 | 注册（重复应 409） | POST | `/api/auth/register` | 200/409 | 409 | ✅ |
| 4 | 当前用户 | GET | `/api/users/me` | 200 | 200 | ✅ |
| 5 | 更新身体资料 | PUT | `/api/users/me/profile` | 200 | 200 | ✅ |
| 6 | 食物分页（存 foodId） | GET | `/api/foods?page=1&size=5` | 200 | 200 | ✅ |
| 7 | 搜索米饭 | GET | `/api/foods?keyword=%E7%B1%B3%E9%A5%AD` | 200 | 200 | ✅ |
| 8 | 新增食物 | POST | `/api/foods` | 200 | 200 | ✅ |
| 9 | 删除不存在食物 | DELETE | `/api/foods/999999` | 404 | 404 | ✅ |
| 10 | 创建餐次（存 mealId） | POST | `/api/meals` | 200 | 200 | ✅ |
| 11 | 添加食物项（存 itemId） | POST | `/api/meals/18/items` | 200 | 200 | ✅ |
| 12 | 热量计算 | GET | `/api/meals/18/calories` | 200 | 200 | ✅ |
| 13 | 修改克数 | PUT | `/api/meals/18/items/25` | 200 | 200 | ✅ |
| 14 | 建议 higher | GET | `/api/meals/18/replacements?strategy=higher` | 200 | 200 | ✅ |
| 15 | 建议 lower | GET | `/api/meals/18/replacements?strategy=lower` | 200 | 200 | ✅ |
| 16 | 删除食物项 | DELETE | `/api/meals/18/items/25` | 200 | 200 | ✅ |
| 17 | 今日记录 | GET | `/api/days/today` | 200 | 200 | ✅ |
| 18 | 指定空日期 | GET | `/api/days/2020-01-01` | 200 | 200 | ✅ |
| 19 | 未登录 → 401 | GET | `/api/meals/18/calories` | 401 | 401 | ✅ |
| 20 | 非法策略 → 400 | GET | `/api/meals/18/replacements?strategy=whatever` | 400 | 400 | ✅ |
| 21 | 克数为 0 → 400 | POST | `/api/meals/18/items` | 400 | 400 | ✅ |

> 对照方式：在 Apifox 里按同一顺序发送，状态码与「实际」列一致即通过。

> 注意：「新增食物」「创建餐次」「添加食物项」会写库；重复执行会累积测试数据。
