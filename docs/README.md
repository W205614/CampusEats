# 文档导航

先按[项目首页](../README.md)启动演示、完成第一笔订单，再按需要阅读下列内容。

## 现行指南

| 想了解什么 | 阅读入口 |
|---|---|
| 目录职责、源码阅读顺序、本地前端开发 | [开发与源码阅读](development.md) |
| 角色权限、合单、履约与售后 | [平台架构](platform-architecture.md) |
| 检查任务、图片空间、备份和运行限制 | [日常维护](operations.md) |
| 升级已有数据库、备份恢复和回滚 | [升级与回滚](platform-deployment.md) |
| 微信支付、腾讯云服务及接入状态 | [生产接入总览](production-readiness.md) |
| CVM/CLB、多实例、监控与云端恢复 | [生产部署与运维](production-deployment.md) |
| 手机与微信身份、短信和订阅消息 | [身份与通知](identity-notifications.md) |
| COS、公私文件与上传结果核验 | [文件存储](storage-integration.md) |
| 前端依赖约束与兼容层 | [前端依赖说明](frontend-security-review.md) |
| 开发协作、安全私报和授权状态 | [贡献指南](../CONTRIBUTING.md)、[安全政策](../SECURITY.md)、[许可说明](licensing.md) |
| 本轮功能、修复与升级影响 | [变更记录](../CHANGELOG.md) |
| 启动、构建、验证及维护脚本的用途 | [脚本导航](../scripts/README.md) |

## 当前版本验证

- [当前版本验证与维护记录](verification/current.md)：当前源码、定向验证、容量测量与日常部署；区分本地候选和已部署CI制品。
- [生产能力补齐首轮候选留档](verification/production-upgrade.md)：首次本地候选、分阶段回归、容量与恢复历史，真实云待办仍需逐项验收。
- [部署配置导航](../deploy/README.md)：根目录、部署目录和模块配置的存放规则。
- `images/`：项目首页使用的演示截图。

新增现行操作说明放在本目录；日常部署记录以 `verification/current.md` 为准，本轮生产能力源码及验收范围以 `verification/production-upgrade.md` 为准，各自列明本机证据目录。完整数据备份和离线镜像恢复包已按用户要求停用，`.local/backups/LATEST.json` 和 `LATEST.txt` 为禁用标记，不提供恢复能力。人工备份与发布验证工具仍保留，但不会自动运行；明确执行发布时产生的临时恢复材料须在验证完成后人工回收，并恢复指针的禁用标记，步骤见部署说明。密钥、原始日志及临时备份不提交 Git；源码、当前镜像、业务卷、上传文件、必要配置和运维状态继续保留。
