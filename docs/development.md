# 开发与源码阅读

本文以当前多校区版本的源码为准。建议先完成[首次部署](../README.md)，再跟踪一笔“加购 → 报价 → 合单 → 支付 → 履约”的请求。

## 后端目录

根目录 `pom.xml` 聚合三个 Maven 模块，Java 版本为 21。依赖方向为 `campuseats-server → campuseats-api / campuseats-common`。

Java 包名为 `com.campuseats`，应用入口为 `CampusEatsApplication`。模块名称描述各自职责：`common` 放公共异常与摘要工具，`api` 放请求和响应类型，`server` 实现点餐、履约、账务与外部服务接入。

命名整理没有改变现有数据库标识 `sky_take_out`、Flyway V1—V24 的内容或 Compose 业务卷；这些是既有数据和备份脚本使用的标识。升级时保留原数据，不需要改库名或重新初始化。历史发布回执和 `v1.0.0` 标签仍按当时的目录与 Maven 坐标记录。

| 目录                                                        | 职责与建议入口                                                                                                                     |
| ----------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `campuseats-common/src/main/java/com/campuseats/common/`                  | 公共异常 `BusinessException`、摘要工具 `Hashes`                                                                                    |
| `campuseats-api/src/main/java/com/campuseats/api/`                       | 请求 `Commands` 和响应 `Views`                                                                                                     |
| `campuseats-server/src/main/java/com/campuseats/web/`                     | HTTP 入口；按目录、结算、履约、售后和管理控制器阅读，再看认证与统一异常处理                                                        |
| `campuseats-server/src/main/java/com/campuseats/platform/`                | 多校区主业务：`PlatformScope` 权限、`StoreCart` 购物车、`CheckoutService` 合单、`FulfillmentService` 履约、`AftersaleService` 售后 |
| `campuseats-server/src/main/java/com/campuseats/finance/`                 | `LedgerService` 事务内业务流水、`FinanceService` 期初、日结预览、冻结账单及对账；平台账单用于经营核对，商家资金由渠道独立收取      |
| `campuseats-server/src/main/java/com/campuseats/payment/`                 | 持久支付意图、微信验签解密、查单关单、异步退款、商户快照与渠道账单；模拟与真实交易按渠道区分                                       |
| `campuseats-server/src/main/java/com/campuseats/storage/`、`integration/` | LOCAL/COS 文件和元数据、身份与短信/订阅适配器及接入状态                                                                            |
| `campuseats-server/src/main/java/com/campuseats/business/`                | 商品、地址、配额、缓存、支付接口等复用服务，也保留旧接口使用的交易服务                                                             |
| `campuseats-server/src/main/java/com/campuseats/security/`                | 员工／演示／微信登录、`LocalUserService` 独立测试账号、会话、角色校验与限流                                                        |
| `campuseats-server/src/main/java/com/campuseats/infra/`、`mapper/`        | 数据访问、配置、种子数据、后台维护与批量查询                                                                                       |
| `campuseats-server/src/main/java/com/campuseats/websocket/`               | 订单通知连接与一次性票据                                                                                                           |
| `campuseats-server/src/main/resources/`                            | 应用配置和 `db/migration/` 中的 Flyway 迁移                                                                                        |
| `campuseats-server/src/test/`                                      | 集成测试、并发及边界测试，与生产代码分开                                                                                           |

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

下表按默认多校区模式列出入口。用户端由 `api.ts` 发请求，员工端由 `platform-api.ts` 发请求，控制器和服务均位于 `campuseats-server/src/main/java/com/campuseats/`。

| 场景                 | 从页面追到服务                                                                                                                                                                                                                                                                                      | 关键操作                                                                                                                                                     |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 购物车增减餐品       | [菜单](../frontend/client/src/pages/menu/index.vue)或[购物车](../frontend/client/src/pages/cart/index.vue) → `POST /api/v1/user/stores/{storeId}/cart/items` → `PlatformCheckoutController` → [StoreCart.change](../campuseats-server/src/main/java/com/campuseats/platform/StoreCart.java)                       | `platform.StoreCart` 按用户和店铺锁定购物车、检查版本；加购时调用 `business.CartService.validate` 校验商品与口味。                                           |
| 合单报价、提交与支付 | [结算页](../frontend/client/src/pages/checkout/index.vue) → `PlatformCheckoutController` → [CheckoutService.preview / submit](../campuseats-server/src/main/java/com/campuseats/platform/CheckoutService.java)；[订单页](../frontend/client/src/pages/orders/index.vue) → 同一控制器 → `CheckoutService.pay`      | 报价读取各店购物车，提交时创建子单、调用 `business.QuotaService.reserve` 预占配额并清空对应购物车；支付成功后由 `finance.LedgerService.payment` 逐子单记账。 |
| 店长接单和自取核销   | [订单履约页](../frontend/admin/src/pages/Orders.vue) → [PlatformFulfillmentController](../campuseats-server/src/main/java/com/campuseats/web/PlatformFulfillmentController.java) → [FulfillmentService](../campuseats-server/src/main/java/com/campuseats/platform/FulfillmentService.java)                                     | `action` 处理接单，核对店铺权限与订单版本，再调用 `business.QuotaService.transition` 确认配额；自取码核销走单独的 `verify` 方法。                            |
| 查经营报表           | [经营报表页](../frontend/admin/src/pages/Reports.vue) → [PlatformManagementController](../campuseats-server/src/main/java/com/campuseats/web/PlatformManagementController.java) → `DiscoveryService.reportView` → [ReportQueryService.view](../campuseats-server/src/main/java/com/campuseats/platform/ReportQueryService.java) | 工作台按店铺或平台范围取数，`platform.ReportQueryService` 负责聚合。旧接口由 `CatalogController` 调用 `business.ReportService`，需开启 `campus.legacy-api`。 |

