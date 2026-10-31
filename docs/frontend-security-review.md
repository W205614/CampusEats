# 前端依赖约束

两端分别维护 package-lock.json，构建使用 npm ci。管理端使用 Vue 3 / Vite 8；用户端锁定官方 uni-app Vue 3 CLI。生产仅部署静态文件，不部署 Node 开发服务器、SSR 或 uniCloud 服务。

兼容升级通过 overrides 固定 Vite 7.3.7、Rollup 4.64.0、PostCSS、ws、jpeg-js、unimport、adm-zip、Express 和 intlify 依赖。Windows/Linux 的 Rollup native optional packages 显式保留，以支持跨平台源码构建。

DCloud H5 使用自带 Vue fork，编译器固定 3.4.21；Vue 3.5 的 compiler 与该 fork 混用曾在真实导航中报只读 slot 属性错误。不能仅以 npm install 成功判定兼容；必须经过两端构建和浏览器业务验收。

本仓库通过 glob／watch 适配层、chokidar 4 和 Jimp 1 移除 braces 与旧 phin 的受影响路径，不使用历史漏洞例外清单。审计规则拒绝任意级别的新发现或无效报告；兼容补丁对锁定的上游文件检查版本与修改前后哈希，未知内容必须重新审查。当前审计结论以对应提交的 GitHub Actions 和扫描产物为准，不沿用旧日期的零漏洞结论；验证边界见[当前版本记录](verification/current.md)。

2026-10-31 的发布检查发现管理端契约生成工具间接引入的 minimatch 5.1.9 受 [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249) 影响。管理端将 Redocly 依赖固定为已审查的 1.34.20，并将其 minimatch 升至修复版 11.0.1；复用用户端的受控补丁机制，只把 Redocly 的 CommonJS 导入改为命名导出。postinstall 在安装时校验并适配，不直接维护 node_modules；真实 HTTP 文档读取测试覆盖带协议和不带协议的 URL 匹配、请求头选择，以及重复安装不改写文件。该依赖用于构建工具，发布的静态站点不运行它。

重新检查：

```powershell
npm audit --prefix frontend/admin --registry=https://registry.npmjs.org
npm audit --prefix frontend/client --registry=https://registry.npmjs.org --json > .local/audit-client.json
node scripts/check-client-audit.mjs
npm run test:toolchain --prefix frontend/admin
npm run test:toolchain --prefix frontend/client
```

升级框架后重新审查调用点与受控补丁，同时重跑 H5、小程序、类型检查、HTTP 数据契约及浏览器业务流程，再决定是否删除 overrides 和兼容桥。
