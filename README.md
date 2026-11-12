# Mealwise · 一餐热量计算与食物替换推荐

一个可演示、可交付、可面试讲解的全栈项目：登录后记录一餐吃了什么、多少克，系统算出总热量与三大营养素，并**在同类别里**给出「换成什么更合适」的 Top3 建议。

另外提供 **AI 饮食推荐**：大模型（DeepSeek）生成一日三餐方案 → 逐样核验热量（项目食物库 + Open Food Facts 联网）→ 后端按每餐预算缩放克数，模型不可用时自动降级为本地规则搭配。

浏览器打开即用：**http://localhost:8080**（前端已打进后端 jar，单端口、同源、无 CORS）。

## 文档索引

| 文档 | 读它来做什么 |
|---|---|
| [docs/GUIDE.md](docs/GUIDE.md) | **使用与运维说明**：怎么跑、每个页面干什么、数字怎么算、FAQ、运维要点 |
| [docs/API.md](docs/API.md) | **接口文档**：全部接口的请求/响应、错误码、curl 示例 |
| [docs/APIFOX.md](docs/APIFOX.md) | **用 Apifox 检测**：可导入集合（21 个请求）、环境变量、断言说明 |
| [mealwise-miniprogram/README.md](mealwise-miniprogram/README.md) | 微信小程序导入、必改配置、上线要求 |
| [docs/init.sql](docs/init.sql) | 建表 + 46 条食物 + 3 个演示账号 + 3 个演示餐次（全新安装用这个） |
| [docs/migration-v4-demo-seed.sql](docs/migration-v4-demo-seed.sql) · [docs/migration-v5-meal-item-version.sql](docs/migration-v5-meal-item-version.sql) | 老库升级：补演示数据 / 补 `meal_item.version` |

> 本文档（README）偏"怎么起 + 怎么验收 + 设计取舍"。

---

## 1. 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.3.5 · MyBatis-Plus 3.5.9 · MySQL · Jakarta Validation · JJWT 0.12 · **LangChain4j 1.23（Agent 工具调用）** · springdoc + Knife4j |
| 前端 | Vue 3 · TypeScript · Vite · Pinia · Vue Router（hash） · Element Plus · Axios |
| 构建 | Maven · pnpm |
| 部署 | 可执行 jar（前端静态托管）/ Docker + docker-compose |

**刻意没有引入**：Redis、Kafka、微服务、服务注册。单体可运行是硬约束——演示项目里这些只会增加启动门槛。

---

## 2. 目录结构

```
mealwise/
├── mealwise-server/                 # Spring Boot 单体
│   └── src/main/java/com/mealwise/
│       ├── common/                  # Result / 异常 / traceId / BigDecimal 序列化 / 枚举 / 工具
│       ├── security/                # JWT 过滤器、Security 配置、401-403 写出
│       ├── user/                    # 注册登录
│       ├── food/                    # 食物库 CRUD
│       ├── meal/                    # 餐次、食物项、热量计算
│       ├── recommendation/          # 替换推荐（只读，规则引擎）
│       └── ai/                      # 模型调用（方案 / 口语解析 / 解释 / RAG / LangChain4j Agent），全部可降级
├── mealwise-web/                    # Vue3 前端（dist 会被复制到后端 static）
├── mealwise-miniprogram/            # 微信小程序（同一套后端 API）
├── docs/
│   ├── init.sql                     # 建表 + 46 条食物 + 3 个演示账号 + 3 个演示餐次
│   ├── migration-v4-demo-seed.sql   # 老库补演示账号/餐次
│   └── migration-v5-meal-item-version.sql  # 老库补乐观锁列
├── Dockerfile                       # 多阶段：容器内 mvn package → JRE 运行
├── .dockerignore
├── docker-compose.yml               # MySQL 8 + app
└── start-local.ps1                  # 本机一键启动
```

---

## 3. 快速开始

### 3.1 本机（Windows，推荐）

前置：MySQL 5.7+/8.0 已启动（默认 `root/123456`，可在 `application-dev.yml` 改）。

```powershell
cd <repo>
.\start-local.ps1
```

脚本会：初始化数据库（若无 `mealwise` 库）→ 构建 jar（若无）→ 启动 → 打开浏览器。

手动等价步骤：

```powershell
# 1) 建库（空库即可；表与种子由 Flyway 在应用启动时自动创建）
mysql -u root -p123456 -e "CREATE DATABASE IF NOT EXISTS mealwise DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2) 打包（前端产物需先在 mealwise-server/src/main/resources/static）
mvn -f mealwise-server\pom.xml -DskipTests package

# 3) 启动（Flyway 会执行 V1：建表 + 46 条食物 + 演示账号/餐次）
java -jar mealwise-server\target\mealwise-server-1.0.0.jar --spring.profiles.active=dev
```

> 也可以直接 `mysql < docs/init.sql` 建全套数据；此时 Flyway 会记录一条 `BASELINE` 并跳过迁移，效果一样。
> 若 Tomcat 报 `Unable to create tempDir`，加 `-Djava.io.tmpdir=<可写目录>`。

要启用**运行时 LLM**（口语录餐 / 解释润色 / 营养问答）：把密钥写进 `mealwise-server/ai-local.yml` 的 `ai.api-key`（该文件已 gitignore），或设环境变量 `DEEPSEEK_API_KEY`。**不配也能跑**，三个接口会走本地规则/检索兜底。

