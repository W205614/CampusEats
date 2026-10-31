# 部署配置导航

本目录集中保存部署配置和运维模板；运行命令仍从项目根目录执行。

| 文件 | 用途 |
|---|---|
| `web.Dockerfile` | 构建工作台、用户H5与Nginx镜像 |
| `mysql.Dockerfile` | 在固定MySQL 8.4.13镜像上安装经官方SHA-256核对的curl/libcurl安全补丁 |
| `nginx.conf`、`proxy-headers.conf` | 页面路由、API代理与可信代理头 |
| `maven-settings.xml` | 后端源码镜像的Maven镜像源配置 |
| `dish.svg` | 默认演示餐品图片 |
| `ops-config.example.json` | 运维接入模板；复制到 `.local/ops-config.json` 后配置副本目录和告警地址 |

根目录的 `compose.yaml`、`Dockerfile`、`pom.xml`、`.env.example` 与 Git／编辑器配置保留默认入口位置。前端 `package.json`、锁文件、Vite／TypeScript配置以及后端应用配置继续留在所属模块，便于工具默认发现和独立构建。

本机 `.env`、运维实际配置和凭据不提交Git。验证材料的实际目录见[当前版本记录](../docs/verification/current.md)，不以历史目录名判断当前状态。完整数据备份和离线镜像恢复包已按用户要求停用，`.local/backups/LATEST.json` 和 `LATEST.txt` 为禁用标记，不代表存在可用恢复集。发布工具保留临时备份及独立恢复校验，验证完成后由维护者回收临时材料并恢复禁用标记，脚本不会自动完成这些清理步骤。`.local/tools/`、运维状态与锁按用途保留；当前镜像、业务卷、上传文件和必要配置继续保留。

部署步骤见[项目首页](../README.md)，升级保护见[升级与恢复](../docs/platform-deployment.md)，运维模板字段见[日常维护](../docs/operations.md#可配置的一次性运维入口)。
