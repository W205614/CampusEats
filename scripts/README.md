# 脚本导航

以下脚本从项目根目录执行，PowerShell 脚本使用 `pwsh`。首次体验只需 `start.ps1 -SkipVerify`；其它脚本按需使用。环境配置、凭据、备份与输出保存在受忽略的 `.env`／`.local/`。

| 用途 | 脚本 | 说明 |
|---|---|---|
| 首次启动、源码升级 | `start.ps1` | 生成本机配置、构建镜像并等待健康；已有数据会先暂停写入、备份和独立验证，详见[升级说明](../docs/platform-deployment.md) |
| 前端源码构建 | `build-frontends.ps1` | 安装依赖，检查兼容层和类型，构建管理端、H5、小程序 |
| 基础可用性 | `smoke.ps1` | HTTP 就绪与入口检查 |
| 运维只读检查 | `ops-check.ps1` | 任务、积压、图片、空间及验证备份；发现异常返回非零 |
| 一次性运维入口 | `ops-run.ps1 -Action Check\|Backup` | 可接定时调用，项目互斥；按配置复制验证备份、发送变化或恢复通知；不会自动注册定时任务 |
| 运维接入模板 | `ops-config.example.json` | 复制到 `.local/ops-config.json` 配置受控副本目录与告警地址；配置与状态不提交 Git |
| 安全备份 | `safe-backup.ps1` | 暂停写入，调用备份及独立恢复检查，恢复原运行状态；日常备份优先用此入口 |
| 备份底层步骤 | `backup.ps1` | 导出数据库、文件与清单；单独运行不等同于完整安全备份流程 |
| 独立恢复核验 | `restore-check.ps1` | 读取指定备份，在临时容器恢复并核验，不覆盖日常数据库 |
| 历史库迁移预检 | `migration-preflight.ps1` | 检查重复标识与归属；只作部分预检，不能证明 schema 可直接迁移 |
| 数据清单比对 | `platform-data-manifest.ps1` | 输出／比对指定业务数据清单，仅写入受保护的 `.local/` |
| 历史容器整理 | `consolidate-containers.ps1` | 专项维护工具；先验证备份，仅处理匹配项目目录且已停止的旧容器，保留卷和镜像；首次部署无需执行 |
| 接口类型 | `generate-contracts.mjs` | 默认从已有快照生成；`--fetch` 会登录指定后端并刷新快照，不是纯本地生成 |
| 通知验收 | `check-websocket.mjs` | 连接实际后端校验票据与会话边界，需要正确环境 |
| 用户端依赖审计 | `check-client-audit.mjs` | 检查 `.local/audit-client.json`，发现漏洞或无效报告即失败 |
| 后端与镜像安全 | `scan-images.ps1` | 固定版本 Trivy，扫描两应用镜像的系统包及运行时依赖，保留完整报告和 CycloneDX 清单 |
| 性能身份夹具 | `prepare-performance.mjs` | 仅允许明确隔离的项目、端口与配置，准备独立数据库用户和有效会话；不测登录容量 |
| 有限性能测量 | `measure-performance.mjs` | 使用隔离身份夹具，测量1／5／10并发下的核心链路；操作说明见运维指南 |

`docker-read.ps1` 是运维读取的超时辅助文件；`test-ops-check.ps1`、`test-release-safety.ps1`、`check-client-audit.test.mjs` 及 `tests/ops-docker-fixture.ps1` 是脚本回归与夹具，不是日常启动入口。

涉及写入的业务、迁移和浏览器验收应使用独立项目、端口与数据卷。[日常维护](../docs/operations.md)列出备份与检查命令；完整自动验证入口是 [GitHub Actions](../.github/workflows/verify.yml)。
