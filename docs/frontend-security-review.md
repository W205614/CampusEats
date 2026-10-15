# 前端依赖约束

两端分别维护 package-lock.json，构建使用 npm ci。管理端使用 Vue 3 / Vite 8；用户端锁定官方 uni-app Vue 3 CLI。生产仅部署静态文件，不部署 Node 开发服务器、SSR 或 uniCloud 服务。

兼容升级通过 overrides 固定 Vite 7.3.7、Rollup 4.64.0、PostCSS、ws、jpeg-js、unimport、adm-zip、Express 和 intlify 依赖。Windows/Linux 的 Rollup native optional packages 显式保留，以支持跨平台源码构建。

DCloud H5 使用自带 Vue fork，编译器固定 3.4.21；Vue 3.5 的 compiler 与该 fork 混用曾在真实导航中报只读 slot 属性错误。不能仅以 npm install 成功判定兼容；必须经过两端构建和浏览器业务验收。

2026-10-15 已通过本仓库 glob／watch 适配层、chokidar 4 和 Jimp 1 移除 braces 与旧 phin 的受影响路径，两端当前 npm 审计均为0项已知漏洞。旧 `audit-exceptions.json` 已删除，审计规则拒绝任意级别的新发现或无效报告。兼容补丁对锁定的两个上游文件检查版本与修改前后哈希，未知内容必须重新审查；详见[替换方案与验证证据](dependency-security-20261015.md)。零已知漏洞不是全面安全认证。

重新检查：

```powershell
npm audit --prefix frontend/admin --registry=https://registry.npmjs.org
npm audit --prefix frontend/client --registry=https://registry.npmjs.org --json > .local/audit-client.json
node scripts/check-client-audit.mjs
npm run test:toolchain --prefix frontend/client
```

升级框架后重新审查调用点与受控补丁，同时重跑 H5、小程序、类型检查、HTTP 数据契约及浏览器业务流程，再决定是否删除 overrides 和兼容桥。