### 3.2 Docker（MySQL 8 + 应用，一条命令）

```bash
cp .env.example .env        # 填 DEEPSEEK_API_KEY（不填也能起，AI 自动降级）
docker compose up --build
```

打开 http://localhost:8080 。compose 会自动建库、导入种子数据、等 MySQL 健康后启动应用。

**不需要本机装 JDK / Maven**：`Dockerfile` 是多阶段的，构建阶段在 `maven:3.9-eclipse-temurin-17` 里打包，运行阶段只有 `17-jre`（前端 dist 已在 `mealwise-server/src/main/resources/static`，随 jar 走）。

| 细节 | 说明 |
|---|---|
| MySQL 端口 | 映射到 **3307**（避开宿主机已有 3306） |
| 应用端口 | 8080 |
| 初始化 SQL | `docs/init.sql` 挂进 `/docker-entrypoint-initdb.d/`，仅首次建库时执行 |
| JWT 密钥 | compose 里通过 `JWT_SECRET` 注入，**生产必须换成 32+ 字节随机串** |
| 重新初始化 | `docker compose down -v` 删掉数据卷再 up |
| 镜像拉不动 | 国内网络到 `registry-1.docker.io` 可能只解析到 IPv6 而超时：给 Docker Desktop 配 registry mirror，或先 `docker pull 镜像源/library/xxx` 再 `docker tag` 成标准名 |
| 中文乱码 | `docs/init.sql` 已在开头 `SET NAMES utf8mb4`；若手工导入旧脚本，记得加 `--default-character-set=utf8mb4` |
| Flyway | 容器首次启动时日志会出现 `Successfully baselined schema with version: 1`（init.sql 已建表），属正常 |

> **已验证的跑法**（本机实测）：Docker Desktop 29.9.3 + Docker Compose v5.7.0 + WSL2；
> 因 `docker.io` 直连超时，先用 `dockerproxy.net` 拉取 `mysql:8.0 / maven:3.9-eclipse-temurin-17 /
> eclipse-temurin:17-jre` 并 `docker tag` 成标准名，再 `docker compose up --build -d`。
> 起完后 `docker ps` 两个容器均 `healthy`，`http://localhost:8080` 用 `demo / Demo@123` 登录成功，
> AI 录餐/问答在容器内同样可用（真实 DeepSeek key 来自 `.env`）。

### 3.3 演示账号

`docs/init.sql` 已经内置这三个账号（BCrypt 哈希），**装完即可登录，不用先注册**：

| 账号 | 密码 | 说明 |
|---|---|---|
| `demo` | `Demo@123` | **主演示账号 + 管理员**（无过敏原；可进「用户管理」；名下预置 2 个餐次） |
| `other` | `Other@123` | 普通用户，名下 1 个餐次，用于演示越权：访问 demo 的餐次返回 403 |
| `allergy` | `Allergy@123` | 普通用户，对 `egg` 过敏，用于演示过敏原过滤 |

> 已有库（不是用 init.sql 新建的）要补这三个账号与演示餐次，执行一次 `docs/migration-v4-demo-seed.sql` 即可（幂等）。

也可以直接注册新账号（默认角色 `USER`、状态启用）。

**管理员能力**（`demo` 登录后顶部出现「用户管理」）：

| 操作 | 接口 | 保护规则 |
|---|---|---|
| 查看全部账号 | `GET /api/admin/users` | 仅 ADMIN |
| 启用 / 禁用 | `PATCH /api/admin/users/{id}/status` | 不能改自己；不能禁用最后一个管理员；**禁用后已签发 token 立即失效** |
| 改角色 | `PATCH /api/admin/users/{id}/role` | 不能改自己；不能降级最后一个管理员 |
| 重置密码 | `POST /api/admin/users/{id}/reset-password` | 规则同注册（8-32 位含字母数字） |
| 删除账号 | `DELETE /api/admin/users/{id}` | 不能删自己；连带删除该用户餐次与食物项 |

角色与状态**以数据库为准**（每次请求由 JWT 过滤器查库），所以改角色/禁用账号不需要等 token 过期。

### 3.4 手机端（微信小程序）

小程序在 `mealwise-miniprogram/`，**复用同一个后端**，只需要改一处配置：

```js
// app.js
globalData: { baseUrl: 'http://<你电脑的局域网IP>:8080' }
```

1. 微信开发者工具 →「导入项目」→ 选 `mealwise-miniprogram` → 填 AppID（或测试号）
2. **详情 → 本地设置 → 勾选「不校验合法域名…」**（否则 http + IP 会被拦）
3. 编译后用 `demo / Demo@123` 登录

页面：今日（三餐聚合 + 目标对比）、食物库、记录一餐、我的资料、登录。
真机预览需要自己的 AppID；上线需要 https + 备案域名。

> 本机没有微信开发者工具，**小程序未做运行时验证**，只做了 JSON/JS 语法校验与接口地址连通性验证。

### 3.5 手机浏览器（同一套 H5，已做移动适配）

不需要装任何东西：手机与电脑同一 Wi-Fi，浏览器打开 `http://<局域网IP>:8080`。

移动端适配集中在 `mealwise-web/src/styles/mobile.css`，只补了桌面样式覆盖不到的三类问题：

