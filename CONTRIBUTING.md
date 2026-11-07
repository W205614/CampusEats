# 参与开发

先阅读[项目首页](README.md)、[开发与源码阅读](docs/development.md)和[脚本导航](scripts/README.md)，确认改动对应的入口、业务规则与现有验证方式。使用独立分支，尽量让每次改动只解决一个明确问题。

## 开发环境

后端使用 Java 21 和 Maven；前端使用与 [CI](.github/workflows/verify.yml) 一致的 Node.js 24.19.0，并保留 lockfile。PowerShell 脚本使用 PowerShell 7。运行后端集成测试需要可用的 Docker Linux 容器环境，Testcontainers 会创建独立 MySQL、Redis 等测试依赖。

从仓库根目录安装前端依赖：

```powershell
npm ci --prefix frontend/admin
npm ci --prefix frontend/client --include=optional
```

实际凭据放在受保护且被 Git 忽略的本机配置中。不要提交 `.env`、`.local/`、访问令牌、证书私钥、真实用户数据或包含这些内容的日志。新增依赖前先核对官方来源、许可、维护情况和集成成本。

项目自有内容采用 [MIT License](LICENSE)。第三方代码与素材保留原版权及许可证；前端构建自动生成 `THIRD_PARTY_LICENSES.md`，不要手工编辑或在分发时移除，详见[许可说明](docs/licensing.md)。

## 修改与验证

复用现有的权限、事务、响应和错误处理约定。为可确认的缺陷提供最小复现，测试真实实现及其直接调用方；外部服务可以使用签名 HTTP 夹具，但不要在测试中复制业务算法。仅修改文案或其它低影响内容时，选择对应的静态检查即可。

本地先运行直接影响范围的检查，例如：

```powershell
# 按实际改动选择测试类；这些集成测试使用独立 Testcontainers 依赖
mvn -B -ntp -pl campuseats-server -am '-Dtest=StoredImagesIntegrationTest,ImageStorageTest' '-Dsurefire.failIfNoSpecifiedTests=false' test

# 管理端改动
npm run test:toolchain --prefix frontend/admin
npm run build --prefix frontend/admin

# 用户端改动
npm run test:toolchain --prefix frontend/client
npm run typecheck --prefix frontend/client
npm run build:h5 --prefix frontend/client
npm run build:mp-weixin --prefix frontend/client
```

完整自动检查由 [GitHub Actions 工作流](.github/workflows/verify.yml)执行，包括 Maven 验证、前端构建、依赖审计、脚本测试、契约比对、浏览器回归、双实例验证、独立恢复及镜像安全检查。提交后等待对应 CI 完成并检查结果。涉及外部服务时，另行记录真实账号与设备上的验收结果。

## 接口与数据变更

`frontend/contracts/openapi.ts` 由生成器维护，不手工修改。没有接口变更时，从已有快照生成并核对差异：

```powershell
node scripts/generate-contracts.mjs
git diff -- frontend/contracts/openapi.ts
```

接口变更需要同步真实 OpenAPI 快照并重新生成类型。`generate-contracts.mjs --fetch` 会登录指定后端，应先按[开发说明](docs/development.md)和[脚本导航](scripts/README.md)准备隔离环境，再检查快照与生成结果。

数据库使用新增 Flyway 迁移，保留已经执行过的历史迁移，并验证升级后的旧记录与新结构。写入式浏览器测试、容量测试、迁移及恢复使用独立 Compose 项目、端口、凭据和数据卷。发布与备份脚本会暂停写入或更新恢复指针，执行前按[升级与回滚说明](docs/platform-deployment.md)安排维护窗口。

## 提交说明

说明具体触发条件、修改后的行为、验证命令及结果，并列出仍需真实服务验证的部分。截图和证据应脱敏；大型临时报告留在被忽略的本机目录，仓库文档仅记录必要结论与证据位置。涉及安全漏洞时使用 [SECURITY.md](SECURITY.md) 中的私有报告渠道。
