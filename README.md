# CampusEats · 校园多校区点餐平台

CampusEats 让学生在多个校区、多个店铺选餐后合并付款，各店分别备餐，支持配送和到店自取。员工工作台按角色处理店铺营业、订单履约、售后和经营报表。

项目面向本机演示，默认监听 `127.0.0.1`。付款与退款均为模拟操作，演示账号由体验者共享，请使用虚构的个人信息。后端使用 Java 21、Spring Boot 4.1、MySQL 8.4 和 Redis 7.4，员工工作台使用 Vue 3，用户端使用 uni-app。下列步骤体验浏览器 H5 版，微信小程序登录需另行配置，真机验收仍待完成。

## 首次启动

准备 Git、PowerShell 7（命令为 `pwsh`）以及已启动的 Docker Desktop，选择 Linux 容器模式。当前构建面向 `linux/amd64`。首次构建需能下载基础镜像、Maven 与 npm 依赖以及 Oracle 安全补丁。Java 和前端应用均在 Docker 中构建。

在 PowerShell 7 中执行：

```powershell
git clone https://github.com/W205614/CampusEats.git
cd CampusEats
pwsh -NoProfile -File ./scripts/start.ps1 -SkipVerify
docker compose ps
```

看到 `mysql`、`redis`、`server`、`web` 四个服务均为 `healthy` 即可登录。启动脚本生成本机 `.env` 并初始化演示数据，`-SkipVerify` 跳过本机 Maven 验证。已有容器的日常启停见[日常维护](docs/operations.md#日常启停与排障)，已有数据库或 CI 制品环境请先阅读[升级与回滚](docs/platform-deployment.md)。

## 登录并完成第一笔订单

在仓库根目录运行 `Get-Content .local/demo-credentials.txt`，查看脚本生成的初始密码，凭据仅供本机使用。以下步骤使用全新数据库的默认演示模式。

1. 打开[员工工作台](http://localhost:18083/admin/)，使用平台账号 `admin` 登录。在「校区与店铺」找到「默认店铺」，进入「营业配置」，确认当前处于营业时段，开启「接收新订单」并保存。新店默认关闭接单。
2. 打开[用户点餐端](http://localhost:18083/)，点击「登录点餐」并选择一个演示用户。在「默认校区 → 默认店铺」加购餐品，进入购物车，点击「选择此店」和「合并结算」。
3. 选择「到店自取」，点击「计算报价」，提交订单，再到订单页点击「整批模拟支付」。如体验配送，先添加该店所在校区的地址。
4. 回到工作台，退出 `admin`，使用默认店铺的店长 `manager1` 登录并按提示改密。在「订单履约」依次完成「接单备餐」「备餐完成」，用用户订单页显示的自取码核销。此时可在订单页看到已完成的订单。

需要独立体验账号时，可由平台管理员在「用户账号」中创建，用户在点餐端选择「独立账号登录」。其他角色和试用模式见[演示账号说明](docs/operations.md#演示账号)。

## 目录与源码入口

| 想做什么 | 从哪里开始 |
|---|---|
| 改管理台页面 | [员工工作台页面](frontend/admin/src/pages/)按业务命名，[路由](frontend/admin/src/router.ts)定义访问入口 |
| 改用户点餐流程 | [用户端页面](frontend/client/src/pages/)包含选店、购物车、结算和订单，[页面配置](frontend/client/src/pages.json)列出导航顺序 |
| 查订单如何报价、支付和履约 | `sky-server/` 是后端主模块，从[HTTP 入口](sky-server/src/main/java/com/sky/web/)沿服务方法追踪，见[四条业务调用链](docs/development.md#按业务追踪请求) |
| 找数据库变更 | [Flyway 迁移](sky-server/src/main/resources/db/migration/)按版本保存数据库演进记录 |
| 查接口契约 | [共享契约](frontend/contracts/)包含 OpenAPI 快照、生成类型和前端共享模型；[Java 接口类型](sky-pojo/src/main/java/com/sky/api/)包含请求与响应定义 |
| 找构建与部署入口 | [根 POM](pom.xml)聚合后端服务、接口类型和[共享异常与哈希工具](sky-common/src/main/java/com/sky/common/)；[Compose](compose.yaml)编排四个服务，[部署配置](deploy/README.md)和[脚本导航](scripts/README.md)列出构建与运维入口 |
| 查提交会运行哪些验证 | [GitHub Actions 工作流](.github/workflows/)定义 CI 的构建、测试、安全扫描与制品导出 |

## 界面预览

**用户点餐 · 选店、浏览餐品与加购**

<img src="docs/images/platform-h5-menu.png" alt="用户点餐端的选店、餐品列表和购物车入口" width="390">

**员工工作台 · 校区与店铺管理**

![平台工作台](docs/images/platform-dashboard.png)

**员工登录入口**

![员工登录页](docs/images/platform-login.png)

## 专题文档

| 主题 | 阅读入口 |
|---|---|
| 架构、权限和交易规则 | [多校区平台架构](docs/platform-architecture.md) |
| 本地开发、前端联调和微信小程序 | [开发与源码阅读](docs/development.md) |
| 日常启停、排障和维护 | [日常维护](docs/operations.md) |
| 维护已有环境、升级和回滚 | [部署与恢复](docs/platform-deployment.md) |
| 源码、CI、容量测量与部署版本 | [当前版本验证记录](docs/verification/current.md) |
| 其他指南 | [文档目录](docs/README.md) |