| 问题 | 处理 |
|---|---|
| 表格列多，手机上挤压/溢出 | ≤640px 单元格压紧、字号降到 13px、食物库隐藏「过敏原/状态」列；≤380px 再隐藏「类别」 |
| 弹窗固定宽，手机上出框 | 所有 `el-dialog` 改 94vw、body 限高 68vh 可滚动、底部按钮等宽铺满 |
| iOS 输入框聚焦自动放大 | 输入类控件字号统一提到 16px（iOS 的缩放阈值） |
| 刘海屏遮挡 | `env(safe-area-inset-bottom)` 底部留白 |
| 导航放不下 | 横向滚动而非折行 |

断点：`900 / 760 / 640 / 560 / 380`（前两档是页面原有，后三档是本次补的）。

---

## 4. 验收演示流程（2 分钟）

### 4.1 命令行验收：登录 / Token / 越权 403

```bash
# 0) 登录拿 JWT（响应体：{ code, message, data: { token, tokenType, expiresIn, user } }）
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Demo@123"}' | jq -r '.data.token')

# 1) 自己的餐次 → 200，合计 465.5 kcal（米饭 200g + 鸡胸肉 150g + 西兰花 100g）
curl -s http://localhost:8080/api/meals/1/calories -H "Authorization: Bearer $TOKEN" | jq '.data.total'

# 2) 不带 token → 401（未认证）
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/meals/1/calories

# 3) 用 other 的 token 访问 demo 的餐次 → 403（按 userId 隔离，不是 404）
OTHER=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"other","password":"Other@123"}' | jq -r '.data.token')
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:8080/api/meals/1/calories -H "Authorization: Bearer $OTHER"

# 4) 伪造/过期 token → 401
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:8080/api/meals/1/calories -H "Authorization: Bearer not-a-jwt"
```

> 期望输出依次为：`465.5` → `401` → `403` → `401`。
> `userId` 只从 JWT 里取，请求体里传 `userId` 会被忽略；换别人的 `mealId` 一律 403。

### 4.2 页面验收

1. **登录** `demo / Demo@123`
2. 进「食物库」：搜「米饭」，能看到 46 条种子数据、分页、类别筛选；试着新增一条食物再删掉（软删除，列表消失但历史数据不受影响）
3. 进「一餐记录」：新建午餐 → 搜索并添加
   - 米饭 200 g
   - 鸡胸肉 150 g
   - 西兰花 100 g
4. 右侧实时显示：**465.5 kcal / 蛋白 46.2 g / 脂肪 5.0 g / 碳水 57.0 g**
5. 点「替换建议」：
   - 米饭（主食，116 kcal/100g）Top3：意大利面(煮) +84 kcal、蛋炒饭 +128 kcal、花卷 +202 kcal
   - 每条都带一句理由：`同为「主食」，每 100g 比「米饭」高 42.0 kcal；按 200g 计，替换后多摄入约 84.0 kcal`
   - 切到 `lower` 可以看到热量更低的候选（delta 为负）
6. **今日记录**：点顶部「今日记录」
   - 早餐/午餐/晚餐按固定顺序分区，每餐显示时间、小计、食物明细
   - 右侧「今日摄入 vs 建议」：4 条进度条 + 差值 + 状态标签（未达标/达标/超标）
   - 日期选择器可回看历史某天；非今天时出现「今天」快捷按钮
7. **身体资料**：右上角头像 →「我的资料」
   - 填 男 / 175 cm / 70 kg（年龄可留空，按 30 岁估算）
   - 保存后回「今日记录」，应看到：BMR ≈ 1648.8、建议 2267 kcal、蛋白 105 g、脂肪 63 g、碳水 320 g
   - 说明区会展示计算口径与两条假设（年龄缺省、活动系数 1.375）
8. **边界演示**
   - 克数填 0 → 400 `克数必须大于 0`
   - `foodId` 传 999999 → 404 `食物不存在`
   - 策略传 `whatever` → 400 `替换策略非法，仅支持 higher 或 lower`
   - 身高填 50 cm → 400 `身高需在 100-250 cm 之间`
   - 用 `other` 账号访问 demo 的餐次 → 403 `无权访问该餐次`
   - 退出登录后直接访问接口 → 401
9. **接口文档**：http://localhost:8080/doc.html （Knife4j）或 `/swagger-ui.html`

---

## 5. API 一览

