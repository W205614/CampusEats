# 开发与源码阅读

本文以当前多校区版本的源码为准。建议先完成[首次部署](../README.md)，再跟踪一笔“加购 → 报价 → 合单 → 支付 → 履约”的请求。

## 后端目录

根目录 `pom.xml` 聚合三个 Maven 模块，Java 版本为 21。依赖方向为 `sky-server → sky-pojo / sky-common`。

| 目录                                                        | 职责与建议入口                                                                                                                     |
| ----------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `sky-common/src/main/java/com/sky/common/`                  | 公共异常 `BusinessException`、摘要工具 `Hashes`                                                                                    |
| `sky-pojo/src/main/java/com/sky/api/`                       | 请求 `Commands` 和响应 `Views`                                                                                                     |
| `sky-server/src/main/java/com/sky/web/`                     | HTTP 入口；按目录、结算、履约、售后和管理控制器阅读，再看认证与统一异常处理                                                        |
| `sky-server/src/main/java/com/sky/platform/`                | 多校区主业务：`PlatformScope` 权限、`StoreCart` 购物车、`CheckoutService` 合单、`FulfillmentService` 履约、`AftersaleService` 售后 |
| `sky-server/src/main/java/com/sky/finance/`                 | `LedgerService` 事务内业务流水、`FinanceService` 期初、日结预览、冻结账单及对账；平台账单用于经营核对，商家资金由渠道独立收取      |
| `sky-server/src/main/java/com/sky/payment/`                 | 持久支付意图、微信验签解密、查单关单、异步退款、商户快照与渠道账单；模拟与真实交易按渠道区分                                       |
| `sky-server/src/main/java/com/sky/storage/`、`integration/` | LOCAL/COS 文件和元数据、身份与短信/订阅适配器及接入状态                                                                            |
| `sky-server/src/main/java/com/sky/business/`                | 商品、地址、配额、缓存、支付接口等复用服务，也保留旧接口使用的交易服务                                                             |
| `sky-server/src/main/java/com/sky/security/`                | 员工／演示／微信登录、`LocalUserService` 独立测试账号、会话、角色校验与限流                                                        |
| `sky-server/src/main/java/com/sky/infra/`、`mapper/`        | 数据访问、配置、种子数据、后台维护与批量查询                                                                                       |
| `sky-server/src/main/java/com/sky/websocket/`               | 订单通知连接与一次性票据                                                                                                           |
| `sky-server/src/main/resources/`                            | 应用配置和 `db/migration/` 中的 Flyway 迁移                                                                                        |
| `sky-server/src/test/`                                      | 集成测试、并发及边界测试，与生产代码分开                                                                                           |

平台五类原控制器默认启用：`PlatformController`（目录发现）、`PlatformCheckoutController`、`PlatformFulfillmentController`、`PlatformAftersaleController`、`PlatformManagementController`；另有身份、支付、通知、存储及运维入口。旧交易控制器由 `campus.legacy-api=true` 条件启用，部分集成测试仍覆盖旧交易路径；账务 HTTP 入口与当前平台模式配套启用。`business/` 提供复用服务及旧接口实现，`platform/` 负责多校区流程。数据库变更使用新增 Flyway 迁移；V13—V17为业务能力追加，V18—V19补充余额对账索引，V20—V23增加生产接入及多实例数据，V24补充期间流水汇总覆盖索引。

控制器只协调 HTTP 与服务。`ArchitectureBoundaryTest` 检查控制器不持有 `Db`，并检查指定核心服务不依赖 `web` 视图。配额、金额和退款算法继续复用现有服务。

## 前端与接口

| 目录                       | 从哪里开始                                                                                                                                                         |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `frontend/admin/src/`      | `main.ts → router.ts → pages/`；`platform-api.ts` 发起平台业务请求，`stores/session.ts` 保存员工会话                                                               |
| `frontend/client/src/`     | `main.ts → pages.json → pages/menu/、cart/、checkout/、orders/`；`login/、account/、messages/` 负责账号、分页钱包与站内消息；`api.ts` 处理 uni-app 请求与 API 地址 |
| `frontend/contracts/`      | 共享模型、错误与分页工具；`openapi.json` 为接口快照，`openapi.ts` 为生成类型                                                                                       |
| `frontend/admin/tests/`    | Playwright 场景，包含用户端与工作台流程，运行前确认隔离环境                                                                                                        |
| `frontend/client/tooling/` | 锁定 uni-app 工具链的兼容包、补丁与回归；[用途和升级规则](../frontend/client/tooling/README.md)                                                                    |

