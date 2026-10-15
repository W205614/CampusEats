# CampusEats · 多校区点餐与店铺管理

基于 Spring Boot、Vue 3 和 uni-app 的校园点餐平台。支持多个校区、独立店铺、跨校区合并结算和校区共享配送，保持 **Java 单体、单实例** 部署。

**默认 Compose 启用演示登录和模拟支付退款，不发生真实资金交易。** 不包含商户结算、自动路线规划或第三方配送。保留微信小程序登录接入代码，需要自行配置，尚未完成微信真机验收。

## 功能

| 使用者 | 能力 |
|---|---|
| 点餐用户 | 选择校区与店铺、搜索餐品和店铺、口味与套餐、独立店铺购物车、跨校区合单、逐店选择配送或自取、优惠券、订单、催单、再来一单、收藏、评价与售后举证 |
| 平台管理员 | 校区、店铺、楼栋、店长和配送员，平台优惠券、售后介入、评价隐藏，跨店统计与审计 |
| 店长 | 本店商品、分类、配额、营业规则、运营员、活动价和店铺券，订单履约、售后、评价回复和统计 |
| 运营员 | 本店订单接单、备餐、拒单、派单、自取核销、售后处理和评价回复 |
| 配送员 | 本校区抢单池、本人配送任务、取餐与送达；抢单前不展示完整收货信息 |

每名店长、运营员归属一家店，配送员归属一个校区。每家店属于一个校区，配送地址须在店铺所在校区。停用校区或店铺会停止新交易，历史订单仍可查询并完成后续处理。

## 页面预览

以下截图于2026-10-15从本机新版截取，展示演示环境。

![员工登录与用户入口切换](docs/images/platform-login.png)

![多校区与独立店铺工作台](docs/images/platform-dashboard.png)

<img src="docs/images/platform-h5-menu.png" alt="H5 校区选店与点餐" width="390">

## 快速开始

需要可用的 Docker Linux 容器环境与 Compose；启动脚本使用 PowerShell 7、Java 21 和 Maven。前端本机构建与下文浏览器检查使用 Node.js；Docker 前端构建和 CI 固定为已验证的 `24.19.0`。

```powershell
./scripts/start.ps1
docker compose ps
```

启动脚本默认执行后端验证，从源码构建Java与Web镜像，并启动四个服务；`-SkipVerify` 仅跳过这一步后端验证。已有数据库升级前，须暂停应用写入、备份数据库和上传文件、在独立容器验证恢复，再启动新版执行 Flyway 迁移。脚本检测到运行中数据库的迁移版本落后时会执行该流程，并为原运行应用镜像保留回滚标签；已有数据的升级与回退步骤见[升级说明](docs/platform-deployment.md)。

成功启动后的默认地址如下；修改`WEB_PORT`后须使用对应端口。

| 入口 | 地址 |
|---|---|
| 默认入口（用户点餐） | http://localhost:18083/ |
| 平台／店铺／配送工作台 | http://localhost:18083/admin/ |
| H5 点餐端 | http://localhost:18083/app/ |

直接访问网站即进入点餐页，点击「登录点餐」选择用户账户。商家、配送员和平台管理员点击「商家/配送工作台」进入员工登录；工作台也提供「用户点餐／去点餐」返回入口，无需手动修改网址。员工账户不再预填 `admin`，两端会话各自保存。

全新演示部署初始化平台账号 `admin`，初始密码由启动脚本写入本机 `.local/demo-credentials.txt`。演示店长为 `manager1` 至 `manager4`，运营员为 `operator1` 至 `operator4`，配送员为 `courier1`、`courier1b`、`courier2`、`courier2b`。新建的这些店长、运营员和配送员首次登录必须改密；已有账号保留原密码，修改 `.env` 或重新生成凭据文件不会重置已有账号。

全新数据库在 `demo` 配置下初始化两个校区、每校区两家店及对应角色。已有数据库按固定 ID、分类和用户名是否存在补充，不覆盖已有商品或营业配置，也不保证恢复出相同演示布局。新增店铺表单默认关闭接单，先配置营业时间和费用，再打开接单开关。

