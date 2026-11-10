# 安全与质量审阅边界

2026-11-10 为项目补充 Java、JavaScript/TypeScript、Python 和 GitHub Actions 的 CodeQL `security-and-quality` 分析，并启用 Dependabot 告警。首轮未过滤结果为 92 条；对应原始结果和后续复扫不能因审阅关闭而删除或改写。当前状态以 [Security and quality](https://github.com/W205614/CampusEats/security) 和目标提交的 [分析工作流](../.github/workflows/security.yml) 为准，不能只凭这份说明认定当前告警为零。

工作流保存所有严重级别的原始 SARIF；全部语言分析处理完成后，检查当前 ref 的活动未解决告警。实际修复、经维护者明确认可的审阅处置、仍待解决的发现分别记录。活动数为零不代表扫描器从未发现问题，更不代表绝对安全或生产验收。

## 已认可的兼容、协议及上下文

下列九条由维护者于 2026-11-10 明确认可处置；没有以删除功能、改算法名称、排除源码或修改原始报告消警。未来行为或边界变化时须重新审阅，不能把旧结论用于新的数据流。

| 原告警 | 审阅依据与适用边界 | 回归或来源 |
| --- | --- | --- |
| [#20](https://github.com/W205614/CampusEats/security/code-scanning/20) · MD5 | 仅用于既有员工旧密码首次验证后迁移至 Argon2；保留已有账号升级契约，属于认可的兼容约束，不是新的密码存储方案或算法修复 | `CommerceIntegrationTest.legacyMd5LoginMigratesAndForcesPasswordChange` |
| [#21](https://github.com/W205614/CampusEats/security/code-scanning/21) · SHA-1 | 微信账单下载的 `hash_type` 协议约束；未擅自改成服务商不支持的校验算法 | [微信账单文档](https://pay.wechatpay.cn/doc/v3/partner/4013462614) |
| [#16](https://github.com/W205614/CampusEats/security/code-scanning/16) · CSRF | 业务 API 为无状态、显式 Bearer 认证，不使用浏览器自动携带的 Cookie/Basic 授权；不适用于以后新增 Cookie 会话的入口 | `BearerAuthenticationBoundaryTest` 验证无凭据、Cookie/Basic 不能授权写请求，以及恶意 Origin 预检被拒绝 |
| [#23](https://github.com/W205614/CampusEats/security/code-scanning/23) · 认证分支 | 未提供 Bearer 时仍经过 Spring 授权链，不因省略认证头而放行受保护资源 | 同上，真实 HTTP 认证边界回归 |
| [#22](https://github.com/W205614/CampusEats/security/code-scanning/22) · 财务分支 | 平台与商家两分支分别核对平台角色或匹配店铺授权，不能通过选择参数跳过权限 | `FinanceAuthorizationBoundaryTest` |
| [#14](https://github.com/W205614/CampusEats/security/code-scanning/14) · 图片流 | 光栅上传先解码后重编码为 PNG，实际响应为 `image/png` 并带 `nosniff`；不是 HTML 响应上下文 | `ImageResponseBoundaryTest` 验证附加 HTML 被清除及真实响应头 |
| [#3](https://github.com/W205614/CampusEats/security/code-scanning/3) · 网络写文件 | 显式 `--fetch` 将接口文档保存到固定契约快照；输出路径不由网络内容选择，CI 比较实际契约 | [契约生成器](../scripts/generate-contracts.mjs)、[CI](../.github/workflows/verify.yml) |
| [#4](https://github.com/W205614/CampusEats/security/code-scanning/4) · 测试日志 | 回环 HTTP 下载夹具将请求路线记录到父测试指定文件，供重试上限和 404 行为断言使用 | [下载回归](../scripts/tests/test-tool-download.ps1) |
| [#2](https://github.com/W205614/CampusEats/security/code-scanning/2) · 文件到网络 | 隔离性能工具按明确用途读取合成会话；发送前检查 `.local`、元数据、项目、时效、身份、真实健康容器与非日常回环端口 | [性能工具](../scripts/measure-performance.mjs)、[日常维护](operations.md) |

两类 JavaScript 数据流规则属于需要审阅用途的发现，参见 CodeQL 的[网络写文件](https://codeql.github.com/codeql-query-help/javascript/js-http-to-file-access/)和[文件数据发网络](https://codeql.github.com/codeql-query-help/javascript/js-file-access-to-http/)说明。确认用途并不等于不存在数据流，也不能报告为代码已修复。

历史微信凭据告警已依据维护者的撤销／轮换确认处置；当前源码无原值。此处记录的是维护者确认，不宣称本次已向微信独立验证凭据状态，不展示历史值或重写 Git 历史。