## 按业务追踪请求

下表按默认多校区模式列出入口。用户端由 `api.ts` 发请求，员工端由 `platform-api.ts` 发请求，控制器和服务均位于 `sky-server/src/main/java/com/sky/`。

| 场景                 | 从页面追到服务                                                                                                                                                                                                                                                                                      | 关键操作                                                                                                                                                     |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 购物车增减餐品       | [菜单](../frontend/client/src/pages/menu/index.vue)或[购物车](../frontend/client/src/pages/cart/index.vue) → `POST /api/v1/user/stores/{storeId}/cart/items` → `PlatformCheckoutController` → [StoreCart.change](../sky-server/src/main/java/com/sky/platform/StoreCart.java)                       | `platform.StoreCart` 按用户和店铺锁定购物车、检查版本；加购时调用 `business.CartService.validate` 校验商品与口味。                                           |
| 合单报价、提交与支付 | [结算页](../frontend/client/src/pages/checkout/index.vue) → `PlatformCheckoutController` → [CheckoutService.preview / submit](../sky-server/src/main/java/com/sky/platform/CheckoutService.java)；[订单页](../frontend/client/src/pages/orders/index.vue) → 同一控制器 → `CheckoutService.pay`      | 报价读取各店购物车，提交时创建子单、调用 `business.QuotaService.reserve` 预占配额并清空对应购物车；支付成功后由 `finance.LedgerService.payment` 逐子单记账。 |
| 店长接单和自取核销   | [订单履约页](../frontend/admin/src/pages/Orders.vue) → [PlatformFulfillmentController](../sky-server/src/main/java/com/sky/web/PlatformFulfillmentController.java) → [FulfillmentService](../sky-server/src/main/java/com/sky/platform/FulfillmentService.java)                                     | `action` 处理接单，核对店铺权限与订单版本，再调用 `business.QuotaService.transition` 确认配额；自取码核销走单独的 `verify` 方法。                            |
| 查经营报表           | [经营报表页](../frontend/admin/src/pages/Reports.vue) → [PlatformManagementController](../sky-server/src/main/java/com/sky/web/PlatformManagementController.java) → `DiscoveryService.reportView` → [ReportQueryService.view](../sky-server/src/main/java/com/sky/platform/ReportQueryService.java) | 工作台按店铺或平台范围取数，`platform.ReportQueryService` 负责聚合。旧接口由 `CatalogController` 调用 `business.ReportService`，需开启 `campus.legacy-api`。 |

追数据库结构可从 [V5 多校区迁移](../sky-server/src/main/resources/db/migration/V5__multi_campus_platform.sql)开始，按版本往后看。权限范围、合单与售后规则见[平台架构](platform-architecture.md)。

其他能力的源码入口如下。

| 要理解的能力 | 代码链与关键约束                                                                                                                                  |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| 独立测试账号 | `LocalUserController → LocalUserService → AuthService`；平台创建，首次及重置后改密，账号启停与会话撤销复用原用户归属，不开放注册                  |
| 时限和异常   | `PlatformScope.event → FulfillmentStages` 保存阶段快照；`OperationsJobs → OperationsService` 有界扫描、确认跟进与人员交接；历史异常保留发生时快照 |
| 持久消息     | 同事务的 `BusinessMessages` 生成接收账号消息，`InboxService` 每次检查当前权限；配送消息有履约白名单，WebSocket不承担必达确认                      |
| 模拟账务     | `CheckoutService / PlatformJobs → LedgerService` 成功落账；`FinanceService` 读取期初、冻结已结束日并对账，后续退款不改旧账单                      |
| 集合分页     | `CollectionPages / CursorPages / DiscoveryCursor`；签名范围绑定身份与筛选，前端两端 `paged-list.ts` 隔离旧账号、旧筛选响应                        |

管理端新增 `Users.vue`、`ServiceOperations.vue`、`Inbox.vue`、`Finance.vue`。订单的用户、商家和配送响应分开；配送完成、取消或改派后的字段与访问权限由服务端投影控制，验证时覆盖这三种状态。抢单楼栋使用下单快照，楼栋改名后不回查新名称覆盖它。

