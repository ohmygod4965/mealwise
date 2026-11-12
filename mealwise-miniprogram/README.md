# Mealwise 微信小程序

与 Web 端**共用同一个后端**（`mealwise-server`），不重复实现业务逻辑，只做界面与调用。

## 页面

| 页面 | 路径 | 功能 |
|---|---|---|
| 今日 | `pages/day/day` | 日期切换、当日摄入合计、摄入 vs 建议（进度条+状态）、早/午/晚/加餐分区 |
| 食物库 | `pages/foods/foods` | 关键词搜索、分页加载、热量与过敏原标签 |
| 记录一餐 | `pages/meal/meal` | 选餐次 → 搜食物 → 加 100g → 改克数/删除 → 实时期小计 |
| 我的资料 | `pages/profile/profile` | 性别 / 身高 / 体重 / 年龄，保存后回到今日页刷新目标 |
| 登录 | `pages/login/login` | 登录 / 注册（含过敏原），成功后进今日页 |

## 运行前必改一处

`app.js` 里的后端地址：

```js
globalData: {
  baseUrl: 'http://10.167.168.18:8080',   // ← 改成你电脑的局域网 IP
  ...
}
```

- 查本机 IP：`ipconfig`，看 WLAN 的 IPv4 地址
- 手机与电脑必须在**同一 Wi-Fi**
- 后端要监听 0.0.0.0（Spring Boot 默认如此），且防火墙放行 8080

## 导入步骤（微信开发者工具）

1. 打开「微信开发者工具」→ 导入项目
2. 目录选 `mealwise-miniprogram`，AppID 填你自己的（或选「测试号」）
3. 右上角「详情 → 本地设置」勾选 **不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**
   - 这一步是必须的：小程序默认只允许 https + 备案域名，本地 `http://IP:8080` 会被拦
4. 点「编译」，用 `demo / Demo@123` 登录即可

## 真机预览

- 开发者工具点「预览」→ 手机扫码
- **必须使用你自己的 AppID**（测试号不支持真机预览）
- 手机与电脑同一网段时，`http://局域网IP:8080` 可直连；若不通，先检查 Windows 防火墙入站规则

## 发布上线还需要

| 项 | 说明 |
|---|---|
| 合法域名 | 小程序后台「开发设置 → 服务器域名」配置 `request` 域名，**必须 https 且已备案** |
| 生产地址 | 把 `baseUrl` 换成你的域名（如 `https://meal.example.com`），并去掉端口 |
| 用户体系 | 目前复用账号密码登录；小程序通常改成 `wx.login` + 后端换 openid 的静默登录 |
| 审核类目 | 健康/饮食类内容可能要求资质，建议先做工具类定位 |

## 与 Web 端的差异

- 未做食物库的增删改（只读搜索）——移动端主要用于记录与查看
- 替换建议页未搬（接口已就绪：`GET /meals/{id}/replacements`，加一个页面即可）
- 登录态存 `wx.setStorageSync`，没有 refresh token（后端本身也没有）

## 代码结构

```
mealwise-miniprogram/
├── app.js / app.json / app.wxss     # 全局配置、登录态、设计变量
├── project.config.json              # 导入配置（urlCheck: false）
├── sitemap.json
├── utils/
│   ├── request.js                   # wx.request 封装：Bearer、401 跳登录、错误 toast
│   └── format.js                    # 日期/数字/餐次文案
└── pages/{login,day,foods,meal,profile}/   # 每页 js/wxml/wxss/json
```

## 已验证 / 未验证

- ✅ 8 个 JSON 配置文件均可解析；8 个 JS 文件 `node --check` 语法通过
- ✅ 后端经局域网 IP `http://10.167.168.18:8080` 可达（未带 token 返回 401，符合预期）
- ❌ **未在微信开发者工具/真机实际运行**——本机没有安装开发者工具，无法做运行时验证。
  首次导入若报错，最可能是「域名校验」或 `baseUrl` 未改成自己的 IP。
