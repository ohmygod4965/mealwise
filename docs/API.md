# Mealwise 接口文档

> 版本 v1.0 · 对应后端 `mealwise-server` 1.0.0
> 在线调试：启动后打开 **http://localhost:8080/doc.html**（Knife4j）或 `/swagger-ui.html`
> 机器可读：**http://localhost:8080/v3/api-docs**

---

## 1. 公共约定

### 1.1 基础信息

| 项 | 值 |
|---|---|
| Base URL | `http://<host>:8080` |
| 业务前缀 | `/api` |
| 请求/响应 | `application/json; charset=UTF-8` |
| 时间格式 | `yyyy-MM-dd HH:mm:ss`（时区 `Asia/Shanghai`） |
| 日期参数 | `yyyy-MM-dd` |
| 数值精度 | 热量/营养保留 **1 位小数**（`BigDecimal`，HALF_UP） |

### 1.2 认证

除 `POST /api/auth/**` 与静态资源外，所有接口都需要：

```
Authorization: Bearer <token>
```

- token 由 `POST /api/auth/login` 返回，HS256 签名，有效期 24 小时（响应里的 `expiresIn` 秒数）
- token 缺失/过期/签名错误 → `401`
- 已登录但访问他人资源 → `403`

### 1.3 统一响应体

```json
{
  "code": 200,
  "message": "ok",
  "data": { },
  "traceId": "abf0f9f9254a4362a69bd5a94b4ee46c"
}
```

| 字段 | 说明 |
|---|---|
| `code` | 与 HTTP 状态码一致（**不搞 200 里塞错误**） |
| `message` | 可直接展示给用户的中文提示 |
| `data` | 业务数据；无数据或出错时省略 |
| `traceId` | 链路 id，同时出现在响应头 `X-Trace-Id`，排查时用它 grep 日志 |

### 1.4 错误码

| code | HTTP | 场景 | 示例 message |
|---|---|---|---|
| 200 | 200 | 成功（含"无候选"这类正常空结果） | `ok` |
| 400 | 400 | 参数校验失败、策略非法、克数 ≤ 0、枚举非法 | `克数必须大于 0` |
| 401 | 401 | 未登录 / token 失效 / 用户名密码错误 | `未登录或登录已过期` |
| 403 | 403 | 已登录但访问他人餐次 | `无权访问该餐次` |
| 404 | 404 | 食物 / 餐次 / 食物项不存在 | `餐次不存在` |
| 409 | 409 | 用户名已占用、重复禁用食物、**乐观锁版本冲突** | `用户名已被占用` / `该食物项已被修改，请刷新后重试` |
| 429 | 429 | **AI 接口限流**（每用户 20 次/分钟）或全局日 token 额度用完 | `AI 调用太频繁（每分钟最多 20 次），请稍后再试` |
| 500 | 500 | 未预期异常（对外只给通用文案） | `系统繁忙，请稍后重试` |

### 1.5 分页

请求：`?page=1&size=10`（`size` 上限 100，超出按 100 处理）

```json
{
  "records": [],
  "total": 46,
  "page": 1,
  "size": 10
}
```

---

## 2. 认证

### 2.1 注册

`POST /api/auth/register`

| 参数 | 类型 | 必填 | 规则 |
|---|---|---|---|
| username | string | 是 | 4-50 位字母/数字/下划线，全局唯一 |
| password | string | 是 | 8-32 位，必须同时含字母和数字 |
| allergens | string[] | 否 | 小写 token，如 `["egg","dairy"]` |

```json
// 请求
{ "username": "demo", "password": "Demo@123", "allergens": ["egg"] }

// 响应 data
{ "id": 1, "username": "demo", "allergens": ["egg"], "profileComplete": false }
```

错误：`409 用户名已被占用` · `400 用户名需为 4-50 位字母、数字或下划线`

### 2.2 登录

`POST /api/auth/login`

```json
// 请求
{ "username": "demo", "password": "Demo@123" }

// 响应 data
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1, "username": "demo", "allergens": [],
    "gender": "MALE", "heightCm": 175.0, "weightKg": 70.0, "age": 30,
    "profileComplete": true
  }
}
```

