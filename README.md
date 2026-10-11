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

## 代码结构

- `sky-common`：公共异常与哈希工具；`sky-pojo`：请求、响应契约；`sky-server`：接口、安全、业务服务与数据库访问。
- `frontend/admin`：Vue 管理端；`frontend/client`：uni-app 用户端；`frontend/contracts`：API 类型、展示映射与共享错误类。
- `deploy`、`compose.yaml`：源码镜像构建与 Nginx 入口；`scripts`：构建、验收、备份、恢复及可选压测；`docs`：架构和历史证据。

业务服务按购物车、计价、订单、配额、目录和后台任务组织。保留直接可读的业务判断，不引入通用 CRUD 框架或合并两端不同的请求实现。

## 开发与验证

```powershell
mvn -B -ntp verify
./scripts/build-frontends.ps1
npm run test --prefix frontend/admin
node scripts/check-websocket.mjs
node scripts/generate-contracts.mjs --fetch
```

测试使用 Testcontainers 创建独立 MySQL/Redis，包含真实并发、交易回滚、权限和迁移场景。浏览器测试需要已启动的本机演示环境。生成的 API 类型及 OpenAPI 快照放在 `frontend/contracts`。

测试按风险分工：真实数据库测试验证业务一致性与迁移，隔离测试验证缓存竞态和限流边界，浏览器测试验证实际页面流程，烟测验证部署入口与鉴权。不同层次的覆盖不按“重复测试”删除。备份恢复、故障演练和容量测试按需运行，不并入每次页面回归。

浏览器公共辅助代码在 `frontend/admin/tests/helpers.ts`。每个测试保存并恢复营业配置，写入用户数据的测试要求购物车原本为空，并恢复默认地址、清理自己的地址和购物车。订单通过取消/退款收尾，测试员工和楼栋停用，关联历史及审计保留。登录仅对明确的 `RATE_LIMITED` 响应等待窗口恢复，其他错误直接失败；单个测试最多 90 秒，继续保持单 worker。测试源码也纳入管理端类型检查。

2026-10-11 冗余整理后的本机验收：

| 检查 | 实际结果 |
|---|---|
| 代码整理 | 移除 191 个旧实现及无引用资源文件；共享错误类、登录/配置/请求辅助逻辑；消除管理端重复类型检查 |
| 后端回归 | 修改前、修改后均为 47 项通过，失败/错误/跳过均为 0；未删减当前业务场景 |
| 浏览器 | 最终源码构建后 10/10 通过；营业配置、原默认地址和空购物车恢复到测试前状态 |
| 源码构建与接口 | 管理端、H5、小程序构建通过；两端类型检查、运行中 OpenAPI 比较、WebSocket 安全检查通过 |
| 容器与数据 | 四个最终容器健康；独立备份恢复通过；重启后业务表校验和、上传文件哈希一致；订单归属及配额检查通过 |
| 清理 | 删除本项目 4 个旧镜像引用及对应旧镜像；42 个其他项目容器、205 个原有数据卷核对保留；清理后烟测通过 |

入口跳转已修复：访问 `http://localhost:18083/` 使用相对跳转进入管理端，保留端口；CI 增加了相应烟测。首轮浏览器的 8/10 失败来自测试集中登录触发既有限流，已修正测试等待方式，未放宽服务端限制。详细证据见[本次整理验收](docs/cleanup-verification-20261011.md)。本轮未重跑容量压测或全面故障演练；下表是历史结果。

2026-10-07 历史本机验收：

| 检查 | 实际结果 |
|---|---|
| 后端回归测试 | 47 项通过：39 项真实数据库、8 项隔离回归；失败/错误/跳过均为 0 |
| Chromium 实际页面 | 10 条流程通过：送达、退款、提交恢复、商品/套餐/图片、分类与套餐缓存联动、楼栋/配额/营业、地址/购物车/快照、两种员工角色、统计导出与审计 |
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

2026-10-11 本次清理按明确选择删除旧镜像及回滚标签，不另存镜像归档；Docker 中仅保留本项目最终镜像及运行所需的 MySQL/Redis 镜像。本机数据备份保存在受保护且被 Git 忽略的 `.local/backups/20261011-before-cleanup` 和 `.local/backups/20261011-final`。后续运行启动脚本仍会在重建前保留当时镜像，清理应继续按项目范围核对，不能全局 prune。

已有数据库必须先备份、检查冲突、在恢复克隆中显式建立 Flyway 基线，再验证迁移。应用不自动为任意非空数据库建立基线，也不自动删除冲突数据。不要对有业务数据的项目执行 `docker compose down -v`。

- [部署、迁移与恢复](docs/v1-deployment.md)
- [架构与接口约定](docs/v1-architecture.md)
- [实施与验收记录](docs/v1-verification.md)
- [一致性与降级恢复修复验收](docs/consistency-verification-20261007.md)
- [原始审查](docs/enterprise-review-20261007.md)

旧实现、课程补丁、旧初始化 SQL、无引用 PNG 和报表模板已移出当前目录，可通过[整理前提交](https://github.com/W205614/CampusEats/tree/f85f3e7faf36b9ef3bd8d6e4a58c33155b3a18e8)追溯。当前 Flyway 迁移仍完整保留；历史文档中的路径和指标以对应历史提交为准。

