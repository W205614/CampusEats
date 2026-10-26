# 开发与源码阅读

本文以当前多校区版本的源码为准。建议先完成[首次部署](../README.md)，再跟踪一笔“加购 → 报价 → 合单 → 支付 → 履约”的请求。

## 后端目录

根目录 `pom.xml` 聚合三个 Maven 模块，Java 版本为 21。依赖方向为 `sky-server → sky-pojo / sky-common`。

| 目录 | 职责与建议入口 |
|---|---|
| `sky-common/src/main/java/com/sky/common/` | 公共异常 `BusinessException`、摘要工具 `Hashes` |
| `sky-pojo/src/main/java/com/sky/api/` | 请求 `Commands` 和响应 `Views` |
| `sky-server/src/main/java/com/sky/web/` | HTTP 入口；按目录、结算、履约、售后和管理控制器阅读，再看认证与统一异常处理 |
| `sky-server/src/main/java/com/sky/platform/` | 多校区主业务：`PlatformScope` 权限、`StoreCart` 购物车、`CheckoutService` 合单、`FulfillmentService` 履约、`AftersaleService` 售后 |
| `sky-server/src/main/java/com/sky/business/` | 商品、地址、配额、缓存、支付接口等复用服务，也保留旧接口使用的交易服务 |
| `sky-server/src/main/java/com/sky/security/` | 登录、会话、角色校验与限流 |
| `sky-server/src/main/java/com/sky/infra/`、`mapper/` | 数据访问、配置、种子数据、后台维护与批量查询 |
| `sky-server/src/main/java/com/sky/websocket/` | 订单通知连接与一次性票据 |
| `sky-server/src/main/resources/` | 应用配置和 `db/migration/` 中的 Flyway 迁移 |
| `sky-server/src/test/` | 集成测试、并发及边界测试，与生产代码分开 |

平台五类控制器默认启用：`PlatformController`（目录发现）、`PlatformCheckoutController`、`PlatformFulfillmentController`、`PlatformAftersaleController`、`PlatformManagementController`；旧交易控制器由 `campus.legacy-api=true` 条件启用，部分集成测试仍覆盖旧交易路径。`business/` 与 `platform/` 并非可以整目录删除的重复实现。不要修改已执行的 Flyway 迁移；数据库变化应新增迁移。

## 前端与接口

| 目录 | 从哪里开始 |
|---|---|
| `frontend/admin/src/` | `main.ts → router.ts → pages/`；`platform-api.ts` 发起平台业务请求，`stores/session.ts` 保存员工会话 |
| `frontend/client/src/` | `main.ts → pages.json → pages/menu/、cart/、checkout/、orders/`；`api.ts` 处理 uni-app 请求与 API 地址 |
| `frontend/contracts/` | 共享模型、错误与分页工具；`openapi.json` 为接口快照，`openapi.ts` 为生成类型 |
| `frontend/admin/tests/` | Playwright 场景，包含用户端与工作台流程，运行前确认隔离环境 |
| `frontend/client/tooling/` | 锁定 uni-app 工具链的兼容包、补丁与回归；[用途和升级规则](../frontend/client/tooling/README.md) |

阅读顺序：用户 `pages/checkout/index.vue` → `api.ts` → 后端 `PlatformCheckoutController` → `CheckoutService` → `StoreCart`、`Money`、`PlatformScope` → `V5__multi_campus_platform.sql`。随后看 `FulfillmentService` 和 `AftersaleService`，对照[架构规则](platform-architecture.md)理解事务和权限边界。

## 本地构建与前端开发

首次体验只需 Docker，不需要安装本机开发工具。修改源码时，后端本机验证需 Java 21、Maven；前端使用与 [CI](../.github/workflows/verify.yml) 一致的 Node 版本，使用 lockfile 安装依赖。

以下命令在项目根目录的 PowerShell 7 执行：

```powershell
# 安装两端依赖，检查兼容层及类型，构建管理端、H5 和小程序
pwsh -NoProfile -File ./scripts/build-frontends.ps1

# 从仓库已有 OpenAPI 快照重新生成类型，不请求正在运行的服务
node ./scripts/generate-contracts.mjs
```

只修改单端时，可在相应目录使用 `package.json` 中的构建或类型检查命令，不必每次重建两端。`openapi.ts` 不手工编辑，接口变化时同步快照和生成类型。

前端开发服务器代理到已启动的 Docker 后端。先在本机 `.env` 的 `ALLOWED_ORIGINS` 里保留原地址并加入 `http://127.0.0.1:5173,http://127.0.0.1:5174`，再运行 `docker compose up -d server` 使配置生效。在两个终端分别执行：

```powershell
# 终端一：员工工作台 http://127.0.0.1:5173/admin/
$env:API_PROXY = 'http://localhost:18083'
npm run dev --prefix frontend/admin
```

```powershell
# 终端二：用户 H5 http://127.0.0.1:5174/app/
$env:API_PROXY = 'http://localhost:18083'
$env:VITE_API_BASE = 'http://localhost:18083'
npm run dev:h5 --prefix frontend/client
```

终端二的 `VITE_API_BASE` 让 API 和演示图片指向 Docker 入口，仅用于该开发终端；正式 H5 构建默认保持同源，不把本机地址写进源码。端口调整后同步修改 API 地址、代理与允许的 Origin。开发服务器也会访问该后端的实际数据，写入式验收应先按[独立验收说明](platform-deployment.md#独立验收)准备隔离环境。Compose 不公开 MySQL／Redis 端口，直接在 IDE 启动后端还需自行配置可访问的数据库、Redis 和运行环境；不能只启动 Java 就认为拥有完整演示环境。

工作台的 Vite 配置没有代理 `/demo-images/`，编辑商品时默认演示图片可能无法在开发端口显示；完整静态资源展示应在 Docker 构建后的工作台检查。

## 微信小程序

1. 构建后将 `frontend/client/dist/build/mp-weixin/` 导入微信开发者工具。
2. 按账号权限配置自己的 AppID；本机工具预览与正式发布的要求不同。
3. 设备访问需在 `frontend/client/.env.local` 设置 `VITE_API_BASE` 为可访问的 HTTPS API 地址，并重新构建。按需同步配置 `src/manifest.json` 的 AppID 与微信合法 request 域名。
4. 微信登录所需 `WECHAT_APPID`、`WECHAT_SECRET` 仅配置在后端本机环境，Secret 不进入前端或 Git。

H5 默认使用同源 API。H5 和小程序构建成功不代表微信登录、真机或真实资金交易已经通过验收；当前支付和退款仍为模拟实现。

## 本机文件与验证范围

`target/`、`node_modules/`、`dist/`、浏览器报告由工具生成，受 Git 忽略。`.env`、`.local/` 中可能有密码、用户数据、备份及回滚证据，不能作为“多余文件”批量删除。

本地只验证改动直接影响的模块或流程；提交后以该提交的 GitHub Actions 结论为准。构建、类型、契约、依赖审计、隔离业务验收和备份恢复的完整步骤见 CI，已有证据见[验收索引](verification/README.md)。