错误：`401 用户名或密码错误`（**不区分**用户名不存在与密码错误，防用户名枚举）

---

## 3. 食物库

### 3.1 分页查询

`GET /api/foods?keyword=&category=&page=1&size=10`

| 参数 | 说明 |
|---|---|
| keyword | 名称模糊匹配（可空） |
| category | 类别精确匹配（可空） |
| page / size | 分页，默认 1 / 10 |

只返回 `enabled=true` 的食物。

```json
// 响应 data
{
  "records": [
    {
      "id": 1, "name": "米饭", "category": "主食",
      "caloriesPer100g": 116.0, "proteinPer100g": 2.6,
      "fatPer100g": 0.3, "carbPer100g": 25.9,
      "unitName": "碗", "unitWeightG": 200.0,
      "allergens": [], "enabled": true
    }
  ],
  "total": 46, "page": 1, "size": 10
}
```

### 3.2 新增

`POST /api/foods`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 最长 64 |
| category | string | 是 | 最长 32，如 主食/肉蛋/蔬菜 |
| caloriesPer100g | number | 是 | ≥ 0，最多 6 位整数 1 位小数 |
| proteinPer100g | number | 是 | ≥ 0 |
| fatPer100g | number | 是 | ≥ 0 |
| carbPer100g | number | 是 | ≥ 0 |
| unitName | string | 否 | 如「碗」「个」 |
| unitWeightG | number | 否 | 1 单位≈多少克，> 0 |
| allergens | string[] | 否 | 过敏原 token |
| enabled | boolean | 否 | 默认 true |

响应 `data` 为创建后的 `FoodVO`。

### 3.3 编辑

`PUT /api/foods/{id}` —— **全量更新**，字段同 3.2。`404 食物不存在`。

### 3.4 删除（软删除）

`DELETE /api/foods/{id}`

把 `enabled` 置为 `false`：列表与推荐候选不再出现，但**历史餐次仍能正常计算**。
再次删除同一食物 → `409 该食物已处于禁用状态`。

---

## 4. 餐次与食物项

> 所有接口的 `userId` 只从 token 取；请求体里即使传了也会被忽略。

### 4.1 创建餐次

`POST /api/meals`

```json
// 请求
{ "mealType": "LUNCH", "eatenAt": "2026-11-07 12:30:00" }

// 响应 data
{ "id": 12, "mealType": "LUNCH", "eatenAt": "2026-11-07 12:30:00", "items": [] }
```

- `mealType`：`BREAKFAST | LUNCH | DINNER | SNACK`
- `eatenAt` 省略则取服务器当前时间

### 4.2 添加食物项

`POST /api/meals/{mealId}/items`

```json
// 请求
{ "foodId": 1, "quantityG": 200.0 }

// 响应 data
{
  "itemId": 33, "foodId": 1, "foodName": "米饭", "quantityG": 200.0,
  "calories": 232.0, "protein": 5.2, "fat": 0.6, "carb": 51.8
}
```

错误：`400 克数必须大于 0` · `404 食物不存在` · `404 餐次不存在` · `403 无权访问该餐次` · `400 该食物已下架`

### 4.3 修改克数

`PUT /api/meals/{mealId}/items/{itemId}`

```json
{ "quantityG": 150.0, "version": 3 }
```

| 字段 | 必填 | 说明 |
|---|---|---|
| quantityG | 是 | > 0 |
| version | 否 | 读取时的版本号（各查询接口都会返回）。带上可精确检测并发冲突；不带则退化为"读后条件更新"，冲突时报错更晚 |

只允许改克数；换食物等价于"删旧加新"。

并发冲突（版本已被推进）→ `409 该食物项已被修改，请刷新后重试`。
响应里的 `version` 会 +1，下次更新请用新值。

### 4.4 删除食物项

`DELETE /api/meals/{mealId}/items/{itemId}` → `data: null`

食物项必须属于路径上的餐次，否则 `404 食物项不存在`。

### 4.5 热量计算

`GET /api/meals/{mealId}/calories`

