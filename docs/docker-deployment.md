> 历史记录：本文所引用的课程实现及旧部署资源已于 2026-10-11 移出当前目录，可在[整理前提交](https://github.com/W205614/CampusEats/tree/f85f3e7faf36b9ef3bd8d6e4a58c33155b3a18e8)中追溯。当前运行与验证以 README 为准。

# Docker Desktop 部署与小程序接入

## 当前访问方式

| 入口 | 地址或路径 | 说明 |
| --- | --- | --- |
| 管理端 | http://localhost:18083 | 管理员 `admin` / `123456` |
| 接口文档 | http://localhost:18083/doc.html | Knife4j，两组接口 |
| 店铺状态 | http://localhost:18083/user/shop/status | 无需登录，空 Redis 默认打烊 |
| 微信小程序 | `E:\project\CampusEats\mp-weixin` | 导入微信开发者工具 |
| Docker Desktop 项目 | `campuseats` | mysql、redis、server、web |

默认只绑定 `127.0.0.1:18083`。数据库和 Redis 不映射主机端口，Java 8080 仅容器内部可用。Nginx `/api/` 转发至 Java `/admin/`，`/user/` 与 `/ws/` 保留路径。WebSocket 使用当前页面主机和端口。

## 启动与停止

Docker Desktop使用Linux容器模式。启动脚本优先用本机Maven构建并执行回归测试；没有Maven时使用Maven容器。需要Node.js执行课件前端补丁，建议使用PowerShell 7，烟测上传使用其`-Form`。

```powershell
cd E:\project\CampusEats
pwsh -NoProfile -File scripts/start.ps1
docker compose ps
docker compose logs --tail 100 server
```

`start.ps1` 首次生成 `.env`，使用随机 MySQL、Redis 和 JWT 密钥；再次运行保留该文件。构建失败或容器健康检查失败会报错。更改 Java 代码后重新运行启动脚本；已有最新 JAR 时可用 `-SkipBuild`。

```powershell
# 停止服务，保留数据库、Redis 和图片
docker compose stop
# 启动已有容器
docker compose start
# 删除本项目容器和网络，仍保留持久卷
docker compose down
```

持久卷为 `campuseats_mysql-data`、`campuseats_redis-data`、`campuseats_uploads`。初始化 SQL **只在全新 MySQL 数据卷上执行**，重启不会重新导入，也不会清空订单。不要用 `down -v` 作为常规重启命令。

Compose 使用 `healthcheck` 和 `depends_on: service_healthy` 等待数据库初始化和接口可用，依据 [Docker 官方启动顺序说明](https://docs.docker.com/compose/how-tos/startup-order/)。这里只验证本地部署，未实现高可用或自动数据库迁移。

## 微信开发者工具

1. 导入 `E:\project\CampusEats\mp-weixin`，工程类型为小程序。
2. 项目 AppID 沿用课件配置。若没有该 AppID 的权限，请在开发者工具中选择自己的 AppID 或受支持的测试模式；本次没有验证微信账号或 AppID 权限。
3. 本地模拟器请求 `http://localhost:18083`；项目配置已有 `urlCheck: false`。如果工具仍提示域名校验错误，请核对该工具当前的本地调试设置。
4. 管理端切换“营业中”，再在小程序中浏览菜品、加入购物车、设置收货地址、提交订单。
5. Docker 默认使用固定的 `campuseats-local-demo` 身份；所有演示登录共享一个用户。支付按钮只更新本地订单状态，不调用 `wx.requestPayment`，不涉及真实资金。

小程序是编译后的 uni-app 微信产物，并不是完整的 uni-app 源工程，也不是 H5 网站。运行在开发者工具/微信中，无法把它放到 Nginx 后直接通过浏览器执行。本次检查了请求路径、JS 语法和接口链路，**未执行微信开发者工具 UI 或真机验收**。

原文件保留在用户给定的 D 盘目录。本项目副本修改了接口地址、相对图片 URL 的转换，以及课件中写死的客服电话。修改端口时也要同步小程序 `common/vendor.js` 中的 `baseUrl`，或运行 `scripts/prepare-assets.ps1 -Port 新端口`。

## 演示模式与真实集成

`.env` 的默认设置：

```dotenv
DEMO_ENABLED=true
MOCK_PAYMENT_ENABLED=true
DELIVERY_CHECK_ENABLED=false
BIND_ADDRESS=127.0.0.1
WEB_PORT=18083
```

演示用户免调用微信身份接口；配送距离免调用百度API；图片保存到持久卷并由`/uploads/`提供。原课件OSS图片返回403，22道种子菜品改用课件PNG照片，两道汤保留占位图，可在管理端上传替换。初始化`03-course-images.sql`只替换这些种子菜品的旧占位路径，不覆盖用户上传；已有数据卷升级时需单独应用该SQL，本机已应用。

商家联系电话可在`.env`设置`SHOP_PHONE`后重建server容器；留空时小程序提示未设置电话，避免拨打课件示例号码。修改管理端/小程序补丁后运行启动脚本会重新应用补丁，原始D盘课件不变。

切换真实微信登录时，在不入库的 `.env` 设置 `DEMO_ENABLED=false`、`WECHAT_APPID` 和 `WECHAT_SECRET`，并确保开发者工具的 AppID 与后端一致；启用百度配送校验还需设置 `DELIVERY_CHECK_ENABLED=true`、`BAIDU_AK` 和 `SHOP_ADDRESS`。然后运行：

```powershell
docker compose up -d --force-recreate server
```

`MOCK_PAYMENT_ENABLED=false` 会明确拒绝当前支付接口，**不是开启真实微信支付**。真实支付还需要商户资质、证书、签名验签、金额校验、幂等通知以及退款闭环；课件小程序的真实支付调用也被注释了。

真机中的 `localhost` 指向手机本身。需要真机调试时，另行配置电脑局域网地址或受微信支持的 HTTPS 域名、入口绑定和微信平台域名；默认配置只用于本机，不是公网部署。本次未调整防火墙或开放公网。

## 课件资源与复现

数据库源：课件 `资料\day01\数据库\sky.sql`。管理端源：`资料\day01\前端运行环境\nginx-1.20.2\html\sky`。小程序源：`资料\day06\微信小程序代码\mp-weixin`。

数据库脚本已纳入 `deploy/mysql`。编译后管理端和小程序保持 gitignore，以免混入大量构建产物；在这台电脑上文件已经复制完成。换电脑或新克隆需提供课件并运行：

```powershell
pwsh -NoProfile -File scripts/prepare-assets.ps1 -CourseRoot '你的课件资料目录'
pwsh -NoProfile -File scripts/start.ps1
```

如果更换课件版本导致管理端 JS 文件名或小程序编译结构变化，需要重新核对资源准备脚本，不能假定文本替换仍然有效。

## 验证范围

```powershell
mvn -B -ntp verify
pwsh -NoProfile -File scripts/smoke.ps1 -VerifyRestart
```

新增回归测试验证支付定位、重复/并发模拟支付、非法订单拒绝、用户归属、客户端金额篡改和非演示配置默认拒绝模拟支付。烟测会创建并保留两笔演示订单，完成一笔、取消一笔，保留演示地址与上传图片，并将店铺切为营业中。`-VerifyRestart` 会重启本项目 Java 容器。

原本机 `com.sky.test` 中的 9 个课件测试没有纳入默认构建：Redis 示例注释掉 Spring 测试启动，7 个用例因未注入而失败；两个 HTTP 示例依赖完整开发环境。本次不把它们描述为通过。如果自行配置好课件测试环境，可用 `-Dlesson.tests.exclude=**/NoLessonTest.java` 取消排除。
