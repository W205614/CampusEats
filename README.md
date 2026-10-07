# CampusEats

单校区、单店的校园点餐工程演示。Java 单体 + MySQL + Redis；Vue 3 管理端、uni-app 微信小程序与 H5。**支付和退款均为模拟，不发生真实资金交易。**

## 启动

需要 Docker Desktop/Linux Engine、Java 21、Maven、Node 24，PowerShell 7。首次运行：

```powershell
./scripts/start.ps1
```

脚本生成本机配置和独立的演示管理员密码，执行真实数据库测试，再从源码构建并启动四个容器。

- 管理端：http://localhost:18083/admin/
- H5：http://localhost:18083/app/
- 管理员：`admin`，初始密码见本机 `.local/demo-credentials.txt`。
- 用户端三个演示账户互相独立。演示登录仅在 `demo` profile 启用。
- 默认关店：在管理端“营业规则”确认费用与时段后打开营业开关。
- 使用独立的 `campuseats-v1` 数据卷，原 `campuseats` 数据卷保留。
- 修改 `.env` 中初始密码不会重置已有管理员。旧数据中的 MD5 密码在首次登录迁移并要求改密。

## 业务规则

下单服务端计价并保存商品、费用和收货地址快照。请求键防重复下单；购物车版本防覆盖。每日供餐按菜品计数，套餐扣组成菜品份数。

下单预占，接单转消耗；接单前取消、拒单、支付超时返还，接单后取消不自动返还。已支付订单取消后进入模拟退款任务，任务成功后才标记退款。

配送员只查看和操作自己的订单。配送超时标记待处理，不自动冒充送达。实时通知断线时页面继续查询数据库状态。

## 开发与验证

```powershell
mvn -B -ntp verify
./scripts/build-frontends.ps1
npm run test --prefix frontend/admin
node scripts/check-websocket.mjs
node scripts/generate-contracts.mjs --fetch
```

测试使用 Testcontainers 创建独立 MySQL/Redis，包含真实并发、交易回滚、权限和迁移场景。浏览器测试需要已启动的本机演示环境。生成的 API 类型及 OpenAPI 快照放在 `frontend/contracts`。

2026-10-07 本机验收：

| 检查 | 实际结果 |
|---|---|
| 后端真实数据库测试 | 35 项通过，失败/错误/跳过均为 0 |
| Chromium 实际页面 | 9 条流程通过：送达、退款、提交恢复、商品/套餐/图片、楼栋/配额/营业、地址/购物车/快照、两种员工角色、统计导出与审计 |
| 源码构建 | 管理端、H5、微信小程序通过；两端类型检查通过 |
| 运行恢复 | 独立备份恢复、Redis/MySQL 故障、过期任务租约恢复及 WebSocket 安全检查通过 |
| 容量测试 | 1 万订单/5 万明细；50 并发持续 10 分钟达到首轮门槛，具体负载、机器和指标见验收记录 |

支付/退款仅为模拟；微信开发者工具预览、真机和真实微信登录尚未验收。用户端仍有 7 项高危构建依赖告警，按限定范围接受至 2026-11-07，见[依赖安全记录](docs/frontend-security-review.md)。远端 CI 结果以 GitHub 对应提交的 Actions 为准。

管理端开发：`npm run dev --prefix frontend/admin`；H5 开发：`npm run dev:h5 --prefix frontend/client`。默认代理本机8080，可用 `API_PROXY` 指定后端。

小程序导入 `frontend/client/dist/build/mp-weixin`。真实微信登录/真机需要自己的 AppID、可访问的 HTTPS 后端及合法请求域名，详见部署文档。编译成功不代表真机已通过。

## 运行与恢复

```powershell
./scripts/backup.ps1
./scripts/restore-check.ps1 -BackupDirectory E:/project/CampusEats/.local/backups/具体备份目录
./scripts/recovery-check.ps1
./scripts/load.ps1
```

备份包括数据库、上传文件与本机配置；恢复检查使用独立临时容器。故障演练会短暂停止本机演示依赖。容量测试使用独立 `campuseats-load-时间戳` 环境和固定数据集，不修改演示数据。运行后保留结果并停止测试容器。

整理旧容器时，先完成备份和独立恢复验证，再执行：

```powershell
./scripts/consolidate-containers.ps1 -VerifiedBackupDirectory E:/project/CampusEats/.local/backups/具体备份目录
```

脚本按本仓库的 Compose 项目标签和路径校验范围，只移除已停止的旧版、压测容器，并要求最终 4 个容器健康。数据库与上传卷、回滚镜像和本机证据保留。当前演示仅保留 `campuseats-v1` 的 MySQL、Redis、Java、Nginx 四个容器。

已有数据库必须先备份、检查冲突、在恢复克隆中显式建立 Flyway 基线，再验证迁移。应用不自动为任意非空数据库建立基线，也不自动删除冲突数据。不要对有业务数据的项目执行 `docker compose down -v`。

- [部署、迁移与恢复](docs/v1-deployment.md)
- [架构与接口约定](docs/v1-architecture.md)
- [实施与验收记录](docs/v1-verification.md)
- [原始审查](docs/enterprise-review-20261007.md)

旧后端与课程回归测试保存在 `legacy`，仅供追溯，不编译、不提供旧接口。旧课程前端及补丁脚本不参与新构建。