```json
{
  "mealId": 12, "mealType": "LUNCH", "eatenAt": "2026-11-07 12:30:00",
  "items": [
    { "itemId": 33, "foodId": 1, "foodName": "米饭", "quantityG": 200.0,
      "calories": 232.0, "protein": 5.2, "fat": 0.6, "carb": 51.8 }
  ],
  "total": { "calories": 465.5, "protein": 46.2, "fat": 5.0, "carb": 57.0 },
  "itemCount": 3,
  "totalGrams": 450.0
}
```

### 4.6 替换建议

`GET /api/meals/{mealId}/replacements?strategy=higher`

| 参数 | 取值 | 默认 |
|---|---|---|
| strategy | `higher`（找热量更高）/ `lower`（找更低） | `higher` |

```json
{
  "mealId": 12,
  "strategy": "higher",
  "items": [
    {
      "itemId": 33,
      "originalFood": { "id": 1, "name": "米饭", "category": "主食", "caloriesPer100g": 116.0 },
      "quantityG": 200.0,
      "originalCalories": 232.0,
      "suggestions": [
        {
          "foodId": 11, "name": "意大利面(煮)", "category": "主食",
          "caloriesPer100g": 158.0,
          "replacedCalories": 316.0,
          "deltaCalories": 84.0,
          "reason": "同为「主食」，每 100g 比「米饭」高 42.0 kcal；按 200g 计，替换后多摄入约 84.0 kcal"
        }
      ]
    }
  ],
  "totalDeltaIfAllApplied": 98.0,
  "message": null
}
```

规则：

- 同 `category`、`enabled=true`、热量严格更高/更低、**过敏原与当前用户不冲突**
- 每项最多 **3 条**，按 `|deltaCalories|` 升序（差异温和优先）
- **无候选不是错误**：`suggestions: []` + `message` 有值，HTTP 仍 200
- 非法 strategy → `400 替换策略非法，仅支持 higher 或 lower`

---

## 5. 用户资料

### 5.1 查询当前用户

`GET /api/users/me`

```json
{
  "id": 1, "username": "demo", "allergens": ["egg"],
  "gender": "MALE", "heightCm": 175.0, "weightKg": 70.0, "age": 30,
  "profileComplete": true
}
```

> `profileComplete=false` 时，`/api/days/*` 的 `targets` 与 `comparison` 均为 `null`。

### 5.2 更新身体资料

`PUT /api/users/me/profile`

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| gender | string | 是 | `MALE` / `FEMALE` |
| heightCm | number | 是 | 100 - 250 |
| weightKg | number | 是 | 30 - 300 |
| age | integer | 否 | 10 - 100，缺省按 30 岁估算 |

```json
{ "gender": "MALE", "heightCm": 175, "weightKg": 70, "age": 30 }
```

错误：`400 身高需在 100-250 cm 之间` · `400 体重需在 30-300 kg 之间`

---

## 6. 日记录

### 6.1 今日

`GET /api/days/today`

### 6.2 指定日期

`GET /api/days/{date}`，`date` 形如 `2026-11-07`

```json
{
  "date": "2026-11-07",
  "meals": [
    {
      "mealId": 12, "mealType": "BREAKFAST", "eatenAt": "2026-11-07 08:10:00",
      "items": [ { "itemId": 33, "foodId": 1, "foodName": "米饭", "quantityG": 100.0,
                   "calories": 116.0, "protein": 2.6, "fat": 0.3, "carb": 25.9 } ],
      "total": { "calories": 116.0, "protein": 2.6, "fat": 0.3, "carb": 25.9 }
    }
  ],
  "totals": { "calories": 1920.0, "protein": 88.0, "fat": 52.0, "carb": 240.0 },
  "targets": {
    "bmr": 1648.8, "tdee": 2267.0, "calories": 2267.0,
    "protein": 105.0, "fat": 63.0, "carb": 320.0,
    "formula": "Mifflin-St Jeor 基础代谢 × 1.375 活动系数",
    "assumptions": [
      "活动水平按轻度活动（系数 1.375）估算",
      "目标为维持体重：蛋白质 1.5 g/kg、脂肪占总热量 25%、其余由碳水补齐"
    ]
  },
  "comparison": {
    "calories": { "intake": 1920.0, "target": 2267.0, "diff": -347.0, "percent": 84.7, "status": "UNDER" },
    "protein":  { "intake": 88.0, "target": 105.0, "diff": -17.0, "percent": 83.8, "status": "UNDER" },
    "fat":      { "intake": 52.0, "target": 63.0, "diff": -11.0, "percent": 82.5, "status": "UNDER" },
    "carb":     { "intake": 240.0, "target": 320.0, "diff": -80.0, "percent": 75.0, "status": "UNDER" }
  }
}
```