统一响应：`{ code, message, data, traceId }`，`code` 与 HTTP 状态码一致。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册（用户名唯一，BCrypt） |
| POST | `/api/auth/login` | 登录，返回 JWT（24h） |
| GET | `/api/foods?keyword=&category=&page=&size=` | 分页搜索（默认只返回上架） |
| POST | `/api/foods` | 新增食物 |
| PUT | `/api/foods/{id}` | 全量编辑 |
| DELETE | `/api/foods/{id}` | 软删除（`enabled=0`） |
| POST | `/api/meals` | 创建一餐 |
| POST | `/api/meals/{mealId}/items` | 添加食物项 |
| PUT | `/api/meals/{mealId}/items/{itemId}` | 改克数 |
| DELETE | `/api/meals/{mealId}/items/{itemId}` | 删项 |
| DELETE | `/api/meals/{mealId}` | 删除整餐（连带食物项），用于撤销 AI 入账 |
| GET | `/api/meals/{mealId}/calories` | 热量明细 + 合计 |
| GET | `/api/meals/{mealId}/replacements?strategy=higher` | 替换建议 |
| GET | `/api/users/me` | 当前用户资料（含身体数据与 `profileComplete`） |
| PUT | `/api/users/me/profile` | 更新性别 / 身高 / 体重 / 年龄 |
| GET | `/api/days/today` | 今日记录：早/午/晚聚合 + 合计 + 目标对比 |
| GET | `/api/days/{date}` | 指定日期（`yyyy-MM-dd`）的记录 |
| POST | `/api/ai/meal-plan` | AI 饮食推荐（大模型 + 联网核验，失败降级本地） |
| POST | `/api/ai/parse-meal` | 自然语言录餐：`{"text":"中午吃了两碗米饭和一份鸡胸肉"}` → 结构化食物项 / 追问 |
| POST | `/api/ai/ask` | 营养知识问答（BM25 检索食物库 + 常识 → LLM 依据资料回答） |
| GET | `/api/meals/{mealId}/replacements/explain?strategy=higher` | 规则 Top3 + LLM 润色的一句解释（模型失败回退规则原文） |
| POST | `/api/ai/agent` | LangChain4j Agent 对话：模型自主编排 5 个工具，多轮记忆按 sessionId |
| DELETE | `/api/ai/agent/{sessionId}` | 清空该会话记忆 |
| GET | `/api/admin/users` | 用户列表（仅 ADMIN） |
| PATCH | `/api/admin/users/{id}/status` | 启用/禁用（仅 ADMIN） |
| PATCH | `/api/admin/users/{id}/role` | 改角色（仅 ADMIN） |
| POST | `/api/admin/users/{id}/reset-password` | 重置密码（仅 ADMIN） |
| DELETE | `/api/admin/users/{id}` | 删除账号（仅 ADMIN） |
| GET | `/api/admin/ai-audit?limit=50` | AI 调用审计（仅 ADMIN）：feature/source/result/耗时 |

错误码：`400` 参数/策略非法 · `401` 未登录或 token 失效 · `403` 越权/非管理员/账号被禁用 · `404` 资源不存在 · `409` 冲突（用户名占用、重复禁用、乐观锁版本冲突） · `415` Content-Type 不支持 · **`429` AI 接口限流 / 日 token 额度用完** · `500` 系统异常。

---

## 6. 关键设计（面试可讲）

### 6.1 数据隔离：一处校验，而非到处 `where user_id`

所有餐次入口第一个动作都是 `mealService.requireOwned(userId, mealId)`：
- 餐次不存在 → 404
- 存在但不属于当前用户 → 403

`userId` 只来自 JWT，请求体里即使传了也被忽略。`meal_item` 还要再校验「item 属于路径上的 meal」，防止用我的 mealId 改别人的 itemId。

### 6.2 热量为什么不在表里冗余

`meal_item` 只存 `food_id + quantity_g`。热量是派生值：
- 冗余后要在「改克数 / 换食物 / 修正食物库」三条路径同步，漏一条就永久错账
- 食物库会迭代（修正数据），冗余会让历史餐次用旧值、新餐次用新值，口径分裂
- 计算成本是单餐几次乘法，O(项数)，完全不值得用一致性去换

真需要"审计当时算的数"，正确做法是**快照**语义，而不是把派生值当事实表。

### 6.3 推荐为什么按「热量差升序」而不是取极值

直接取热量最高/最低，等于给用户一张排行榜，结果往往是「换成五花肉」这种不可执行建议。
按 `|delta|` 升序 = 优先给**差异温和**的选项，更可接受，也更容易用一句话解释清楚。

算法：同类别 → 上架 → 热量严格更高/更低 → 过敏原交集为空 → 按 `|delta|` 升序取 Top3。
无候选时返回 `suggestions: []` + `message`，**仍是 200**——"没有建议"是正常业务结果，不是错误。

### 6.4 精确计算：BigDecimal 只在出口截断

`per100g × qty ÷ 100` 全程高精度，最后一步 `setScale(1, HALF_UP)`。
出口统一由 Jackson 序列化器把 `BigDecimal` 格式化成 1 位小数，避免"每项先四舍五入再求和"与"先求和再四舍五入"差 0.1。

### 6.5 401/403 在 Filter 层自己写响应

Spring Security 的认证失败发生在 DispatcherServlet 之前，`@RestControllerAdvice` 捕获不到，默认会给 HTML 错误页。所以 `AuthErrorWriter` 直接序列化统一响应体，前端拿到的永远是 JSON。

### 6.6 每日建议摄入：公式与口径

用 **Mifflin-St Jeor** 估算基础代谢（比 Harris-Benedict 更贴近现代人群）：

```
男：BMR = 10×体重kg + 6.25×身高cm − 5×年龄 + 5
女：BMR = 10×体重kg + 6.25×身高cm − 5×年龄 − 161
TDEE = BMR × 活动系数（本项目固定 1.375 轻度活动）
```

三大营养素按「维持体重」分配：**蛋白质 1.5 g/kg、脂肪占总热量 25%、剩余热量给碳水**。

三个刻意的设计：

1. **只在出口取整**。BMR 是 `1648.75` 这种小数，如果先四舍五入成 `1648.8` 再乘活动系数，TDEE 会差 0.1；所以中间全程保留精度，最后一步才 `setScale(1)`。
2. **假设必须外显**。年龄缺省按 30 岁、活动系数 1.375，这两条都通过 `targets.assumptions[]` 原样返回给前端展示——用户看到"建议 2267 kcal"时，必须能知道这个数字基于什么假设，否则就是黑箱。
3. **资料不全返回 null，不猜**。缺性别/身高/体重时 `targets: null`、`comparison: null`，前端引导去填资料，而不是拿默认体重蒙一个数字出来。

