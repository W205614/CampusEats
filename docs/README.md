# 文档导航

先按[项目首页](../README.md)启动演示、完成第一笔订单，再按需要阅读下列内容。

## 现行指南

| 想了解什么 | 阅读入口 |
|---|---|
| 目录职责、源码阅读顺序、本地前端开发 | [开发与源码阅读](development.md) |
| 角色权限、合单、履约与售后 | [平台架构](platform-architecture.md) |
| 检查任务、图片空间、备份和运行限制 | [日常维护](operations.md) |
| 升级已有数据库、备份恢复和回滚 | [升级与回滚](platform-deployment.md) |
| 前端依赖约束与兼容层 | [前端依赖说明](frontend-security-review.md) |
| 启动、构建、验证及维护脚本的用途 | [脚本导航](../scripts/README.md) |

## 当前版本验证

- [当前版本验证与维护记录](verification/current.md)：当前V12源码、日常部署与目录整理的范围、结果和限制；以记录中的提交与环境为准。
- [部署配置导航](../deploy/README.md)：根目录、部署目录和模块配置的存放规则。
- `images/`：项目首页使用的演示截图。

新增现行操作说明放在本目录；`verification/current.md` 只维护当前版本记录，不累计旧日期报告。对应本机材料统一放在 `.local/verification/current/{architecture,deployment,organization}/`；旧验证、历史验收备份和旧镜像包按明确清单移入回收站。密钥、原始日志、备份及镜像包不提交 Git，当前业务数据卷、最新恢复集和运维状态不属于过时验证文件。