服务时限默认10／30／15／15／60／120分钟，保存在阶段开始时；策略修改不追溯改变在途订单。不可用配送员的任务可以由平台改派，取餐前撤回分配后应解除对应异常。超时本身不自动退款或取消。期初快照只表达升级时已有的支付与退款余额，不伪造历史流水；流水与冻结日结继续保留整数分、来源防重和负净额。

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

公开能力接口 `/api/v1/user/auth/capabilities` 控制登录入口。`.env` 的 `APP_MODE=demo` 保留演示身份；`trial` 禁用共享演示登录，仍然使用模拟资金。`demo` 和 `trial` 互斥；微信身份配置与验收见[身份接入](identity-notifications.md)。

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

终端二的 `VITE_API_BASE` 让 API 和演示图片指向 Docker 入口，仅用于该开发终端；正式 H5 构建默认保持同源，不把本机地址写进源码。端口调整后同步修改 API 地址、代理与允许的 Origin。开发服务器也会访问该后端的实际数据，写入式验收应先按[独立验收说明](platform-deployment.md#独立验收)准备隔离环境。Compose 不公开 MySQL／Redis 端口，直接在 IDE 启动后端还需自行配置可访问的数据库、Redis 和运行环境。

工作台的 Vite 配置没有代理 `/demo-images/`，编辑商品时默认演示图片可能无法在开发端口显示；完整静态资源展示应在 Docker 构建后的工作台检查。

## 微信小程序

1. 构建后将 `frontend/client/dist/build/mp-weixin/` 导入微信开发者工具。
2. 本机工具预览与正式发布的要求不同。生产构建按[小程序接入步骤](production-readiness.md#小程序生产构建)设置进程环境中的 `WECHAT_APPID` / `PUBLIC_BASE_URL`，运行 `build:mp-weixin:production`，不手改生成目录或源 manifest。
3. 在微信后台配置 request、uploadFile、downloadFile 合法域名，导入与该次 AppID/API 源匹配的构建产物；开发联调仍可使用被忽略的 `frontend/client/.env.local`。
4. 微信登录所需 `WECHAT_APPID`、`WECHAT_SECRET` 仅配置在后端本机环境，Secret 不进入前端或 Git。

H5 默认使用同源 API。默认演示使用模拟支付；生产使用独立微信支付流程，前端只展示服务端确认的结果。微信登录、付款和订阅消息按[接入指南](production-readiness.md)在真机验收。

## 本机文件与验证范围

根目录保留 Maven、Docker、Git 和编辑器需要默认发现的入口配置；部署文件与运维配置模板集中在 `deploy/`，应用配置及前端构建配置留在所属模块，见[部署配置导航](../deploy/README.md)。不将所有配置搬入统一目录后再为每个工具补路径。

`target/`、`node_modules/`、`dist/` 和浏览器报告由工具生成，受 Git 忽略。本机证据目录由[当前版本记录](verification/current.md)列明，制品发布记录绑定提交与 CI run。备份停用状态、临时材料回收及配置、工具、业务数据保留规则统一见[本机保留策略](platform-deployment.md#本机保留策略)。

本地只验证改动直接影响的模块或流程；提交后以该提交的 GitHub Actions 结论为准。写入测试使用 Testcontainers 或独立 Compose 凭据、端口、镜像及数据卷，不指向日常数据库。修改数据库时，既保留历史迁移的固定目标测试，也在升级测试里按原列比对旧记录，另行核对新阶段、消息和期初记录，将旧字段一致性与新增业务数据分别核对。

例如，仅在修改账务和履约运营时选择相关测试类，实际执行仍按改动决定，不默认全量运行：

```powershell
mvn -B -ntp -pl sky-server -am '-Dtest=FinanceIntegrationTest,OperationsIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

页面模拟传输测试覆盖乱序响应等组件边界，角色体验通过真实服务与浏览器验证。真实体验使用独立浏览器会话和真实服务；API只准备基础夹具及核对结果，新增或修改的关键业务动作从页面操作，保留脱敏证据。未修改的其它全量流程、恢复和安全门禁交给对应SHA的CI；源码测试、真实页面、制品来源和日常部署核验分别记录。当前进度和已获得的证据见[当前版本验证与维护记录](verification/current.md)。