说明：

- `meals` 按 `BREAKFAST → LUNCH → DINNER → SNACK` 排序（同类内按时间）
- `status`：达成率 85%~115% 为 `ON_TRACK`，低于 `UNDER`，高于 `OVER`
- 当天没有记录：`meals: []`、`totals` 全 0，**仍返回 200**
- 用户没填资料：`targets` 与 `comparison` 均为 `null`

---

## 7. AI 饮食推荐

> 链路：大模型生成方案 → 逐样核验热量（本地食物库 + Open Food Facts）→ 后端按每餐预算缩放克数。
> 模型不可用（无密钥 / 超时 / 返回非法 JSON）时自动降级为本地规则搭配，**接口仍返回 200**。

### 7.1 生成方案

`POST /api/ai/meal-plan`

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| goal | string | 否 | `FAT_LOSS` / `MAINTAIN` / `MUSCLE_GAIN`，默认 `MAINTAIN` |
| targetCalories | number | 否 | 800–5000，缺省用用户资料算出的 TDEE |
| mealTypes | string[] | 否 | `BREAKFAST/LUNCH/DINNER/SNACK`，默认三餐 |
| preferences | string | 否 | ≤200 字，如「想吃中餐、少油」 |
| excludeAllergens | string[] | 否 | 与账号过敏原合并 |

```json
// 请求
{ "goal": "FAT_LOSS", "targetCalories": 1800, "preferences": "想吃中餐，少油" }
```

```json
// 响应 data（节选）
{
  "goal": "FAT_LOSS",
  "targetCalories": 1800.0,
  "source": "llm",
  "model": "deepseek-chat",
  "summary": "中式少油减脂三餐，控制总热量约1800kcal…",
  "meals": [
    {
      "mealType": "BREAKFAST",
      "items": [
        {
          "name": "牛奶", "nameEn": "milk", "grams": 261.0,
          "kcalPer100g": 54.0, "calories": 140.9,
          "protein": 8.4, "fat": 7.8, "carb": 11.1,
          "verified": true, "verifySource": "food-db", "sourceUrl": ""
        }
      ],
      "total": { "calories": 682.0, "protein": 42.3, "fat": 21.4, "carb": 74.2 }
    }
  ],
  "totals": { "calories": 1801.2, "protein": 120.5, "fat": 55.1, "carb": 190.3 },
  "verification": { "checked": 12, "matched": 10 },
  "notes": [
    "已核验 10 项（本地食物库 9 项、联网 1 项）",
    "其余 2 项为模型估算值"
  ]
}
```

字段说明：

| 字段 | 含义 |
|---|---|
| `source` | `llm` = 大模型生成；`local` = 本地规则兜底 |
| `verifySource` | `food-db` 项目食物库 / `web` 联网营养库 / `estimate` 模型估算 |
| `verification` | `checked` 参与核验的项数，`matched` 命中项数 |
| `grams` | 已按该餐热量预算缩放，全天合计与目标偏差通常在 ±3% 内 |

降级示例（未配置密钥时）：

```json
{ "source": "local", "model": null,
  "notes": ["未配置大模型密钥，已使用本地食物库规则搭配", "..."] }
```

### 7.2 实现要点

- **联网核验**：`Open Food Facts` 旧版搜索接口（v2 会忽略 search_terms 且常 503），带内存缓存与超时降级；
- **三重过滤**：名称匹配、宏量自洽（4P+9F+4C 误差 ≤30%）、数值合理（4–900 kcal/100g）；
- **双源一致性**：联网值与模型估值偏差 >60% 判为错配，宁用估值也不采信错数据；
- **预算收敛**：模型只决定"吃什么"，克数由后端按每餐预算（早 30% / 午 40% / 晚 30%）缩放。

配置（`ai.*`）：