追数据库结构可从 [V5 多校区迁移](../campuseats-server/src/main/resources/db/migration/V5__multi_campus_platform.sql)开始，按版本往后看。权限范围、合单与售后规则见[平台架构](platform-architecture.md)。

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

首次 Docker 体验需要 Git、PowerShell 7、Docker 与 Compose，无需宿主 Java/Node。宿主开发按组件安装 Java 21、Maven 3.9.9 或 Node.js 24.19.0，使用 lockfile 安装前端依赖。本轮宿主 Java 的实际验证环境是 Windows、Java 21、PowerShell 7.6.9；Linux 宿主 Java、macOS 和其他架构未逐项验证。

<a id="host-source"></a>

### 宿主 Java 与独立数据库

下面使用独立项目 `campuseats-source-dev`、MySQL `13384`、Redis `16384`、Java `18084`。先确认端口可用，只操作本节的新环境，不复用日常业务卷。Compose 原文件不公开数据库端口，因此需要本节的回环映射。

在根目录的 PowerShell 7 执行一次准备；环境文件已存在时保留它，后续从“启动依赖”继续：

```powershell
. ./scripts/secure-files.ps1
$devDirectory = New-SecureDirectory -Path (Join-Path $PWD '.local/source-dev')
$devEnvironment = Join-Path $devDirectory 'dev.env'
if (Test-Path -LiteralPath $devEnvironment) { throw '保留已有开发配置，不重新生成数据库口令。' }
$devConfiguration = Get-Content .env.example -Raw
foreach ($purpose in @('root-password','app-password','redis-password','admin-signing-key','demo-password')) {
  $random = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
  $devConfiguration = $devConfiguration.Replace("replace-with-random-$purpose", $random)
}
$devConfiguration = $devConfiguration.Replace('MYSQL_IMAGE=campuseats-mysql:v1', 'MYSQL_IMAGE=campuseats-mysql:v1.0.4-source-dev')
$devConfiguration = $devConfiguration.Replace('REDIS_IMAGE=campuseats-redis:v1', 'REDIS_IMAGE=campuseats-redis:v1.0.4-source-dev')
Write-SecureTextFile -Path $devEnvironment -Content $devConfiguration
Write-SecureTextFile -Path (Join-Path $devDirectory 'ports.yaml') -Content "services:`n  mysql:`n    ports: ['127.0.0.1:13384:3306']`n  redis:`n    ports: ['127.0.0.1:16384:6379']`n"
$devConfiguration = $null
$random = $null
```

启动依赖，只启动 MySQL/Redis 两个服务，不启动容器中的 Java/Web：

```powershell
$devCompose = @('compose', '--env-file', '.local/source-dev/dev.env', '-p', 'campuseats-source-dev', '-f', 'compose.yaml', '-f', '.local/source-dev/ports.yaml')
docker @devCompose build --pull mysql redis
docker @devCompose up -d --no-build --wait --wait-timeout 180 mysql redis
```

`.env` 不会自动加载到宿主 Java。在启动 Java 的同一终端显式设置：

```powershell
$devValues = ConvertFrom-StringData (Get-Content .local/source-dev/dev.env -Raw)
$env:SPRING_PROFILES_ACTIVE = 'demo'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '18084'
$env:DB_URL = 'jdbc:mysql://127.0.0.1:13384/sky_take_out?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USER = 'campus'
$env:MYSQL_PASSWORD = $devValues.MYSQL_PASSWORD
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '16384'
$env:REDIS_PASSWORD = $devValues.REDIS_PASSWORD
$env:JWT_ADMIN_SECRET = $devValues.JWT_ADMIN_SECRET
$env:DEMO_ADMIN_PASSWORD = $devValues.DEMO_ADMIN_PASSWORD
$env:UPLOADS_PATH = Join-Path $PWD '.local/source-dev/uploads'
$env:ALLOWED_ORIGINS = 'http://127.0.0.1:18084,http://127.0.0.1:5173,http://127.0.0.1:5174'
$devValues = $null
mvn -B -ntp -DskipTests install
mvn -B -ntp -f campuseats-server/pom.xml spring-boot:run
```

`install` 只在本机 Maven 仓库解析源码模块依赖，不向远端发布应用包；此处跳过测试，完整检查另见贡献指南。首次 Java 启动执行 Flyway V1—V24，演示种子仅在相应模式初始化。数据库名 `sky_take_out` 是保留标识，不重命名现有数据库。

在另一终端检查 `http://127.0.0.1:18084/actuator/health/readiness` 与 `/api/v1/user/auth/capabilities`，应分别为 `UP`、`DEMO` / `MOCK`。本轮实际执行了安装、源码启动、健康、管理员与演示用户登录及菜单读取，确认 V24 与 69 张表；复用了同配方的既有精确依赖镜像，没有宣称本次在 Windows 重新构建这两个依赖镜像。

