# 文档导航

先按[项目首页](../README.md)启动演示、完成第一笔订单，再按需要阅读下列内容。

## 现行指南

| 想了解什么                           | 阅读入口                                                                             |
| ------------------------------------ | ------------------------------------------------------------------------------------ |
| 目录职责、源码阅读顺序、本地前端开发 | [开发与源码阅读](development.md)                                                     |
| 角色权限、合单、履约与售后           | [平台架构](platform-architecture.md)                                                 |
| 检查任务、图片空间、备份和运行限制   | [日常维护](operations.md)                                                            |
| 升级已有数据库、备份恢复和回滚       | [升级与回滚](platform-deployment.md)                                                 |
| 微信支付、腾讯云服务及接入状态       | [生产接入总览](production-readiness.md)                                              |
| CVM/CLB、多实例、监控与云端恢复      | [生产部署与运维](production-deployment.md)                                           |
| 手机与微信身份、短信和订阅消息       | [身份与通知](identity-notifications.md)                                              |
| COS、公私文件与上传结果核验          | [文件存储](storage-integration.md)                                                   |
| 前端依赖约束与兼容层                 | [前端依赖说明](frontend-security-review.md)                                          |
| 开发协作、安全私报和授权状态         | [贡献指南](../CONTRIBUTING.md)、[安全政策](../SECURITY.md)、[许可说明](licensing.md) |
| 本轮功能、修复与升级影响             | [变更记录](../CHANGELOG.md)                                                          |
| 启动、构建、验证及维护脚本的用途     | [脚本导航](../scripts/README.md)                                                     |

## 当前版本验证

- [当前版本验证与维护记录](verification/current.md)：最终提交、CI、全量回归、容量测量、日常部署及维护方式。
- [生产能力补齐首轮候选留档](verification/production-upgrade.md)：早期候选阶段的回归、容量与恢复过程。
- [部署配置导航](../deploy/README.md)：根目录、部署目录和模块配置的存放规则。
- `images/`：项目首页使用的演示截图。

新增现行操作说明放在本目录；最终验收和日常部署以 `verification/current.md` 为入口。密钥、原始日志及本机验收材料保存在受 Git 忽略的目录。自动备份停用状态、临时恢复材料回收和文件保留规则统一见[本机保留策略](platform-deployment.md#本机保留策略)。
