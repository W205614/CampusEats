# 当前版本验证

[当前版本验证与维护记录](current.md) 是唯一的版本证据入口，包含当前V12源码改动、定向验证、日常部署、恢复与镜像扫描，以及目录清理结果。

记录区分本地定向检查、既有部署证据和最终提交的CI；数字只适用于注明的源码与环境。完整自动验证以该提交SHA对应的 [GitHub Actions](https://github.com/W205614/CampusEats/actions/workflows/verify.yml) 为准。

旧日期报告、性能JSON和历史截图不再累计，已按明确清单移入回收站。原始本机材料仅保留 `.local/verification/current/`，备份、凭据及镜像包受Git忽略。操作说明见[文档导航](../README.md)。