### 6.7 一天是怎么组织的

`meal` 表本来就带 `meal_type` 与 `eaten_at`，所以「日记录」不是新表，而是**按天聚合的读模型**：

- 查询当天 `[00:00, 次日00:00)` 的所有 meal → 每餐带明细与小计 → 汇总当天合计
- 按 `BREAKFAST → LUNCH → DINNER → SNACK` 固定顺序展示（同类内按时间）
- 对比口径：达成率 `intake / target × 100`，落在 **85%~115%** 记 `ON_TRACK`，低于 `UNDER`，高于 `OVER`

**为什么不建 `day_log` 表**：日记录没有自己的状态，它完全由 meal 派生。建表就要处理"改了某一餐、日记录要不要同步"这类一致性问题；而聚合查询的成本是单日几次索引扫描，量级完全可接受。真要有"每日总结、备注、打卡"这类自有字段，再加表才合理。

### 6.8 traceId 贯穿

`TraceIdFilter` 为每个请求生成/透传 `X-Trace-Id`，写 MDC 与响应头，日志 pattern 带 `%X{traceId}`。用户报错时贴一张图，grep 一个 id 就能定位整条链路。


---

### 6.9 并发与一致性：乐观锁 + 用户维度的日志

多用户并发时，"数据归属"靠 `meal.user_id` 就能分清（越权一律 403）；
但"同一行被并发修改"会静默丢失更新——所以 `meal_item` 加了一列 `version`：

| 场景 | 行为 |
|---|---|
| 前端带回读取时的 `version` | 服务端比对，不一致直接 `409 该食物项已被修改，请刷新后重试` |
| 不带 `version`（老客户端） | 退化为"读后带版本条件更新"，并发时仍只有 1 个成功，其余 409 |
| 影响行数为 0 | 说明版本已被其他请求推进，同样返回 409，绝不静默覆盖 |

实测 10 个并发改同一条克数：**1×200 + 9×409**（改造前是 10×200、最后写入者胜）。

另外两处配套：

- **日志带 userId**：pattern 里加 `[u:%X{userId:-anon}]`，并发排查时能按用户过滤，不再只有 traceId；
- **约束异常兜底**：`DataIntegrityViolationException` 统一转 400（数据超范围/违反约束），避免校验漏网直接炸成 500。
### 6.10 运行时 LLM：只做「语言」，不做「算术」

模型只在两个地方出现，且都可以被关掉而不影响主流程：

| 能力 | 模型负责 | **Java 负责（可测试、可复现）** | 模型挂了怎么办 |
|---|---|---|---|
| 自然语言录餐 | 把「两碗米饭和一份鸡胸肉」抽成 `[{name, quantity}]` | 食物名匹配（完全/别名/包含）、份数×单位重量换算克数、热量与三大营养素计算 | 降级为内置正则解析（`MealTextParser`），结构完全一致 |
| 替换建议解释 | 把规则给的 delta 润色成一句人话 | Top3 的候选、排序、delta 全部来自 `ReplacementService`，一条都不改 | `explanation` 直接用规则自带的 `reason` |
| 营养问答（RAG） | 依据检索到的资料作答 | BM25 检索（食物库 + 7 条常识），引用来源原样返回 | 返回「检索要点」，不编造 |

追问机制（不猜、不静默）：
- 食物库里没有 → `NEED_CLARIFICATION` + `type=UNKNOWN_FOOD`，附相近候选；
- 没说克数 / 单位无法换算 → `type=AMBIGUOUS_QUANTITY`，提示补「200 克」或「一碗」。

配置：`ai.api-key`（`ai-local.yml` 或环境变量 `DEEPSEEK_API_KEY`），`ai.base-url` / `ai.model` / `ai.timeout-ms`。
**没有密钥时三个接口都返回 200**，只是 `source=rule` / `retrieval`。

### 6.11 LangChain4j Agent：模型只编排，不碰计算

`@Tool` 注册了 5 个工具（`ai/agent/MealAgentTools`），模型自己决定调用顺序与次数：

| 工具 | 底层调用（现有 Service） | 模型决定的部分 |
|---|---|---|
| `searchFood(keyword, extraAllergens)` | `FoodService.page` + 过敏原过滤 | 先查什么、要不要按新过敏原排除 |
| `createMeal(mealType, eatenAt)` | `MealService.create` | 哪一餐、什么时间 |
| `addMealItem(mealId, foodId, grams)` | `MealService.addItem` | 先加哪样、多少克 |
| `calculateCalories(mealId)` | `MealService.calories` | 什么时候汇报 |
| `getReplacements(mealId, strategy)` | `ReplacementService.recommend` | higher 还是 lower |

三条硬边界：

1. **`userId` 不在工具参数里**：工具实例按请求构造、绑定登录用户；越权仍由 Service 抛 403，工具把它转成 `错误: ...` 交给模型解释，不会 500。
2. **数字不经过模型**：热量公式、替换排序、过敏原过滤都在 Service；模型只转述工具返回。
3. **缺参数先追问**：系统提示明确要求「一次只问缺的那一个」。实测只给「两碗米饭」时会先问克数，不会硬编。

