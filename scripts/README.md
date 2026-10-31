# 脚本导航

以下脚本从项目根目录执行，PowerShell 脚本使用 `pwsh`。首次体验只需 `start.ps1 -SkipVerify`；其它脚本按需使用。环境配置、凭据、备份与输出保存在受忽略的 `.env`／`.local/`。

| 用途 | 脚本 | 说明 |
|---|---|---|
| 首次启动、源码升级 | `start.ps1` | 生成本机配置、构建镜像并等待健康；已有数据会先暂停写入、备份和独立验证，详见[升级说明](../docs/platform-deployment.md) |
| CI制品下载 | `download-release.ps1` | 4 MiB有界分段，核对成功run、来源SHA及完整归档／清单摘要，不保存GitHub令牌 |
| CI制品发布 | `release.ps1` | 不构建镜像；停写迁移、原字段及上传核对、独立恢复后开放；应用发布保留已就绪依赖及业务卷，V3要求MySQL与制品身份一致，依赖补丁升级步骤见[部署说明](../docs/platform-deployment.md#已采用-ci-制品的日常环境) |
| CI制品导出 | `export-release.ps1` | CI末尾以V3清单导出已测试、扫描的Java／Web／MySQL三个镜像、四服务扫描证据、SBOM及来源；V1／V2两个应用镜像的历史验证格式仍保留 |
| 加密副本与解密 | `encrypted-backups.ps1`、`decrypt-backup.ps1` | 固定age，缺公钥拒绝对外副本；私钥单独保管 |
| 调度预览 | `ops-schedule.ps1` | 生成和校验Windows任务XML，不实际注册 |
| 凭据权限 | `secure-files.ps1` | 写前限制权限并核验，失败中止；普通临时文件使用回收站清理 |
| 制品身份核验 | `image-identity.ps1` | 发布内部辅助：验证OCI内容图及配置／平台清单身份，不能凭标签或revision代替内容校验 |
| 前端源码构建 | `build-frontends.ps1` | 安装依赖，检查兼容层和类型，构建管理端、H5、小程序 |
| 基础可用性 | `smoke.ps1` | HTTP 就绪与入口检查 |
| 运维只读检查 | `ops-check.ps1` | 任务、积压、图片、空间及验证备份；发现异常返回非零 |
| 一次性运维入口 | `ops-run.ps1 -Action Check\|Backup` | 可接定时调用，项目互斥；按配置复制验证备份、发送变化或恢复通知；不会自动注册定时任务 |
| 运维接入模板 | [`../deploy/ops-config.example.json`](../deploy/ops-config.example.json) | 复制到 `.local/ops-config.json` 配置受控副本目录与告警地址；配置与状态不提交 Git |
| 安全备份 | `safe-backup.ps1` | 明确执行人工备份或发布验证时暂停写入并独立恢复检查，之后恢复原运行状态；当前本机不启用日常备份 |
| 备份底层步骤 | `backup.ps1` | 导出数据库、文件与清单；单独运行不等同于完整安全备份流程 |
| 独立恢复核验 | `restore-check.ps1` | 读取指定备份，在临时容器恢复并核验，不覆盖日常数据库 |
| 历史库迁移预检 | `migration-preflight.ps1` | 检查重复标识与归属；只作部分预检，不能证明 schema 可直接迁移 |
| 数据清单比对 | `platform-data-manifest.ps1` | 输出／比对指定业务数据清单，仅写入受保护的 `.local/` |
| 历史容器整理 | `consolidate-containers.ps1` | 专项维护工具；先验证备份，仅处理匹配项目目录且已停止的旧容器，保留卷和镜像；首次部署无需执行 |
| 接口类型 | `generate-contracts.mjs` | 默认从已有快照生成；`--fetch` 会登录指定后端并刷新快照，不是纯本地生成 |
| 通知验收 | `check-websocket.mjs` | 连接实际后端校验票据与会话边界，需要正确环境 |
| 用户端依赖审计 | `check-client-audit.mjs` | 检查 `.local/audit-client.json`，发现漏洞或无效报告即失败 |
| 后端与镜像安全 | `scan-images.ps1` | 固定版本Trivy，扫描应用镜像及显式指定的MySQL／Redis镜像系统包、Java运行依赖；任意已知漏洞阻断，保留完整报告和CycloneDX清单 |
| 性能身份与历史夹具 | `prepare-performance.mjs` | 核验隔离项目、端口及卷；`--lunch` 增加3名热店员工会话，`--grow-history --history-orders` 将同一夹具递增至最多20万笔合成历史；不测登录容量 |
| 有限性能测量 | `measure-performance.mjs` | 默认测量1／5／10／30客户端读取及30客户端混合交易；`PERF_SCENARIO=lunch` 测量30用户三波集中交易与历史／对账读取，操作说明见运维指南 |

`docker-read.ps1` 是运维读取的超时辅助文件；`test-ops-check.ps1`、`test-release-safety.ps1`、`check-client-audit.test.mjs` 及 `tests/ops-docker-fixture.ps1` 是脚本回归与夹具，不是日常启动入口。

`test-image-identity.ps1` 和 `test-github-artifact-transfer.ps1` 验证镜像身份及分段传输的拒绝边界，CI在Windows及Linux执行。`github-artifact-transfer.ps1` 由正式下载入口调用，不能将单段传输成功当作整个制品校验成功。

涉及写入的业务、迁移和浏览器验收应使用独立项目、端口与数据卷。[日常维护](../docs/operations.md)列出备份与检查命令；完整自动验证入口是 [GitHub Actions](../.github/workflows/verify.yml)。

本机完整备份、离线镜像恢复包与自动备份保持停用，恢复指针为 `DISABLED_BY_USER`。保留这些脚本不表示恢复材料仍存在；明确执行发布仍会生成临时校验材料并更新LATEST。成功后须由维护者按[部署保留策略](../docs/platform-deployment.md#本机保留策略)回收并恢复禁用标记，发布脚本不会自动完成清理。
