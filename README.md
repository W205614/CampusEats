# CampusEats · 校园多校区点餐平台

CampusEats 面向校园内多家餐饮店，提供用户点餐、商家接单、校区配送与售后管理。用户可以跨店、跨校区合并结算，各店独立备餐，按店选择配送或到店自取。

基于 **Spring Boot + Vue 3 + uni-app + MySQL + Redis**，采用单体、单实例后端，通过 Docker Compose 启动 MySQL、Redis、Java 后端和 Nginx 四个服务。

> 默认提供演示登录、模拟支付和模拟退款，不发生真实资金交易。适合本地体验和学习；微信小程序登录需要另行配置，尚未完成微信真机验收。不包含商户资金结算、自动路线规划或第三方配送。

## 功能与解决的问题

| 场景 | 提供的功能 |
|---|---|
| 校区和店铺分散，管理范围容易混淆 | 统一管理多个校区与店铺，店长、运营员按店授权，配送员按校区处理任务 |
| 一次想买多家店的餐品 | 搜索校区、店铺与餐品，独立店铺购物车，跨校区合并结算，各店分别履约 |
| 点餐后需要配送或自取 | 商家接单、备餐、派单，校区配送员抢单、取餐与送达，到店自取码核销 |
| 需要营销和日常经营工具 | 菜品与套餐、口味、每日配额、营业时间、活动价、店铺券、平台券和经营统计 |
| 订单出现问题，需要后续处理 | 取消订单、催单、图片举证、部分或整单退款、平台介入，以及收藏和评价 |

跨校区合单时，各店的配送地址仍须属于该店所在校区；合并付款后，各店独立处理订单。

## 效果图

以下为本地演示环境截图。

**用户点餐端：选择校区与店铺、浏览餐品**

<img src="docs/images/platform-h5-menu.png" alt="H5 点餐端：校区选店、餐品列表与购物车入口" width="390">

**平台工作台：管理校区与独立店铺**

![平台工作台](docs/images/platform-dashboard.png)

**员工登录与用户点餐入口**

![员工登录页](docs/images/platform-login.png)

## 快速部署

以下步骤用于**首次本地演示部署**。已有业务数据的环境，请先阅读[升级与回滚说明](docs/platform-deployment.md)。

### 1. 准备环境

- 安装 Git、PowerShell 7（命令为 `pwsh`）。Windows 自带的 Windows PowerShell 5.1 不适用于此启动脚本。
- 安装并启动 Docker Desktop，使用 **Linux 容器模式**，确保可用 `docker compose`。
- 首次构建需要联网下载基础镜像和依赖。下面的方式在 Docker 内构建应用，无需本机安装 Java、Maven 或 Node.js。

### 2. 下载并启动

打开 PowerShell 7 终端，依次执行：

```powershell
git clone https://github.com/W205614/CampusEats.git
cd CampusEats
pwsh -NoProfile -File ./scripts/start.ps1 -SkipVerify
```

脚本自动生成本机 `.env` 和随机初始密码，从源码构建镜像、初始化数据库与演示数据，并等待服务就绪。`-SkipVerify` 跳过本机 Maven 后端验证；省略该参数需另装 Java 21 和 Maven。

启动结束后查看服务状态与初始密码：

```powershell
docker compose ps
Get-Content .local/demo-credentials.txt
```

四个服务均应显示 `healthy`。初始密码文件仅在本机保存，不要上传或分享。

### 3. 打开页面并登录