H5 登录页提供三个演示用户，购物车、地址和订单按用户编号隔离；同一编号由所有访问者共用，演示登录不用于真实用户身份认证。H5 演示登录按钮支持键盘 Tab／Enter／Space。微信登录只在已配置微信应用的小程序中调用，H5 按钮会说明使用条件。

默认仅绑定 `127.0.0.1:18083`，手机不能直接使用该地址。微信小程序构建产物不等同于微信真机验收。

## 交易与售后规则

- 每用户、每店独立保存购物车版本。一个结算单包含多个店铺子订单，允许跨校区、不同地址及配送／自取混合选择。
- 配送费按配送子订单收取，自取免配送费；包装费按各店商品数量收取。
- 整批创建、整批模拟支付；创建校验失败会整体回滚，网络响应丢失时可按原请求键查询或重试。支付后各店独立接单和履约。待支付结算单有效期15分钟，超时后由后台任务整体取消并释放预占和券。
- 优惠顺序固定为商品活动价、店铺券、平台券。每店最多一张店铺券、整批最多一张平台券；券支持满减或折扣，只抵商品金额。金额以整数分计算，使用最大余数法分摊并保存快照。
- 商品配额仍按菜品与营业日期管理，套餐合并扣减组成菜品；接单前取消释放预占，已备餐或完成后的退款不恢复已消耗份数。
- 备餐完成后发布抢单任务或人工指派本校区配送员。抢单和派单竞争只能一个成功；取餐后不能撤回进入抢单池。自取使用一次性核销码，错误次数受限。
- 用户可直接取消待支付结算单或待接单子订单；备餐中提交取消申请，申请处理中暂停后续履约。
- 有可验证完成时间的订单，完成后7天内可申请整单或按商品数量退款；同一子订单同时只允许一个处理中的售后申请。部分退款按原实付分摊，不退费用；整单退款包括尚未退还的费用。支付后不返券，不重算其他店铺优惠。
- 售后最多3张图片，每张不超过5 MiB，仅支持边长不超过4096像素的PNG/JPEG；读取须鉴权，仅允许订单用户、对应店铺员工和平台管理员。商家拒绝或48小时未处理后，用户可申请一次平台介入。
- 退款通过独立请求、稳定编号及带租约的任务处理。只有模拟网关成功并落库后才计为退款成功；失败任务可恢复重试。

## 工程结构

```text
sky-common/                  公共异常与摘要工具
sky-pojo/                    原有请求与展示契约
sky-server/src/main/java/com/sky/
  platform/                  校区店铺、范围授权、购物车、合单、营销、履约、售后、统计
  security/                  认证、会话撤销与限流
  business/                  复用的交易校验、配额、缓存、历史任务及旧流程回归
  web/                       统一响应与新版接口
  websocket/                 一次性票据及按店铺过滤通知
frontend/admin/              平台、商家与配送工作台
frontend/client/             uni-app H5 与微信小程序源码
frontend/contracts/          OpenAPI、生成类型及前端展示模型
scripts/                     构建、验收、备份恢复
compose.yaml                 MySQL、Redis、Java、Nginx 四服务
```

新版入口继续使用 `/api/v1`。店铺管理显式指定店铺范围；默认配置下，已退役的无店铺范围单店业务入口返回 `410 API_UPGRADED`，认证及新版资源接口按各自规则保留。旧实现仍承担内部复用、历史任务处理和边界回归。

## 验证与运行

```powershell
mvn -B -ntp clean verify
./scripts/build-frontends.ps1
./scripts/smoke.ps1
```

