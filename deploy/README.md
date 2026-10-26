# 部署配置导航

本目录集中保存部署配置和运维模板；运行命令仍从项目根目录执行。

| 文件 | 用途 |
|---|---|
| `web.Dockerfile` | 构建工作台、用户H5与Nginx镜像 |
| `nginx.conf`、`proxy-headers.conf` | 页面路由、API代理与可信代理头 |
| `maven-settings.xml` | 后端源码镜像的Maven镜像源配置 |
| `dish.svg` | 默认演示餐品图片 |
| `ops-config.example.json` | 运维接入模板；复制到 `.local/ops-config.json` 后配置副本目录和告警地址 |

根目录的 `compose.yaml`、`Dockerfile`、`pom.xml`、`.env.example` 与 Git／编辑器配置保留默认入口位置。前端 `package.json`、锁文件、Vite／TypeScript配置以及后端应用配置继续留在所属模块，便于工具默认发现和独立构建。

本机 `.env`、运维实际配置和凭据不提交Git。当前原始验证材料集中在 `.local/verification/current/{architecture,deployment,organization}/`，`.local/backups/` 只保留当前恢复集 `current-architecture-20261026/`；`.local/tools/`、`.local/trivy-cache/`、`.local/operations-state/` 和 `.local/operations-locks/` 属于工具与运维状态，按用途保留。旧验证及旧备份按明确清单移入回收站，业务卷和当前恢复集保留。

部署步骤见[项目首页](../README.md)，升级保护见[升级与恢复](../docs/platform-deployment.md)，运维模板字段见[日常维护](../docs/operations.md#可配置的一次性运维入口)。