多轮记忆在 `AgentMemoryStore`：按「用户 + sessionId」隔离、最近 20 条消息、LRU 200 个会话；
过敏原与减脂/增重偏好就存在对话历史里，跨轮仍生效（实测 4 轮内保持「减脂」「花生过敏」）。
没有密钥时返回 `source=disabled`，不会建模型、也不会假装成功。

实测（真实 DeepSeek）：

```
用户：午饭吃的是米饭 400 克、鸡胸肉 150 克，帮我记下来
助手：午餐已记录 ✅ 米饭 400g 464.0 kcal / 鸡胸肉 150g 199.5 kcal，合计 663.5 kcal …
      （数据库确认：新增 meal id=4，2 个 item，663.5 kcal）

用户：有没有热量更低的替换？帮我看看
助手：米饭 400g → 蒸红薯 少约 120 kcal；鸡胸肉 150g → 基围虾 少约 60 kcal …（Top3 来自规则引擎）
```




### 6.12 交付级护栏：限流、成本、可观测、健康检查

| 能力 | 实现 | 位置 |
|---|---|---|
| 每用户限流 | 20 次/分钟（解析/解释/问答/Agent/方案共用），超限 `429` | `ai/AiQuotaService` |
| 全局日 token 预算 | 50 万 token/天（超过后 AI 接口统一 429，规则能力不受影响） | `ai/AiQuotaService` |
| token 计量 | 每次模型返回的 `usage` 累加进指标，同时进预算 | `ai/assist/LlmClient` |
| 调用指标 | `mealwise_ai_calls_total{feature,source,result}`、`mealwise_ai_latency_seconds`、`mealwise_ai_tokens_total{feature,kind}` | `ai/AiMetrics` |
| 健康检查 | `/actuator/health`（公开，compose 探针）、`/actuator/prometheus`（需登录） | Spring Actuator |
| 前端兜底 | AI 助手弹窗内固定免责声明；降级时标注「本地规则 / 检索结果 / 未配置模型」 | `AiAssistantDialog.vue` |

实测：同一用户连续 21 次调用，第 21 次返回 `429`；`/actuator/prometheus` 里能看到
`mealwise_ai_calls_total{feature="parse-meal",source="rule"} 20` 与 token 计数。

### 6.13 P1 工程化：评测集、缓存、熔断、审计、撤销

| 能力 | 做法 | 位置 |
|---|---|---|
| **口语解析评测集** | 22 条真实说法（`eval/parse-cases.json`），逐条断言「状态 + 命中食物 + 克数 + 追问类型」，完全离线、确定性 | `src/test/resources/eval/parse-cases.json` + `NlParseEvalTest` |
| **RAG 检索评测** | 6 个问题 → 期望资料 id，断言 Top-5 命中率 ≥ 5/6 | `RagEvalTest` |
| **结果缓存** | 解释 5 分钟、问答 10 分钟（key 带 `mealId/strategy`、问题 SHA-256），命中不计费 | `ai/AiCache` |
| **熔断** | 连续 3 次模型失败 → 打开 60 秒，期间直接走降级（不再等超时） | `ai/AiCircuitBreaker` + `LlmClient` |
| **AI 审计** | 每次调用落 `ai_audit_log`（用户/能力/来源/结果/耗时，不存用户原文）；`GET /api/admin/ai-audit` 查看 | `ai/audit/*` + `docs/migration-v6-ai-audit.sql` |
| **撤销入账** | AI 录餐成功后弹「撤销这次入账」，调 `DELETE /api/meals/{id}` 连带删项 | `AiAssistantDialog.vue` + `MealService.delete` |
| **覆盖率** | JaCoCo 0.8.12，`mvn test` 后出 `target/site/jacoco/`；核心包行覆盖：推荐 97.6%、营养 100%、AI 辅助 68.5%、Agent 56.2%（全仓 37.8%，控制器未做单测） | `pom.xml` |
| **前端单测** | Vitest 5 + jsdom：拦截器 5 个用例（200 解包 / 401 清 token 跳登录 / 403 提示不跳转 / 500 兜底文案 / 400 用后端 message） | `src/api/request.test.ts` + `vitest.config.ts` |
| **数据库迁移** | Flyway：全新库启动时自动执行 `V1__init.sql`（建表 + 46 食物 + 演示账号）；已有库 `baseline-on-migrate` 标记 V1 后跳过，不重复建表 | `db/migration/V1__init.sql` + `application.yml` |
| **许可证** | MIT（素材与模型输出单列说明） | `LICENSE` |

> 测试用 `mock-maker-subclass`（`src/test/resources/mockito-extensions/`）：避免 Mockito 在受限环境里
> 依赖 self-attach，CI 与本机行为一致。


## 7. 已知限制 / 不做的事

| 项 | 说明 |
|---|---|
| 无 refresh token | 24h 单 token，登出只在客户端清 token（演示级取舍） |
| 食物库无权限体系 | 全局共享，所有登录用户可增删改（真实项目应加角色） |
| 无整餐组合优化 | 逐项推荐更可解释；组合优化是 NP 问题，收益不明确 |
| 餐次无列表接口 | 前端以"当前餐次"为工作单元，用 sessionStorage 恢复 |
| MySQL 5.7 兼容 | SQL 未用 CHECK 约束与 8.0 专有排序规则，5.7/8.0 均可跑 |
| 前端打包进 Jar | 单端口便于演示；生产更适合 Nginx 托管静态资源 |

---

## 8. 本地已验证