| 配置 | 说明 |
|---|---|
| `ai.enabled` | 总开关 |
| `ai.api-key` | 从 `ai-local.yml`（已 gitignore）或环境变量 `DEEPSEEK_API_KEY` 读取 |
| `ai.base-url` / `ai.model` | DeepSeek OpenAI 兼容端点，默认 `https://api.deepseek.com` / `deepseek-chat` |
| `ai.timeout-ms` / `ai.web-timeout-ms` | 模型 60s / 联网 15s |

## 8. 账号管理（仅管理员）

> 需要 `role = ADMIN`。普通用户访问一律 `403 无权访问该资源`。
> 角色与状态**以数据库为准**（每次请求由 JWT 过滤器查库），因此改角色、禁用账号即时生效，无需等 token 过期。

### 8.1 用户列表

`GET /api/admin/users?page=1&size=10&keyword=demo`

```json
{
  "records": [
    {
      "id": 1, "username": "demo", "role": "ADMIN", "status": 1,
      "allergens": [], "gender": "MALE", "profileComplete": true,
      "createdAt": "2026-11-07 12:50:12"
    }
  ],
  "total": 3, "page": 1, "size": 10
}
```

### 8.2 启用 / 禁用

`PATCH /api/admin/users/{id}/status`

```json
{ "status": 0 }
```

- `status=0` 后：该账号登录返回 `403 账号已被禁用，请联系管理员`，**已签发的 token 立即失效（401）**
- 不能改自己：`400 不能修改自己的账号状态`
- 不能禁用最后一个管理员：`400 不能禁用最后一个管理员`

### 8.3 修改角色

`PATCH /api/admin/users/{id}/role`

```json
{ "role": "ADMIN" }
```

`role` 只能是 `ADMIN` / `USER`；不能改自己；不能降级最后一个管理员。

### 8.4 重置密码

`POST /api/admin/users/{id}/reset-password`

```json
{ "password": "NewPass@123" }
```

规则与注册一致（8-32 位且同时含字母和数字）。重置后旧密码立即失效。

### 8.5 删除账号

`DELETE /api/admin/users/{id}`

连带删除该用户的全部餐次与食物项（`meal_item → meal → sys_user`）。
不能删除自己，也不能删除最后一个管理员。

## 9. 其它

| 路径 | 说明 |
|---|---|
| `GET /doc.html` | Knife4j 可视化文档（可填 token 调试） |
| `GET /v3/api-docs` | OpenAPI 3 JSON |
| `GET /swagger-ui.html` | Swagger UI |
| `GET /` | Web 前端（Vue 构建产物，由后端静态托管） |
| `GET /assets/**` | 前端静态资源（无需认证） |

---

## 10. curl 快速验证

```bash
# 1) 登录拿 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Demo@123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# 2) 搜索食物
curl -s "http://localhost:8080/api/foods?keyword=米饭" -H "Authorization: Bearer $TOKEN"

# 3) 建一餐并加一项
MEAL=$(curl -s -X POST http://localhost:8080/api/meals \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"mealType":"LUNCH"}' | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

curl -s -X POST "http://localhost:8080/api/meals/$MEAL/items" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"foodId":1,"quantityG":200}'

# 4) 看热量与建议
curl -s "http://localhost:8080/api/meals/$MEAL/calories" -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8080/api/meals/$MEAL/replacements?strategy=higher" -H "Authorization: Bearer $TOKEN"
```

---

## 11. AI 辅助接口（口语录餐 / Agent / 问答 / 解释）

> 四个接口都受 **429 限流**（每用户 20 次/分钟）约束；模型不可用时自动降级，仍返回 200。

### 11.1 自然语言录餐

`POST /api/ai/parse-meal`

```json
{ "text": "中午吃了两碗米饭和一份鸡胸肉" }
```

```json
{
  "code": 200,
  "data": {
    "status": "PARSED",              // 或 NEED_CLARIFICATION
    "source": "llm",                  // llm / rule
    "items": [
      { "foodId": 1, "spokenName": "米饭", "name": "米饭", "category": "主食",
        "quantityText": "两碗", "grams": 400.0,
        "calories": 464.0, "protein": 10.4, "fat": 1.2, "carb": 103.6, "confidence": 1.0 }
    ],
    "questions": [],                  // NEED_CLARIFICATION 时给出 type/message/candidates
    "note": null
  }
}
```

