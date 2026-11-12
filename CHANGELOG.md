# Changelog

## [Unreleased] — 2026-11-11

### Added
- **自然语言录餐**：`POST /api/ai/parse-meal`，模型只抽取食物与数量，匹配/换算/热量由 Java 完成；匹配不到或克数不明返回追问。
- **替换建议解释**：`GET /api/meals/{id}/replacements/explain`，规则 Top3 不变，LLM 仅润色理由；失败回退规则原文。
- **营养知识问答（RAG）**：`POST /api/ai/ask`，BM25 检索食物库 + 7 条营养常识，回答带引用。
- **LangChain4j Agent**：`POST /api/ai/agent`，`@Tool` 注册 searchFood / createMeal / addMealItem / calculateCalories / getReplacements；模型自主编排，`sessionId` 维度多轮记忆。
- **AI 护栏**：每用户 20 次/分钟限流、全局日 token 预算、Micrometer 指标、`/actuator/health` 健康检查。
- **前端 AI 助手**：顶部入口，三页签（说一句录餐 / 对话助手 / 营养问答）+ 替换抽屉「AI 解释」；免责声明。
- **交付物料**：`LICENSE`（MIT）、`.env.example`、compose 注入 AI 环境变量与 app 健康检查、`docs/openapi.json` 重新导出。

### P1（工程化）
- **口语解析评测集**：22 条真实说法 `eval/parse-cases.json` + `NlParseEvalTest`，断言状态/食物/克数/追问类型。
- **RAG 检索评测**：6 个问题断言 Top-5 命中（`RagEvalTest`）。
- **结果缓存**：`AiCache`，问答 10 分钟、解释 5 分钟，命中不计费。
- **熔断**：`AiCircuitBreaker`，连续 3 次失败打开 60 秒，期间自动降级。
- **AI 审计**：`ai_audit_log` 表 + `GET /api/admin/ai-audit`；写失败不影响业务。
- **撤销入账**：`DELETE /api/meals/{mealId}` + 前端「撤销这次入账」。
- **覆盖率**：JaCoCo，`mvn test` 输出报告；测试改用 `mock-maker-subclass`。
- **前端单测**：Vitest + jsdom，覆盖 Axios 拦截器 200/401/403/500/400 五个分支（`pnpm test`）。
- **Flyway 迁移**：`V1__init.sql` 作为基线，全新库启动自动建表+种子；已有库 `baseline-on-migrate` 自动跳过。
- **文档**：`CHANGELOG.md`、API 文档补 11.6–11.8、GUIDE 迁移索引补 v6。

### Changed
- `dsh` 无关；后端新增 `langchain4j` / `langchain4j-open-ai` 1.23.0、`spring-boot-starter-actuator`、`micrometer-registry-prometheus`。
- `Dockerfile` 改为多阶段构建（容器内 `mvn package`）。
- `docs/init.sql`：补 `meal_item.version`、演示账号与演示餐次。

### Fixed
- 全新安装后 `GET /api/meals/{id}/calories` 报 `Unknown column 'version'`（第 5 个迁移脚本修复）。
- 演示账号 demo/other/allergy 未入库导致越权演示无法复现（第 4 个迁移脚本修复）。
- **容器内中文种子乱码**：`docs/init.sql` / `V1__init.sql` 开头补 `SET NAMES utf8mb4`，修复 MySQL 容器默认 latin1 导致「米饭」等名称乱码、AI 解析匹配不到的问题。
- Docker 构建阶段与运行阶段增加 `-Djava.net.preferIPv4Stack=true`，避免 IPv6 不通时 Maven / 模型调用卡住。

### Verified
- **Docker 全链路**：Docker Desktop 29.9.3 + Compose v5.7.0，`mysql` / `app` 两容器 healthy；登录、中文检索、热量、AI 录餐、RAG 问答、审计均实测通过。
