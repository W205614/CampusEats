# 前端依赖约束

两端分别维护 package-lock.json，构建使用 npm ci。管理端使用 Vue 3 / Vite 8；用户端锁定官方 uni-app Vue 3 CLI。生产仅部署静态文件，不部署 Node 开发服务器、SSR 或 uniCloud 服务。

兼容升级通过 overrides 固定 Vite 7.3.7、Rollup 4.64.0、PostCSS、ws、jpeg-js、unimport、adm-zip、Express 和 intlify 依赖。Windows/Linux 的 Rollup native optional packages 显式保留，以支持跨平台源码构建。

DCloud H5 使用自带 Vue fork，编译器固定 3.4.21；Vue 3.5 的 compiler 与该 fork 混用曾在真实导航中报只读 slot 属性错误。不能仅以 npm install 成功判定兼容；必须经过两端构建和浏览器业务验收。

用户端审计余项的风险接受只针对 [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) 及指定的传递依赖链。该公告影响 braces 的深层嵌套模式解析，当前无修复版本。不接收不可信 glob，不暴露开发服务器，只构建可信本仓库。例外保存在 frontend/client/audit-exceptions.json；脚本拒绝新高危、任何 critical 或过期例外。低/中危告警仍在原报告中，不能把例外检查通过描述成零漏洞。

重新检查：

```powershell
npm audit --prefix frontend/admin --registry=https://registry.npmjs.org
npm audit --prefix frontend/client --registry=https://registry.npmjs.org --json > .local/audit-client.json
node scripts/check-client-audit.mjs
```

升级框架后同时重跑 H5、小程序、类型检查、HTTP 数据契约及浏览器业务流程，再决定是否删除 overrides 和例外。
