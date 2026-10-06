# 本地部署验证记录

日期：2026-10-06。工作目录：`E:\project\CampusEats`。验证对象为本次修改后的本地工作区，未创建提交、推送或公开部署。

以下为首次部署记录；后续代码已继续修改，当前24项Java回归及功能/体验结果见 [functional-ux-review.md](functional-ux-review.md)。下面的旧JAR哈希仅用于首次部署的历史证据。

## 已确认结果

- Docker Desktop Linux 引擎可用，四个 `campuseats` 服务均为 healthy，唯一主机入口为 `127.0.0.1:18083`。
- `mvn -B -ntp verify` 构建成功。`LocalDeploymentRegressionTest`：9 项，0 失败、0 错误、0 跳过。原外部服务课件测试单独排除，不能计为通过。
- `scripts/smoke.ps1 -VerifyRestart` 成功：管理端及文档入口、缺令牌 401、管理员登录、演示用户登录、浏览分类/菜品、购物车、默认收货地址、服务端金额计算、下单、重启后订单保留、模拟支付、WebSocket 通知、重复支付、商家接单/派送/完成、取消另一笔订单、历史订单、本地图片上传及读取。
- 烟测订单 4 已完成、订单 5 已取消；演示地址和上传图片保留在本项目持久卷。店铺已切为营业中。未触发真实资金交易。
- 浏览器 UI 登录成功，工作台统计可见，菜品页显示正常中文与示例图片。运行截图为 [admin-dishes.png](evidence/admin-dishes.png)。
- 修正 SQL 字符集后，使用一个独立临时数据库重新导入两份初始化 SQL，确认“王老吉”“管理员”中文正确、24 道菜品、管理员密码与 MD5 比对匹配。验证后仅删除该临时数据库，本项目业务库保留。
- Swagger 返回“用户端接口”和“管理端接口”两个分组。
- 小程序 `common/vendor.js` 与管理端修改后的 `app.d0aa4eb3.js` 均通过 `node --check`。PowerShell 脚本语法、Compose 配置和 `git diff --check` 通过。
- JAR 仅含 `application.yml` 和 `application-docker.yml`，不含本机 `application-dev.yml`。`.env`、原开发配置和课件编译前端继续被 gitignore。
- 容器内 `/app/app.jar` 与经过测试的本机 JAR 的 SHA-256 一致：`0d56a5945320492902f307f60f55e2bca0d0932444add8e36f9162d1660ee043`。

本地完整构建日志为 `build-final.log`，部署日志为 `deploy-local.log` / `redeploy-local.log`，业务烟测日志为 `smoke-local.log`；这些日志不入库。

## 未验证范围

微信开发者工具 UI、微信 AppID/账号权限、真机网络、真实微信登录、真实支付退款、百度配送路线、外部 OSS，以及全部业务接口的异常和并发路径均未完成验收。本结果证明本机演示环境和上述已执行链路可用，不代表生产交付。