浏览器业务验收会创建演示订单和优惠券，应先按[独立验收说明](docs/platform-deployment.md#独立验收)准备隔离项目，不要在日常数据上运行。Compose 支持 `SERVER_IMAGE`、`WEB_IMAGE` 命名独立镜像；还须使用独立项目名、端口和数据卷。

在使用隔离环境凭据的验收副本中运行以下命令（示例端口为28083）。首次运行先安装Playwright浏览器，Linux环境可增加`--with-deps`安装系统依赖。浏览器与WebSocket脚本读取`BASE_URL`；冒烟脚本则需显式传入`-BaseUrl`。

```powershell
$env:BASE_URL = 'http://localhost:28083'
npx --prefix frontend/admin playwright install chromium
npm run test --prefix frontend/admin
node scripts/check-websocket.mjs
./scripts/smoke.ps1 -BaseUrl $env:BASE_URL
```

日常环境的备份和独立恢复验证会短暂停止页面与Java服务，MySQL、Redis保持运行。以下示例在项目根目录执行；备份脚本本身不会暂停写入，也不会保存镜像内容，回滚用的镜像须另行保留。

```powershell
$ErrorActionPreference = 'Stop'
$backupDirectory = Join-Path $PWD ('.local/backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
docker compose stop web server
if ($LASTEXITCODE -ne 0) { throw '暂停应用写入失败' }
try {
    ./scripts/backup.ps1 -Destination $backupDirectory
    ./scripts/restore-check.ps1 -BackupDirectory $backupDirectory
} finally {
    docker compose up -d --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw '恢复服务失败，请检查容器日志' }
}
./scripts/smoke.ps1
```

菜单缓存保存目录数据，活动价格和配额在请求时读取，最终交易仍校验数据库。工作台非配送员订单页使用WebSocket作为尽力提示；工作台订单页与打开中的用户订单页每15秒刷新，用户离开订单页后停止轮询。并非所有页面都使用推送，通知任务成功不代表客户端已经收到通知。

统计将支付和成功退款按各自发生日期记录，净收款为所选期间支付额减成功退款额；完成单成交额单独展示。合单支付笔数按结算单去重。历史缺失事件时间保留为未知，不伪造发生日期。

[架构与接口](docs/platform-architecture.md) · [升级与回滚](docs/platform-deployment.md) · [多校区验收记录](docs/platform-verification-20261015.md) · [入口与体验完善](docs/frontend-experience-20261015.md)

2026-10-15的[完整浏览器旅程复验](docs/browser-journey-20261015.md)通过84项后端、30项浏览器、6项构建兼容和8项审计规则回归。新增用例通过页面完成选餐加购、地址与领券、支付、店长接备发、配送员抢取送、图片举证、部分退款和剩余整单退款／平台介入，并修复员工首次改密时多余店铺请求造成的403提示。更早的29项浏览器及屏宽检查保留在[前次复验记录](docs/final-verification-20261015.md)，不混作同一次测试。

随后完成[跨校区混合履约复验](docs/mixed-fulfillment-20261015.md)：完整浏览器回归增至31项，后端84项、构建兼容6项和审计规则8项均通过。新增用例在独立环境通过页面完成两校区两店合付57.53元、配送与自取、领券举证及退款；逐行核对优惠分摊和1分尾差，配送单累计退36.84元、自取单退9.94元，北店后续退款未改变南店完整快照。16项接口拒绝检查覆盖跨店、跨校区配送、跨用户及私有附件。基础数据通过API预置，页面操作与接口断言范围详见记录；本轮未发现需要修改产品代码的缺陷。

CI从源码重复后端回归、两端类型检查与构建、接口契约比对、依赖审计、浏览器和WebSocket验证，再暂停写入并完成独立备份恢复及恢复后的冒烟。后续结果以[对应提交的GitHub Actions](https://github.com/W205614/CampusEats/actions/workflows/verify.yml)为准；上述证据不等同于全站无缺陷、生产容量或微信真机验证。

在2026-10-15提交`2d39a4a`的CI中，两端npm审计均为0项已知漏洞；这只代表该lockfile在审计时的公告结果。用户端的构建适配移除了braces和受影响的phin 2依赖路径，受版本与文件哈希约束；更新uni-app时须重新检查适配与回归。CI不再接受漏洞例外，审计结果不等同于全面安全认证。[依赖安全复核与升级证据](docs/dependency-security-20261015.md)。

历史 v1 文档保留作追溯；已退役的单店压测、故障演练及其辅助脚本已移除，旧实现可从 Git 历史查阅。当前可执行检查以上述命令和 CI 为准。前端只从 `frontend/admin/`、`frontend/client/` 源码构建，小程序输出到 `frontend/client/dist/build/mp-weixin/`，不依赖根目录旧小程序或 `deploy/admin/` 产物。[文件清理范围](docs/structure-cleanup-20261015.md)。
