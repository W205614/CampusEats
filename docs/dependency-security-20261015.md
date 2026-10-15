# 用户端构建依赖复核 · 2026-10-15

## 结论

原有7个高风险包都追溯到同一公告：`braces` 的 [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm)。复核时npm最新版本仍为3.0.3，官方公告明确没有修复版本。新版uni-app稳定版及测试版仍使用相同依赖链，因此本轮未通过升级消除该根因，也未将其标记为已修复。

完成四项兼容覆盖升级，移除另外6条安全公告。审计中的受影响包从26个变为21个；高危包条目从7个变为17个，均仍由同一braces公告传播而来，没有新增高危公告。不能将这些包条目视为17个独立漏洞，也不能将本轮描述为高危清零。

## 已交付的升级

| 依赖 | 原安装版本 | 新版本 | 已移除公告 |
|---|---|---|---|
| `@babel/core` | 7.25.2 | 7.29.7 | [sourceMappingURL任意文件读取](https://github.com/advisories/GHSA-4x5r-pxfx-6jf8) |
| `@intlify/shared` | 9.1.9、9.14.1、9.14.5 | 统一9.14.5 | [原型污染](https://github.com/advisories/GHSA-hjwq-mjwj-4x6c) |
| `esbuild` | 0.20.2、0.27.7 | 统一0.28.2 | [开发服务器跨站读取](https://github.com/advisories/GHSA-67mh-4wv8-2f99)、[Windows文件读取](https://github.com/advisories/GHSA-g7r4-m6w7-qqqr) |
| `postcss-selector-parser` | 6.1.2及已有7.1.6 | 统一7.1.6 | [递归拒绝服务](https://github.com/advisories/GHSA-w9m9-85wc-3x92)、[平方复杂度拒绝服务](https://github.com/advisories/GHSA-rj75-hqrm-r3gf) |

版本通过客户端package.json的精确overrides及lockfile固定。uni-app各组件保持原有同一版本组，没有混用Vue 2／Vue 3工具链。

## 未采用的升级路径

1. `braces`最新3.0.3仍受影响。`micromatch`最新4.0.8依赖braces，`fast-glob`最新3.3.3依赖micromatch；仅升级这些包不能移除漏洞。
2. 已核对`@dcloudio/uni-cli-shared`稳定版`3.0.0-5020620260917001`和测试版`3.0.0-alpha-5030120260930001`的npm元数据，两者仍固定chokidar 3.6.0、fast-glob 3.3.3。相关插件和uni-cloud也保留fast-glob。来源：[稳定版元数据](https://registry.npmjs.org/@dcloudio%2funi-cli-shared/3.0.0-5020620260917001)、[测试版元数据](https://registry.npmjs.org/@dcloudio%2funi-cli-shared/3.0.0-alpha-5030120260930001)。
3. 新版chokidar移除了glob支持，与当前uni-app所用版本并非可直接替换关系。没有强制覆盖、引入非官方braces替代包或修改node_modules来隐藏审计结果。
4. 另一个剩余根因为[phin重定向泄露敏感请求头](https://github.com/advisories/GHSA-x565-32qp-m3vf)。旧Jimp使用phin 2的回调接口，不能直接换为phin 3的Promise接口。试升Jimp 0.22.12会引入受[另一条公告](https://github.com/advisories/GHSA-5v7r-6r5c-r473)影响的file-type 16.x，其修复版21.3.1不符合该Jimp的依赖范围。因此已撤回该试验，保持Jimp 0.10.3。此路径服务小程序自动化工具，不属于真实资金业务。

## 审计口径与约束

| npm报告 | 低 | 中 | 高 | 严重 | 包条目合计 | 独立公告 |
|---|---:|---:|---:|---:|---:|---:|
| 原lockfile，复核前及隔离重查 | 10 | 9 | 7 | 0 | 26 | 8 |
| 升级后lockfile | 0 | 4 | 17 | 0 | 21 | 2 |

高危条目分级重新传播至原来已存在的DCloud包；这些DCloud包版本保持不变。升级后17条高危的所有依赖路径均指向braces的同一公告，4条中危均指向phin。保留原始及升级后报告、依赖树和公告差集供核对。

高危例外仅补充本次明确核对的传递包名，允许的高危公告仍只有GHSA-vfj7-8cjw-p6xm；原到期日2026-11-07不延长。CI仍拒绝未知高危包、允许包上的新高危公告、严重漏洞、过期例外及缺失的审计结果。新增6项回归验证这些约束。

构建在受信本机和CI执行，日常四容器中的Nginx仅提供静态前端，不部署Node开发服务器。该边界降低暴露面，不等于漏洞已修复；不应把不可信glob模式送入构建工具或公开开发服务器。

## 验证与证据

- 客户端npm ci干净安装、两端类型检查、管理端、H5及微信小程序生产构建通过；Linux Web镜像从新lockfile构建通过。
- 隔离28083环境17条浏览器用例全部通过，无重试；接口冒烟与WebSocket验证通过。日常18083只执行只读冒烟，写入式验收数据不进入日常库。
- 审计规则6项测试通过，并加入CI。最终GitHub CI仍执行后端84项、两端构建、契约、审计、浏览器、WebSocket和独立备份恢复；结果以交付提交对应的工作流为准。
- 小程序构建不等同于微信真机验证。

本机证据在`.local/security-upgrade/`：原始package与lockfile、audit-before.json、audit-baseline-recheck.json、audit-comparison.json、各构建和浏览器日志、审计规则测试日志，以及升级前镜像归档和SHA-256。当前原始npm审计报告为`.local/audit-client.json`，配置与口令不提交Git。
