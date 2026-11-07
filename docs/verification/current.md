# 当前版本验证与维护记录

本地点餐、合单、履约、售后、权限和账务功能已完成回归，当前进入按需维护阶段。出现可复现问题、依赖升级或新业务需求时再修改；微信与腾讯云接入按专题指南另行安排。

## v1.0.1 版本与运行核对

当前版本入口为 [v1.0.1](https://github.com/W205614/CampusEats/releases/tag/v1.0.1)，后端模块为 `campuseats-common`、`campuseats-api` 和 `campuseats-server`，Java 包名为 `com.campuseats`，入口为 `CampusEatsApplication`。后端与两端应用版本统一为 `1.0.1`。本轮整理不增加业务功能，不改变 HTTP 契约或 V1—V24 迁移内容；数据库标识和业务卷保持原样。

使用 `git rev-parse v1.0.1` 核对发布提交，按该提交的[CI 结果](https://github.com/W205614/CampusEats/actions/workflows/verify.yml)和 Release 附件验证源码与制品。本机完整收尾回执位于 `.local/release-1.0.1-20261107/final-validation.json`，其中记录本地与远端、Tag、Release、四个运行镜像的对应关系，以及数据核验、临时恢复负载回收和最终容器状态；实际部署记录仍保存在 `.local/releases/artifact-*/release.json`。

下文是 2026-11-06 的历史业务基线、容量测量和当时的收尾统计；不将历史容量数字当成本轮重新执行的容量测试。当前发布和运行状态以上述版本回执为准。

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

日常四个容器在健康验收后已停止，三个业务卷和上传文件保留。已清理 24 个测试容器、18 个测试卷、6 个旧网络和 13 个旧镜像；78 项过时文件及临时备份共 4,001,984,923 字节已移入 Windows 回收站并核验。文件仍占用回收站空间。

完整数据备份、离线镜像包与自动备份保持停用，`LATEST.json` / `LATEST.txt` 为 `DISABLED_BY_USER` 标记，当前没有可用恢复集。详细操作见[本机保留策略](../platform-deployment.md#本机保留策略)。

## 后续外部接入

微信商户权限、真实付款退款、腾讯云短信与 COS、CLB/CVM、云端监控、托管 MySQL PITR、COS 版本恢复及小程序真机验收，在准备好真实资源后按[生产接入](../production-readiness.md)和[生产部署](../production-deployment.md)执行。