**构建与测试**（JDK 17.0.2 + Maven 3.9.9，本机实跑）

- `mvn -f mealwise-server/pom.xml test` → **85 个单测全绿**，0 failure / 0 error：
  - `MealServiceImplTest` 4 个：热量合计（米饭 200g + 鸡胸肉 150g = 431.5 kcal）、餐次不存在 404、**跨用户访问 403**、改他人餐次项 403
  - `ReplacementServiceImplTest` 4 个：higher 取 Top3 并按 |delta| 升序、lower 出现负 delta、过敏原过滤、无候选返回空数组
  - `NutritionTargetServiceTest` 4 个：Mifflin-St Jeor / TDEE / 三大营养素 / 资料不全返回 null
  - `JwtUtilTest` 4 个：签发、解析、篡改、过期
  - `MealTextParserTest` 5 个：切段/剥时间词、缺数量、显式克数、中文数字、模型 JSON
  - `FoodMatcherTest` 4 个：完全一致、别名、包含、未命中不瞎猜
  - `PortionResolverTest` 5 个：克数、份数×单位重量、一碗半、缺数量、无单位重量
  - `MealParseServiceTest` 5 个：整句解析（464 + 199.5 kcal）、UNKNOWN_FOOD、AMBIGUOUS_QUANTITY、模型可用、模型失败降级
  - `Bm25RetrieverTest` 3 个：晚餐常识召回、食物条目优先、无关问题返回空
  - `RecommendationExplainerTest` 3 个：规则回退、模型润色、模型异常回退
  - `MealAgentToolsTest` 9 个：过敏原过滤、额外过敏原、建餐返回 id、时间格式错误、克数校验、加项、热量汇总、替换转述、越权转错误文本
  - `MealAgentServiceTest` 3 个：无密钥禁用、空消息、sessionId 透传
  - `AiQuotaServiceTest` 4 个：20 次以内放行、第 21 次 429、按用户隔离、token 累计
  - `NlParseEvalTest` 22 个：口语评测集（命中食物 + 克数 + 追问类型）
  - `RagEvalTest` 1 个：6 个问题的 Top-5 检索命中率 ≥ 5/6
  - `AiResilienceTest` 3 个：缓存命中/过期、熔断打开与复位、按 feature 隔离
  - `AiAuditServiceTest` 2 个：审计字段完整 + 写失败不影响业务
- **覆盖率**（JaCoCo）：全仓行覆盖 **37.8%**（901/2382）、分支 32.4%；核心包：推荐服务 97.6%、营养目标 100%、AI 辅助 68.5%、Agent 56.2%。控制器与 AI 方案生成（联网部分）未做单测。
- **前端单测**：`pnpm -C mealwise-web test` → **Vitest 5/5 通过**（`src/api/request.test.ts`，拦截器四个分支 + 业务错误优先）。
- **Flyway**：空库启动 → `flyway_schema_history` 记录 `V1 / init / success`，5 张表 + 46 食物 + 3 账号全部自动建好；已有库启动 → 自动记录 `BASELINE` 并跳过迁移，原有数据不受影响。
- 测试注意：Mockito 已改为 `mock-maker-subclass`（`src/test/resources/mockito-extensions/`），
  不再依赖 self-attach；若在旧环境遇到 `Could not self-attach`，可临时加
  `-DargLine="-Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading"`。

**种子数据**（用 `docs/init.sql` 全新建库后实测）

| 项 | 数量 | 说明 |
|---|---|---|
| `food` | **46** | 8 个类别，含验收用例 米饭 116 / 鸡胸肉 133 / 西兰花 34 kcal per 100g |
| `sys_user` | 3 | demo(ADMIN) / other(USER) / allergy(USER, `egg`) |
| `meal` | 3 | meal 1、2 属于 demo（1 号：米饭 200g + 鸡胸肉 150g + 西兰花 100g），meal 3 属于 other |

**HTTP 端到端验收**（临时库 `mealwise_verify` + 打包后的 jar，端口 18080）

| 请求 | 结果 |
|---|---|
| `POST /api/auth/login` demo / other | `200`，均返回 JWT |
| `GET /api/meals/1/calories`（demo 的 token） | `200`，`{calories:465.5, protein:46.2, fat:5, carb:57}`，450 g |
| 同上，不带 token | `401` `未登录或登录已过期` |
| 同上，带伪造 token | `401` |
| `GET /api/meals/1/calories`（other 的 token） | **`403` `无权访问该餐次`** |
| `GET /api/meals/3/calories`（demo 的 token） | **`403`**（反方向同样隔离） |
| `GET /api/meals/3/calories`（other 的 token） | `200`，480 kcal |
| `GET /api/admin/users`（other / demo） | `403` / `200`（3 个账号） |

**交付护栏（P0）实测**

| 项 | 实测结果 |
|---|---|
| 限流 | 同一用户连续 21 次 `POST /api/ai/parse-meal`：前 20 次 `200`，第 21 次 **`429 AI 调用太频繁（每分钟最多 20 次）`** |
| 指标 | `GET /actuator/metrics/mealwise.ai.calls` → `200`，tags `feature/source/result`；`/actuator/prometheus` 可见 `mealwise_ai_calls_total{feature="parse-meal",source="rule"} 20.0` |
| token 计量 | 一次真实解析后 `mealwise_ai_tokens_total{kind="prompt"} 148`、`{kind="completion"} 17` |
| 健康检查 | `GET /actuator/health` → `200 {"status":"UP","groups":["liveness","readiness"]}`（公开，compose 探针可用） |
| 前端 AI 入口 | 顶部「AI 助手」→ 弹窗三页签（说一句录餐 / 对话助手 / 营养问答）+ 替换抽屉「AI 解释」；`vue-tsc` + `vite build` 通过 |
| 部署配置 | compose 传 `DEEPSEEK_API_KEY` 等环境变量，新增 `.env.example`；app 增加端口健康检查 |