追问类型：`UNKNOWN_FOOD`（食物库没有，带候选）、`AMBIGUOUS_QUANTITY`（没说克数/无法换算）。

### 11.2 Agent 对话（LangChain4j @Tool 编排）

`POST /api/ai/agent`

```json
{ "sessionId": null, "message": "午饭吃了米饭 400 克、鸡胸肉 150 克，帮我记一下" }
```

```json
{ "code": 200, "data": { "sessionId": "9f2c…", "reply": "午餐已记录 ✅ …", "source": "llm" } }
```

- 模型自主调用 `searchFood / createMeal / addMealItem / calculateCalories / getReplacements`；
- `sessionId` 为空时服务端生成，后续请求带上同一个 id 即可续聊（多轮记忆）；
- 缺参数会先追问；`userId` 不在工具参数里，越权仍由 Service 抛 403；
- 无密钥时 `source=disabled`。

`DELETE /api/ai/agent/{sessionId}` 清空该会话记忆。

### 11.3 营养问答（RAG）

`POST /api/ai/ask`

```json
{ "question": "减脂期晚餐怎么吃" }
```

```json
{ "code": 200, "data": { "answer": "……[1][3]", "source": "llm",
  "citations": [ { "id": "kb-3", "title": "晚餐怎么安排", "kind": "knowledge", "score": 3.21, "text": "…" } ] } }
```

检索在本地 BM25 完成（食物库 + 7 条营养常识）；模型不可用时 `source=retrieval`，返回检索要点。

### 11.4 替换建议 + 人话解释

`GET /api/meals/{mealId}/replacements/explain?strategy=higher`

规则引擎先算出 Top3（排序、delta 不变），再由模型把 `reason` 润色；模型失败时
`explanation` 回退为 `ruleReason`，`source=rule`：

```json
{ "code": 200, "data": { "mealId": 1, "strategy": "higher", "source": "llm",
  "items": [ { "itemId": 1, "originalName": "米饭", "quantityG": 200.0,
    "suggestions": [ { "foodId": 11, "name": "意大利面(煮)", "deltaCalories": 84.0,
      "ruleReason": "同为「主食」，每 100g 比「米饭」高 42.0 kcal…",
      "explanation": "意面更耐嚼饱腹，每100g多约84千卡需控量。", "fromLlm": true } ] } ] } }
```

### 11.5 可观测 / 健康检查

| 路径 | 说明 |
|---|---|
| `GET /actuator/health` | 健康检查（公开，compose 探针用） |
| `GET /actuator/metrics` | 指标列表（需登录） |
| `GET /actuator/prometheus` | Prometheus 文本指标（需登录） |

关键指标：`mealwise_ai_calls_total{feature,source,result}`、`mealwise_ai_latency_seconds`、
`mealwise_ai_tokens_total{feature,kind}`。

### 11.6 撤销入账

`DELETE /api/meals/{mealId}`

删除整餐（食物项由外键级联删除）。归属校验与其它餐次接口一致：不是自己的餐次返回 `403`，
不存在返回 `404`。前端在 AI 录餐成功后提供「撤销这次入账」按钮直接调它。

```json
{ "code": 200, "message": "ok" }
```

### 11.7 AI 调用审计（仅 ADMIN）

`GET /api/admin/ai-audit?limit=50`

返回最近的 AI 调用记录（不包含用户输入原文）：

```json
{ "code": 200, "data": [
  { "id": 12, "userId": 1, "feature": "ask", "source": "llm",
    "result": "success", "latencyMs": 1213, "detail": null, "createdAt": "2026-11-11 19:29:01" }
] }
```

普通用户访问 → `403`。审计写入失败不会影响业务（服务内已吞异常）。

### 11.8 缓存与熔断

- **缓存**：`ask` 结果按问题 SHA-256 缓存 10 分钟；`explain` 按 `userId+mealId+strategy` 缓存 5 分钟。
  命中时不计 token、不重复写审计。
- **熔断**：模型连续失败 3 次后打开 60 秒；期间 AI 接口直接走降级（`source=rule/retrieval`），
  不再等待模型超时。