本地 JDBC 示例的非 TLS 连接仅适用于此回环隔离开发环境。Java 在前台运行，以 Ctrl+C 停止；IDE 可导入根 POM、配置同组环境变量并从 `CampusEatsApplication` 调试。IDE 调试器本轮未单独验收。数据库暂停使用 `docker @devCompose stop --timeout 60`，保留开发数据卷；清理或重置另行决定，不删除日常卷。

<a id="frontend-dev"></a>

### 前端热更新

如需一次完成双端安装、兼容层/类型检查和所有目标构建，可在根目录的 PowerShell 7 选用以下检查入口。日常热更新直接从后文 `npm ci` 开始，不必先全量构建：

```powershell
# 安装两端依赖，检查兼容层及类型，构建管理端、H5 和小程序
pwsh -NoProfile -File ./scripts/build-frontends.ps1

# 从仓库已有 OpenAPI 快照重新生成类型，不请求正在运行的服务
node ./scripts/generate-contracts.mjs
```

只修改单端时，可在相应目录使用 `package.json` 中的构建或类型检查命令，不必每次重建两端。`openapi.ts` 不手工编辑，接口变化时同步快照和生成类型。

公开能力接口 `/api/v1/user/auth/capabilities` 控制登录入口。`.env` 的 `APP_MODE=demo` 保留演示身份；`trial` 禁用共享演示登录，仍然使用模拟资金。`demo` 和 `trial` 互斥；微信身份配置与验收见[身份接入](identity-notifications.md)。

前端可以代理到上述宿主 Java，或已经准备好的独立 Docker 后端。先使用锁文件安装依赖：

```powershell
npm ci --prefix frontend/admin
npm ci --prefix frontend/client --include=optional
```

以下默认代理到本节 Java 的 `18084`，其允许 Origin 已配置。在两个终端分别执行：

```powershell
# 终端一：员工工作台 http://127.0.0.1:5173/admin/
$env:API_PROXY = 'http://127.0.0.1:18084'
npm run dev --prefix frontend/admin
```

```powershell
# 终端二：用户 H5 http://127.0.0.1:5174/app/
$env:API_PROXY = 'http://127.0.0.1:18084'
Remove-Item Env:VITE_API_BASE -ErrorAction SilentlyContinue
npm run dev:h5 --prefix frontend/client
```

终端二清除进程中的 `VITE_API_BASE`，让 H5 的 API、上传与演示图片保持同源；如本机 `.env.local` 另有该配置，也应相应调整。API 与上传通过 `API_PROXY` 转发。若选择独立 GHCR 后端，将代理改为 `http://127.0.0.1:28104`，仅在该项目的 `.local/ghcr-demo/demo.env` 中为 `ALLOWED_ORIGINS` 加入前端地址，再以相同环境文件和 `campuseats-ghcr-demo` 项目名执行 `up -d --no-build server`。不要修改日常 `.env` 或用日常库做写入式自动测试，见[独立验收](platform-deployment.md#独立验收)。

工作台与 H5 开发服务器直接读取仓库的 `deploy/dish.svg`，提供 `/demo-images/dish.svg`，无需为演示图片启动后端或 Docker。API 与上传文件仍通过 `API_PROXY` 访问所选后端；不要将日常库用于写入式自动测试。

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
mvn -B -ntp -pl campuseats-server -am '-Dtest=FinanceIntegrationTest,OperationsIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

页面模拟传输测试覆盖乱序响应等组件边界，角色体验通过真实服务与浏览器验证。真实体验使用独立浏览器会话和真实服务；API只准备基础夹具及核对结果，新增或修改的关键业务动作从页面操作，保留脱敏证据。未修改的其它全量流程、恢复和安全门禁交给对应SHA的CI；源码测试、真实页面、制品来源和日常部署核验分别记录。当前进度和已获得的证据见[当前版本验证与维护记录](verification/current.md)。