**Docker 端到端实测**（本机 Docker Desktop 29.9.3 + Compose v5.7.0）

| 项 | 实测结果 |
|---|---|
| 容器状态 | `mealwise-mysql` / `mealwise-app` 均 **healthy**；`/actuator/health` → `{"status":"UP"}` |
| 数据初始化 | `docker-entrypoint-initdb.d` 执行 init.sql：46 食物 / 3 账号 / 3 餐次；App 启动日志 `Successfully baselined schema with version: 1` |
| 登录 | `POST /api/auth/login` demo/Demo@123 → `200` + JWT |
| 中文数据 | `GET /api/foods?keyword=米饭` → 命中「米饭」（`SET NAMES utf8mb4` 修复了容器内 latin1 乱码） |
| 规则链路 | `GET /api/meals/1/calories` → `465.5 kcal` |
| AI 链路 | `parse-meal` → `PARSED / source=llm`，米饭 400g 464 kcal + 鸡胸肉 150g 199.5 kcal；`ask` → `source=llm`，5 条引用 |
| 审计 | `GET /api/admin/ai-audit` → 2 条（ask 1189ms / parse-meal 1248ms） |

**P1 工程化实测**

| 项 | 实测结果 |
|---|---|
| 解析评测集 | `NlParseEvalTest` 22 条全过（22/22） |
| RAG 检索评测 | 6 题命中 6 题（Top-5） |
| 缓存 | 同一问题连续两次 `POST /api/ai/ask`：第一次 1 条审计，第二次 **0 条新增**（命中缓存，不再计费） |
| 熔断 | 单测：连续 3 次失败打开、60s 后半开、成功复位；按 feature 隔离 |
| AI 审计 | `GET /api/admin/ai-audit` 返回 `[{feature:"ask",source:"llm",result:"success",latencyMs:1213}, …]`；普通用户访问 → `403` |
| 撤销入账 | 建餐 → 加项 → `DELETE /api/meals/{id}` → `200`；再查热量 → **`404 餐次不存在`** |
| 覆盖率 | JaCoCo：全仓行 37.8%，推荐服务 97.6% / 营养 100% / AI 辅助 68.5% / Agent 56.2% |

**运行时 LLM 端到端**（真实 DeepSeek key，走 `ai-local.yml`，端口 18080）

| 请求 | 实测 |
|---|---|
| `POST /api/ai/parse-meal` `{"text":"中午吃了两碗米饭和一份鸡胸肉"}` | `PARSED`，source=**llm**；米饭 400g / 464 kcal、鸡胸肉 150g / 199.5 kcal（克数与热量都是 Java 算的） |
| `{"text":"吃了一碗海带汤"}` | `NEED_CLARIFICATION`，`UNKNOWN_FOOD` |
| `{"text":"晚上吃了西兰花"}` | `NEED_CLARIFICATION`，`AMBIGUOUS_QUANTITY` |
| `GET /api/meals/1/replacements/explain?strategy=higher` | `200`，source=**llm**，3 项；delta=84 与规则引擎一致，`ruleReason` 原样保留 |
| `POST /api/ai/ask` `{"question":"减脂期晚餐怎么吃"}` | `200`，source=**llm**，5 条引用，回答内含 [1][3][4][5] |
| `POST /api/ai/agent`「午饭吃了米饭 400 克、鸡胸肉 150 克」 | `200`，模型自主调用 searchFood→createMeal→addMealItem×2→calculateCalories，回复 663.5 kcal；**DB 确认新增 meal id=4、2 个 item、663.5 kcal** |
| 同会话「有没有热量更低的替换？」 | `200`，调用 getReplacements(lower)，返回 蒸红薯 −120 kcal / 基围虾 −60 kcal（规则引擎结果） |
| 同会话「我花生过敏，帮我找点坚果」 | 模型记住过敏原，searchFood 排除花生 |
| 同会话「我刚才说的目标是什么？」 | 正确回忆「减脂」——多轮记忆生效 |

**前端**

- `vue-tsc --noEmit` + `vite build` 通过，产物由后端托管（`/` 返回 `index.html`）
- Axios 拦截器：`401` 清 token + 跳登录；`403/404/500` 统一 `ElMessage` 提示（`src/api/request.ts`）
- 列表页有 `v-loading` 与 `EmptyState` 空状态（食物库、今日记录、餐次工作台）

**修复记录**

- `docs/init.sql` 的 `meal_item` 曾漏 `version` 列（实体有 `@Version`），导致全新安装后第一次算热量就 `500 Unknown column 'version'`。
  已在 init.sql 补列，并提供 `docs/migration-v5-meal-item-version.sql` 给老库。
- `docs/init.sql` 曾只有食物种子、没有演示账号 → README 里的 demo/other 登录与越权演示跑不起来。
  已补 `sys_user` + `meal` + `meal_item` 种子，并提供 `docs/migration-v4-demo-seed.sql`。
