# CampusEats · 校园多校区点餐平台

[![CI](https://github.com/W205614/CampusEats/actions/workflows/verify.yml/badge.svg?branch=master)](https://github.com/W205614/CampusEats/actions/workflows/verify.yml)
[![Release](https://img.shields.io/github/v/release/W205614/CampusEats)](https://github.com/W205614/CampusEats/releases/latest)
[![MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

CampusEats 面向校园点餐与食堂店铺管理：学生可以跨店选餐、合并报价和付款，各店分别备餐，再按订单选择校园配送或到店自取；员工在各自权限内处理履约、售后和经营核对。

它解决的是多店重复下单、优惠与费用难以核对、履约和售后分散的问题。当前定位为个人开发、学习和本机演示，默认监听回环地址，付款与退款使用模拟渠道。微信、腾讯云和多实例适配已有代码，真实商户、云端生产和小程序真机仍需单独验收。

推荐稳定版为 **v1.1.2**。Web 镜像更新至 Nginx 1.30.8，修复 CVE-2026-132779。Java 与 Web 直接推广已通过 CI 的制品，应用和小程序构建元数据仍为 1.1.1。公开镜像已按不可变摘要匿名拉取并完成隔离业务验收。[v1.1.2 发布](https://github.com/W205614/CampusEats/releases/tag/v1.1.2) · [镜像来源 6b33aa4](https://github.com/W205614/CampusEats/commit/6b33aa4f8ccd78e4d93067b6d296f73cffa7a0a4)。

## 可以完成什么

| 使用者 | 操作与结果 |
| --- | --- |
| 点餐用户 | 按营业状态、库存和价格选餐。按店选配送或自取，核对各店费用或报价差额后模拟付款，查看订单、消息与按商品退款的结果 |
| 店长 | 维护本店的商品、配额、营业配置、优惠和取餐点，处理履约与售后，查看经营报表 |
| 店铺运营员 | 处理本店接单、备餐、配送安排、自取码核销和职责范围内的售后 |
| 配送员 | 在所属校区领取或接受任务，完成取餐与送达；订单字段和后续访问随权限及履约状态收敛 |
| 平台管理员 | 管理校区、店铺、员工和独立体验账号，处理履约异常、售后介入、审计及模拟账务核对 |

商家独立收款，平台不抽佣；跨店联合优惠由参与商家承担。默认演示中的金额流转不发生真实扣款，规则见[平台架构](docs/platform-architecture.md)。

```mermaid
flowchart LR
    A[选校区与店铺] --> B[加购与合并报价]
    B --> C[模拟付款]
    C --> D[各店分别备餐]
    D --> E[配送送达或自取核销]
    E --> F[完成订单与评价]
    C -. 按状态和权限 .-> G[售后与退款]
```

## 真实界面

以下画面来自 **v1.1.1 公开镜像的隔离业务验收**，使用合成校区、店铺、优惠和订单。左图展示跨校区合单的分店报价，配送店报价上涨 ¥1.00，自取店金额保持不变。右图展示两店履约完成后的订单、取餐点和分次模拟退款记录。

<p>
  <img src='docs/images/v1.1.1-h5-requote.png' alt='跨校区合单更新报价，配送店多付 1 元，自取店金额保持不变' width='300'>
  <img src='docs/images/v1.1.1-h5-orders.png' alt='已完成的配送和自取订单，显示取餐点及分次模拟退款明细' width='300'>
</p>

同一隔离旅程核对了优惠分摊、配送与自取、按商品分次退款及跨角色访问边界。[验证记录](docs/verification/current.md)。

## 选择启动方式

命令在项目根目录的 **PowerShell 7** 中执行；Windows 与 Linux 都使用 `pwsh`，无需切换 Bash 环境变量语法。先取得稳定版的 Compose、配置示例和脚本：

```powershell
git clone --depth 1 --branch v1.1.2 https://github.com/W205614/CampusEats.git
cd CampusEats
```

`v1.1.2` 固定应用源码、Compose 和依赖镜像配方。克隆后继续按本页操作。源码归档携带打标签时的 README，其中仍有 v1.1.1 启动示例。也可以从 [v1.1.2 Release](https://github.com/W205614/CampusEats/releases/tag/v1.1.2) 顶部进入本版上手说明。已有数据环境请按[升级与恢复](docs/platform-deployment.md)保留原卷并执行升级。

| 路线 | 适合谁 | 宿主依赖与入口 |
| --- | --- | --- |
| **预构建应用镜像** | 直接体验已发布应用 | Git、PowerShell、Docker 与 Compose；Java/Web 拉取，MySQL/Redis 按项目配方本地构建；端口 `28104` |
| **源码 Docker 构建** | 修改应用并构建自己的镜像 | 同上，Java/Maven/Node 在构建容器内；默认端口 `18083` |
| **宿主源码开发** | Java 断点、前端热更新 | Java 21、Maven、Node.js `24.19.0`；Docker 提供独立 MySQL/Redis；Java `18084`、员工端 `5173/admin/`、H5 `5174/app/` |

### 拉取预构建应用镜像

先按[镜像首次部署步骤](docs/platform-deployment.md#ghcr-start)生成随机凭据及 `.local/ghcr-demo/demo.env`，其中指定本版本应用的不可变摘要。完成配置后：

```powershell
$composeArgs = @('compose', '--env-file', '.local/ghcr-demo/demo.env', '-p', 'campuseats-ghcr-demo')
docker @composeArgs build --pull mysql redis
docker @composeArgs pull server web
docker @composeArgs up -d --no-build --wait --wait-timeout 300
```

访问[用户端](http://127.0.0.1:28104/)和[员工工作台](http://127.0.0.1:28104/admin/)。拉取只是准备步骤，四服务启动后，Java 通过 Flyway 初始化数据库和演示资料。

| 项目发布的应用镜像 | 已验证平台与职责 |
| --- | --- |
| `ghcr.io/w205614/campuseats-server:v1.1.2` | `linux/amd64`，Java 服务 |
| `ghcr.io/w205614/campuseats-web:v1.1.2` | `linux/amd64`，Nginx、员工工作台与用户 H5 |

两个应用镜像均可匿名拉取。MySQL 8.4.13 和 Redis 7.4.12 由 Compose 按项目配方在本地构建，首次构建需访问 Docker Hub、Alpine 和 Oracle 下载源。[版本及镜像摘要](https://github.com/W205614/CampusEats/releases/tag/v1.1.2) · [验证附件](https://github.com/W205614/CampusEats/releases/download/v1.1.2/campuseats-v1.1.2-verification.json)。

### 从源码构建 Docker 镜像

在全新源码目录中执行：

```powershell
pwsh -NoProfile -File ./scripts/start.ps1 -SkipVerify
$composeArgs = @('compose')
```

脚本生成随机凭据、`.env` 和 `.local/demo-credentials.txt`，构建并启动四服务。`-SkipVerify` 只跳过宿主 Maven 验证；这是**本地源码构建**。已有 CI 制品环境使用[制品升级流程](docs/platform-deployment.md#已采用-ci-制品的日常环境)。

访问[用户端](http://127.0.0.1:18083/)和[员工工作台](http://127.0.0.1:18083/admin/)。首次构建需访问基础镜像、Maven、npm 与 Oracle 补丁来源，宿主无需 Java 或 Node。

### 在宿主运行源码

按[宿主 Java 与独立数据库步骤](docs/development.md#host-source)准备依赖、变量、本地构建并启动 Java，再按[前端热更新](docs/development.md#frontend-dev)启动两端。Maven 只用于源码构建和开发进程，不提供 Maven 应用包下载或运行入口。

微信小程序另行构建并导入微信开发者工具，见[小程序开发](docs/development.md#微信小程序)。服务端镜像不能替代客户端平台验收，编译通过不代表真机登录、支付和订阅消息已验证。

### 状态、日志与暂停

以下用于两条 Docker 应用路线。保留其 `$composeArgs`，新开终端先恢复对应配置与项目名；宿主开发进程按开发指南在原终端停止：

```powershell
docker @composeArgs ps
docker @composeArgs logs --tail 100 server web
docker @composeArgs stop --timeout 60
```

首次部署应看到 `mysql`、`redis`、`server`、`web` 全部健康。停止保留数据；删除持久卷可能丢失数据库、Redis 或上传内容。日常启停见[维护指南](docs/operations.md#日常启停与排障)。

## 登录并完成第一笔订单

以下为全新数据库的演示模式，访问地址取决于所选路线。

1. 使用平台账号 `admin` 登录员工工作台。镜像路线的初始密码在受保护配置的 `DEMO_ADMIN_PASSWORD` 中；源码启动脚本另生成 `.local/demo-credentials.txt`。已有账号不会因改环境变量自动重置密码。
2. 在「校区与店铺」找到「默认店铺」，进入「营业配置」，确认营业时段，开启「接收新订单」并保存。新店默认关闭接单。
3. 用户端点击「登录点餐」，选择演示用户，在「默认校区 → 默认店铺」加购，进入购物车，点击「选择此店」和「合并结算」。
4. 选择「到店自取」，计算报价、提交订单，再点击「整批模拟支付」。体验配送时先添加该店所在校区的地址。
5. 工作台退出 `admin`，以店长 `manager1` 和同一初始化口令登录并按提示改密，完成「接单备餐」「备餐完成」，用用户订单页的自取码核销，查看完成结果。新演示员工的初始口令均来自 `DEMO_ADMIN_PASSWORD`，已有员工继续使用各自当前密码。

平台也可创建独立体验账号，用户选择「独立账号登录」。更多角色与试用模式见[演示账号说明](docs/operations.md#演示账号)。

## 架构与源码入口

本机演示由四服务组成：

```mermaid
flowchart LR
    U[浏览器：用户 H5 / 员工工作台] --> W[Web：Nginx 与前端静态文件]
    W --> J[Java：认证、报价、订单、履约、售后与账务]
    J --> D[(MySQL：业务记录、流水与持久消息)]
    J --> R[(Redis：缓存、限流与按模式启用的协调)]
    J --> F[上传卷：默认本地文件存储]
```

后端为 Java 21 / Spring Boot 4.1，数据层为 MySQL 8.4 / Redis 7.4。员工端使用 Vue 3、Pinia、Element Plus 和 Vite；用户端由 uni-app 构建 H5 与微信小程序。WebSocket 提供实时提示，持久消息及权限判断由后端和数据库负责，详见[平台架构](docs/platform-architecture.md)。

| 目录 | 职责与阅读入口 |
| --- | --- |
| `campuseats-server/` | Spring 应用，从 [HTTP 入口](campuseats-server/src/main/java/com/campuseats/web/)沿服务追踪；[Flyway 迁移](campuseats-server/src/main/resources/db/migration/)维护数据库演进 |
| `campuseats-api/`、`campuseats-common/` | Java 请求/响应类型、共享异常与摘要工具 |
| `frontend/admin/`、`frontend/client/` | 员工工作台和用户端；[开发指南](docs/development.md#按业务追踪请求)提供业务调用链 |
| `frontend/contracts/` | OpenAPI 快照、生成类型和前端共享模型；生成文件按脚本维护 |
| `deploy/`、`scripts/` | [容器配置](deploy/README.md)、[构建/发布/备份工具](scripts/README.md) |
| `.github/workflows/` | 构建、浏览器回归、镜像扫描、CodeQL 与手动镜像推广 |

## 环境、配置与外部服务

本轮使用 Windows、PowerShell **7.6.9** 与 Docker Desktop WSL2（Compose **5.7.0**）验收公开镜像和日常升级。Ubuntu 24.04 CI 构建并验证 `linux/amd64` 镜像。宿主开发使用 Java 21、Maven 3.9 和 Node.js 24.19.0。

必要变量见 [.env.example](.env.example)，以下五项均需独立随机生成，不能保留占位符：

| 变量 | 用途 | 默认处理 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | 数据库管理与恢复 | 源码启动脚本生成随机值；镜像路线显式生成 |
| `MYSQL_PASSWORD` | 应用访问 MySQL | 同上，不与 root 口令共用 |
| `REDIS_PASSWORD` | Redis 认证 | 同上，独立值 |
| `JWT_ADMIN_SECRET` | 签名与认证相关用途 | 同上，生成 32 随机字节，保密保存 |
| `DEMO_ADMIN_PASSWORD` | 首次初始化演示员工 | 同上；已有账号保持原密码 |

演示默认 `APP_MODE=demo`、`MOCK_PAYMENT_ENABLED=true`、`BIND_ADDRESS=127.0.0.1`。宿主 Java 不自动读取 Compose 的 `.env`，按[开发步骤](docs/development.md#host-source)设置数据库、Redis 与上传目录等变量。

微信付款退款、微信身份、腾讯云短信/COS 和订阅消息均为**可选外部接入**。默认缺少真实账号、授权、密钥、模板和资源，这些真实渠道不可用，模拟点餐可独立运行。接入可能产生服务费用，按[生产接入](docs/production-readiness.md)、[身份与通知](docs/identity-notifications.md)、[文件存储](docs/storage-integration.md)准备并真实验收。CLB/CVM、托管数据库和生产多实例方案是模板及接入指导，不能据此认定已生产部署。

## 验证、排错与数据维护

v1.1.2 的应用 CI 通过后端 **361** 项、浏览器 **96** 项、独立恢复及四镜像扫描。正式镜像在独立空卷环境通过 **8** 项针对性浏览器检查，覆盖取餐码刷新和核销、跨店履约、模拟退款及订单异常提示。微信真实渠道、腾讯云和小程序真机按各自的接入流程验收。[安全审阅说明](docs/security-review.md) · [版本验证记录](docs/verification/current.md)。

常用源码检查从根目录执行，按改动选择范围；Java 集成测试需要 Docker，写入式浏览器测试使用独立数据库：

```powershell
mvn -B -ntp clean verify
npm run test:toolchain --prefix frontend/admin
npm run build --prefix frontend/admin
npm run test:toolchain --prefix frontend/client
npm run typecheck --prefix frontend/client
npm run build:h5 --prefix frontend/client
npm run build:mp-weixin --prefix frontend/client
```

CI、浏览器凭据与隔离测试步骤见[贡献指南](CONTRIBUTING.md)。本轮没有生产容量认证，历史合成负载测量只适用于当时版本、环境和方法。

| 现象 | 先检查 |
| --- | --- |
| 启动失败、端口冲突 | 所选端口是否占用、Docker 是否运行 Linux 容器、Compose 是否读取正确环境文件 |
| 数据库或服务不健康 | 用上面的状态/日志命令定位；检查口令与旧卷是否匹配，不删除卷来绕过错误 |
| 镜像拉取失败 | GHCR 网络、完整仓库名、版本与 `amd64` 平台；MySQL/Redis 依赖是否已构建 |
| 能登录但不能下单 | 店铺是否营业接单、配额/地址/校区是否匹配，是否完成报价与模拟付款 |
| 开发 API 失败 | `API_PROXY`、允许的 Origin、宿主数据库映射是否一致，前端是否误连日常后端 |

MySQL、Redis 和上传分别持久化到 Compose 的 `mysql-data`、`redis-data`、`uploads` 命名卷。v1.1.2 继续使用 Flyway **V25**，沿用已有的取餐点和退款消息关联字段。升级前备份数据库、上传与配置并独立验证恢复，随后沿用原卷。[备份、升级与恢复](docs/platform-deployment.md) · [日常排障](docs/operations.md)。

## 版本、参与与许可

源码、镜像与第三方对应源码材料集中在本版本的 Release。历史版本和变更见 [Tags](https://github.com/W205614/CampusEats/tags) · [Releases](https://github.com/W205614/CampusEats/releases) · [变更记录](CHANGELOG.md)。

使用问题与脱敏缺陷通过 [Issues](https://github.com/W205614/CampusEats/issues)反馈，修改按[贡献指南](CONTRIBUTING.md)提交 Pull Request；安全问题按[安全政策](SECURITY.md)报告。更多主题见[文档目录](docs/README.md)。

项目自有源码、文档与素材采用 [MIT License](LICENSE)。第三方代码、基础镜像和制品分别适用其许可证；通知、SBOM 和对应源码获取见[许可说明](docs/licensing.md)。
