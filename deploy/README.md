# 部署配置导航

本目录集中保存部署配置和运维模板；运行命令仍从项目根目录执行。

| 文件                               | 用途                                                                                                                                                                    |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `web.Dockerfile`                   | 构建工作台、用户H5与Nginx镜像                                                                                                                                           |
| `mysql.Dockerfile`                 | 在固定MySQL 8.4.13镜像上安装经官方SHA-256核对的curl/libcurl 和 glibc 安全补丁                                                                                           |
| `nginx.conf`、`proxy-headers.conf` | 页面路由、API代理与可信代理头                                                                                                                                           |
| `maven-settings.xml`               | 后端源码镜像的Maven镜像源配置                                                                                                                                           |
| `dish.svg`                         | 默认演示餐品图片                                                                                                                                                        |
| `ops-config.example.json`          | 运维接入模板；复制到 `.local/ops-config.json` 后配置副本目录和告警地址                                                                                                  |
| [`production/`](production/)       | 生产环境变量与云接入模板、可信代理配置，以及 Prometheus/Grafana/Alertmanager 模板；使用根目录 `compose.production.yaml`，见[生产部署](../docs/production-deployment.md) |

根目录的 `compose.yaml`、`Dockerfile`、`pom.xml`、`.env.example` 与 Git／编辑器配置保留默认入口位置。前端 `package.json`、锁文件、Vite／TypeScript配置以及后端应用配置继续留在所属模块，便于工具默认发现和独立构建。

本机 `.env`、运维实际配置和凭据保存在受 Git 忽略的目录。部署与验证材料见[当前版本记录](../docs/verification/current.md)；备份停用状态、临时材料回收及配置、工具、业务数据保留规则统一见[本机保留策略](../docs/platform-deployment.md#本机保留策略)。

部署步骤见[项目首页](../README.md)，升级保护见[升级与恢复](../docs/platform-deployment.md)，运维模板字段见[日常维护](../docs/operations.md#可配置的一次性运维入口)。
