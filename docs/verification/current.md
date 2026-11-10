# 当前版本验证与维护记录

本地点餐、合单、履约、售后、权限和账务功能已完成回归，当前进入按需维护阶段。出现可复现问题、依赖升级或新业务需求时再修改；微信与腾讯云接入按专题指南另行安排。

## v1.0.4 · 镜像发布与本轮验证

2026-11-10 发布 [v1.0.4](https://github.com/W205614/CampusEats/releases/tag/v1.0.4)，应用来源为 [`b88b67ae074fb2cb27decebde03a1eeaff9a6d5e`](https://github.com/W205614/CampusEats/commit/b88b67ae074fb2cb27decebde03a1eeaff9a6d5e)。本节是该版本的实测结果，后续 README 提交不改变其 Tag 或镜像身份。

| 范围 | 本轮结果与证据 |
| --- | --- |
| 源码 CI | [master](https://github.com/W205614/CampusEats/actions/runs/42781284511)、[Tag](https://github.com/W205614/CampusEats/actions/runs/42784938669) 均成功，各为后端 360 项、浏览器 95 项，失败、错误、跳过为 0；包括真实集成和隔离协议/传输边界测试 |
| 安全与质量 | [完整 CodeQL 分析](https://github.com/W205614/CampusEats/actions/runs/42781284651)通过，四语言分析均完成；CodeQL、Dependabot、秘密扫描当前未解决项为 0。9 条经维护者认可的原始 CodeQL 发现继续保留，见[审阅说明](../security-review.md) |
| 四镜像扫描 | 同次 master CI 的 Java、Web、MySQL、Redis 扫描均通过，SBOM、完整报告与清单哈希绑定；不以扫描通过保证不存在所有安全风险 |
| 实际镜像发布 | [推广任务](https://github.com/W205614/CampusEats/actions/runs/42789942941)第二次尝试成功；第一次为 GHCR `unknown blob`，先确认目标标签均不存在再重试，没有重建镜像或新建版本 |
| 公开获取与运行 | 两个 GHCR 应用镜像匿名摘要拉取通过，配置摘要与解压层序列匹配原 CI；隔离四服务健康，真实页面完成选餐、优惠报价、配送、分次退款、平台介入和评价，资金为模拟 |
| 宿主源码 | Windows 上 Maven 3.9.9 / Java 21 安装与 `spring-boot:run` 通过，独立数据库/Redis、健康、两种登录和目录读取通过；安装跳过测试，测试结论来自上述完整 CI。复用同配方精确依赖镜像，未新做 Windows 依赖镜像构建 |
| 本机日常升级 | 四镜像采用同次 CI 内容；V24、69 张表原字段、10 个上传文件、非镜像配置核对通过。Redis 的非过期持久键前后均为 0，指纹一致；未将此检查称为对任意 Redis 数据集的完整迁移证明 |
| 日常只读边界 | 平台管理员/用户实际覆盖 14 页、30 项 API，店长、运营、配送员凭据未提供；隔离环境的合成角色流程不代替这些日常角色验收。四服务最终恢复停止 |

应用版本均为 `1.0.4`，小程序版本号 `104`，没有新增数据库迁移。旧密码 MD5 首次登录迁移与微信账单 SHA-1 协议作为明确审阅的兼容/协议约束保留，不称为算法已替换或零原始发现。

镜像仓库为 `ghcr.io/w205614/campuseats-server`、`ghcr.io/w205614/campuseats-web`，仅支持本次验证的 `linux/amd64`。不可变摘要、完整验证 JSON、五文件 `SHA256SUMS`、运行时对应源码和 Java 完整源码补充均在 [Release 附件](https://github.com/W205614/CampusEats/releases/tag/v1.0.4)。小附件完成匿名完整下载与摘要核对；大型运行时源码归档核对完整存储摘要与匿名 1 MiB 字节段，没有声称匿名完整下载整个归档。

本机升级的临时恢复资料经过独立恢复；其具体保留/回收由本机清单与维护者确认管理，禁用指针和历史核验元数据不是可用恢复负载。真实微信支付/身份、腾讯云、生产多实例和小程序真机仍未在本轮验收，亦未新增生产容量认证。

首页的两张 v1.0.4 图片来自上述公开镜像隔离旅程，均为合成订单和模拟金额。历史截图继续保留在源码中，不冒充本轮新画面。

## v1.0.3 发布准备修复

2026-11-09 的维护源码版本为 `1.0.3`。修复 `release.ps1` 在升级前数据指纹采集失败时未恢复原应用状态、未保存失败回执的问题；此阶段没有启动候选或迁移。运行中的应用会请求恢复，已停止的应用继续停止，已验证备份及 `capture_failed` 回执保留。迁移后失败仍保持停写。

本地故障注入已先复现旧行为，再验证运行中与已停止两种应用状态、配置不变、候选未启动、恢复引用和失败时间记录；现有发布编排回归通过。没有新增依赖、业务接口或 V1—V24 迁移。最终 CI、发布物获取、四服务镜像及日常升级结果在 `.local/release-1.0.3-20261109/final-validation.json` 中分别记录，未完成步骤不视为通过。远端入口为 [Actions](https://github.com/W205614/CampusEats/actions/workflows/verify.yml) 与 [Releases](https://github.com/W205614/CampusEats/releases)。

## v1.0.2 源码与本地验证

2026-11-09 的维护源码版本为 `1.0.2`：修复两端开发服务器的演示图片 404、生产 Nginx 模板未提供演示图片和许可文件，以及固定工具下载连续遇到 HTTP 504 而中断 CI 的问题。没有业务接口、数据迁移或运行依赖变化。Trivy/age 保持原版本、来源和固定摘要；仅对 502/503/504 最多请求三次，每次 120 秒超时、间隔 2 秒，摘要验证后才写入最终缓存；其他错误及重试耗尽直接失败。

本地 Node.js 24.19.0 / PowerShell 7 验证：管理端 2 项和用户端 7 项工具链测试、管理端构建、用户端类型检查、H5 与微信小程序构建通过；两端真实开发服务器的 2 项 HTTP 回归通过。隔离 Nginx 容器使用生产模板验证图片及三份许可文件可访问、缺失资源和内部端点返回 404。该容器只使用独立端口与临时配置，不挂载业务卷，测试后自动退出。

上述结果属于提交前的本地验证；最终提交的 CI、Tag、Release 和附件状态应从 [Actions](https://github.com/W205614/CampusEats/actions/workflows/verify.yml) 与 [Releases](https://github.com/W205614/CampusEats/releases) 分别核对。本机本轮收尾入口为 `.local/release-1.0.2-20261109/final-validation.json`。本轮未部署日常环境，运行制品仍按下面的 v1.0.1 部署记录核对；历史业务与容量结果不视为本轮重新执行。

初始候选 `cf28b36` 的两次 CI 分别因 Trivy 和 age 下载返回 504 失败，不能视作完整通过。追加工具下载修复后，本地真实 HTTP 的重试上限、404/摘要失败、缓存保留回归通过；现有真实 age 加密往返与损坏拒绝、运维 runner 和扫描编排回归通过。最终发布以包含该修复的后续提交及其 CI 为准。

下载传输后续收敛为显式 HTTP/1.1 与同一 GitHub 资产的 `?download=1` 入口，age/Trivy 的完整归档通过原固定摘要核对；不增加宿主依赖或镜像站。持续网络失败仍会明确中断，不能将有限重试当作外部服务可用性保证。

## v1.0.1 发布与日常部署记录

2026-11-07 发布的 [v1.0.1](https://github.com/W205614/CampusEats/releases/tag/v1.0.1) 为当时日常部署版本，后端模块为 `campuseats-common`、`campuseats-api` 和 `campuseats-server`，Java 包名为 `com.campuseats`，入口为 `CampusEatsApplication`。该版本后端与两端应用均为 `1.0.1`，没有改变 HTTP 契约或 V1—V24 迁移内容；数据库标识和业务卷保持原样。

使用 `git rev-parse v1.0.1` 核对发布提交，按该提交的[CI 结果](https://github.com/W205614/CampusEats/actions/workflows/verify.yml)和 Release 附件验证源码与制品。本机完整收尾回执位于 `.local/release-1.0.1-20261107/final-validation.json`，其中记录本地与远端、Tag、Release、四个运行镜像的对应关系，以及数据核验、临时恢复负载回收和最终容器状态；实际部署记录仍保存在 `.local/releases/artifact-*/release.json`。

下文是 2026-11-06 的历史业务基线、容量测量和当时的收尾统计；不将历史容量数字当成本轮重新执行的容量测试。各次发布和部署状态以对应版本回执为准。

## 已完成的业务基线验收

2026-11-06 完成应用提交 `58fac94e5dca7531d241d37c3d90722b167740cc` 的验证与日常部署，数据库升级至 V24。[CI 运行 42226756510](https://github.com/W205614/CampusEats/actions/runs/42226756510) 成功，部署使用该次 CI 测试并扫描的同一制品。

| 检查             | 结果                                                                            |
| ---------------- | ------------------------------------------------------------------------------- |
| 后端完整验证     | 343 项通过，失败、错误、跳过均为 0                                              |
| 前端与接口       | 两端工具链、类型、管理台/H5/小程序构建及 OpenAPI 生成比对通过                   |
| 浏览器与实时通知 | 95 项浏览器测试及 WebSocket 检查通过                                            |
| 双实例           | 会话、目录版本、票据、广播、幂等与停止节点恢复共 7 项通过                       |
| 数据与文件       | 独立恢复通过；日常升级时原 49 张表的旧字段与主键核对通过，10 个上传文件摘要一致 |
| 安全与制品       | 依赖审计、四服务镜像扫描及制品内容核验通过                                      |

本机证据目录为 `.local/enterprise-release-20261106/`：`final-validation.json` 为最终回执，`ci-final-success.json` 为 CI 记录，`artifact-qa-final/summary.json` 为最终制品验收。早期候选阶段另见[历史留档](production-upgrade.md)。

## 容量测量

使用原午餐场景：30 个用户、3 个员工、20 万条合成历史订单、300 秒三波交易。读取 P95 目标为 1 秒，交易 P95 目标为 10 秒。

| 指标                                  | 最终实测          |
| ------------------------------------- | ----------------- |
| HTTP 请求 / 完整交易                  | 9,727 / 90        |
| 非预期错误 / 失败工作线程             | 0 / 0             |
| 总体读取 P95                          | 124.50 ms         |
| 商家历史 / 对账 P95                   | 76.20 / 610.18 ms |
| 完整交易 P95                          | 5,219.08 ms       |
| 任务积压、业务约束、OOM、异常重启检查 | 通过              |

资源条件为 Windows / Docker Desktop / WSL2 共享开发主机，MySQL 内存上限 2 GiB、memory+swap 上限 4 GiB、InnoDB 缓冲池 512 MiB；Java 768 MiB、Redis 160 MiB、Web 128 MiB。结果对应本机合成数据与模拟资金场景。原始报告为 `performance-provisioned/report.json`，资源依据为同目录 `resource-decision.md`；前两轮未达标报告保留在 `performance/` 和 `performance-final/`。

## 源码、CI 与运行实例

上述提交是本轮业务验收基线。收尾整理统一了新增页面、脚本与配置的排版，并将订单页的支付状态名称映射移入脚本区；应用制品按后续成功 CI 更新，实际提交与镜像以 `.local/releases/artifact-*/release.json` 为准。收尾检查记录在 `.local/maintenance-closeout-20261106/`。

- 使用 `git rev-parse HEAD`、`git status --short` 和 `git ls-remote origin refs/heads/master` 核对本地与远端。
- [Verify CampusEats](https://github.com/W205614/CampusEats/actions/workflows/verify.yml) 保存各提交的完整自动验证结果。
- CI 的 `release-<SHA>` 制品绑定来源、run、attempt、镜像和扫描摘要；发布按该清单核验。
- 使用 `docker compose ps -a` 查看容器状态，以 `docker inspect` 的内容摘要对照本机 `.local/releases/artifact-*/release.json` 中的部署记录。

写入式回归在独立项目、端口和合成数据中执行。日常环境检查健康、角色权限、只读数据和文件。

## 本机收尾状态

以下为 **2026-11-06 的历史收尾**，当前版本以顶部 v1.0.4 记录为准。日常四个容器在当时健康验收后停止，三个业务卷和上传文件保留。当时清理 24 个测试容器、18 个测试卷、6 个旧网络和 13 个旧镜像；78 项过时文件及临时备份共 4,001,984,923 字节移入 Windows 回收站并核验。回收量不等于磁盘释放量。

当时完整数据备份、离线镜像包与自动备份保持停用，`LATEST.json` / `LATEST.txt` 为 `DISABLED_BY_USER` 标记，未保留可用恢复集。后续临时人工恢复资料另按相应维护回执核对，详细约定见[本机保留策略](../platform-deployment.md#本机保留策略)。

## 后续外部接入

微信商户权限、真实付款退款、腾讯云短信与 COS、CLB/CVM、云端监控、托管 MySQL PITR、COS 版本恢复及小程序真机验收，在准备好真实资源后按[生产接入](../production-readiness.md)和[生产部署](../production-deployment.md)执行。