| 入口 | 地址 | 登录方式 |
|---|---|---|
| 用户点餐 | [http://localhost:18083/](http://localhost:18083/) | 点击「登录点餐」，选择一个演示用户，无需密码 |
| 平台／商家／配送工作台 | [http://localhost:18083/admin/](http://localhost:18083/admin/) | 使用下表账号，初始密码见 `.local/demo-credentials.txt` |

全新数据库会初始化两个校区、每校区两家店及以下员工账号：

| 角色 | 账号 |
|---|---|
| 平台管理员 | `admin` |
| 店长 | `manager1` ～ `manager4` |
| 店铺运营员 | `operator1` ～ `operator4` |
| 校区配送员 | `courier1`、`courier1b`、`courier2`、`courier2b` |

新建的店长、运营员和配送员共用文件中的初始密码，首次登录必须改密。已有账号保留原密码，修改 `.env` 或重新生成密码文件不会重置账号。

### 4. 体验第一笔订单

1. 先用 `admin` 登录工作台，在「校区与店铺」找到「默认店铺」，打开「营业配置」，确认营业时段，开启「接收新订单」并保存。全新数据库的店铺默认关闭接单。
2. 在用户端登录演示用户，选择「默认校区 → 默认店铺」，选餐加购；进入购物车，点击「选择此店」后「合并结算」。
3. 首次可选择「到店自取」，点击「计算报价」，提交订单后在订单页完成「整批模拟支付」。如选择配送，先添加与店铺同校区的收货地址。
4. 在工作台退出 `admin`，改用默认店铺的店长 `manager1` 登录并完成首次改密；进入「订单履约」，完成「接单备餐」和「备餐完成」。自取订单使用用户订单页的核销码完成核销；配送订单可派单或由本校区配送员抢单，再完成取餐、送达。

也可以用 `admin` 在工作台查看校区、店铺和员工归属。用户端与员工端会话独立，可在同一浏览器切换体验。三个演示用户各自隔离数据，但同一演示用户编号由所有访问者共用。

## 常见问题与日常使用

| 遇到的问题 | 处理方式 |
|---|---|
| 找不到 `pwsh`，或脚本运行报版本相关错误 | 安装 PowerShell 7，使用上面的 `pwsh -NoProfile -File ...` 命令启动 |
| Docker 连接失败或构建下载失败 | 确认 Docker Desktop 已启动且处于 Linux 容器模式，并检查镜像源与依赖下载的网络连接 |
| `18083` 端口被占用 | 修改 `.env` 的 `WEB_PORT`，同步修改 `ALLOWED_ORIGINS` 中两个地址的端口，再运行启动命令；之后使用新端口访问 |
| 店铺休息中，无法下单 | 在工作台检查对应店铺的接单开关、营业时段和商品配额；新增店铺默认关闭接单 |
| 手机打不开页面 | 默认只监听本机 `127.0.0.1`，请先用部署电脑的浏览器体验 |
| 启动失败或页面无法访问 | 在项目根目录查看 `docker compose ps` 和下方日志，定位未就绪的服务 |

以下命令均在项目根目录执行：

```powershell
# 查看最近的日志
docker compose logs --tail=100 server web

# 暂停服务，保留数据
docker compose stop

# 再次启动已有服务
docker compose up -d --wait --wait-timeout 300
```

数据库、Redis 和上传文件使用 Docker 持久卷保存。不要使用 `docker compose down -v` 停止日常环境，该命令会删除数据卷。

## 项目结构与阅读入口

```text
CampusEats/
├─ README.md              首次部署与第一笔订单
├─ pom.xml                Maven 多模块入口
├─ sky-common/            公共业务异常、摘要工具
├─ sky-pojo/              请求与响应契约
├─ sky-server/            Spring Boot 后端、数据库迁移与测试
├─ frontend/
│  ├─ admin/              平台、商家、配送工作台（Vue 3）
│  ├─ client/             用户端（uni-app，H5／微信小程序）
│  └─ contracts/          共享类型与 OpenAPI 契约
├─ compose.yaml          本地四服务编排
├─ Dockerfile            后端源码镜像
├─ deploy/               Nginx、Web 镜像与 Maven 构建配置
├─ scripts/              启动、构建、验证和备份工具
├─ docs/                 现行指南、验收记录与历史归档
└─ .github/workflows/     GitHub Actions 验证流程
```

首次只需按本文完成部署；阅读源码时按[开发与源码阅读指南](docs/development.md)跟踪一笔订单。`.env` 和 `.local/` 是受 Git 忽略的本机配置、凭据及备份，不属于共享源码。

- [文档导航](docs/README.md)：按体验、开发、维护和查证据选择入口。
- [架构与接口](docs/platform-architecture.md)：权限范围、合单交易与履约规则。
- [日常维护](docs/operations.md)：运维检查、安全备份、资源保护和运行边界。
- [升级与回滚](docs/platform-deployment.md)：已有数据环境的升级与恢复。
- [验收记录索引](docs/verification/README.md)：各轮验证的范围与限制。
