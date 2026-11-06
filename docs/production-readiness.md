# 生产接入总览

默认演示使用本地身份、模拟资金与本地文件，无需云账号。启用微信与腾讯云服务时，按以下顺序配置并验收。

## 接入顺序

1. 准备独立生产数据库及运行、迁移账户，暂不启动应用。禁止把共享演示数据库直接当作生产库；历史模拟交易保留 `MOCK` 标记。生产初始化仅为没有员工的新库创建管理员并强制改密，不覆盖旧账号。
2. 在微信完成服务商、子商户进件，以及合单和各付款场景授权；绑定小程序及公众号 AppID。通过平台工作台“渠道收款与对账”配置每家店的子商户。缺少收款配置的店铺不能创建真实结算。
3. 准备 COS 两个私有桶并开启版本控制；准备腾讯云短信应用、签名、验证码和通知模板；配置小程序订阅模板及合法域名。
4. 复制 `deploy/production/env.example` 与 `deploy/production/integrations.example.yml` 到受保护的部署目录，填写实际参数。私钥、公钥和 MySQL 信任库通过只读目录挂载；不要放进 Git、前端包或工单。
5. 配置齐全后执行检查，在两台应用均未开放流量的窗口，由一台候选镜像运行一次随附的 Flyway 迁移（本轮迁移为 V20—V24），关闭任务且不启动 Web；迁移启动同时执行生产初始化和配置检查，需要前述密钥与云参数。再按部署流程启动业务并完成平台账号、商家、学生、配送员各角色验收。开启外部通知前先验证模板字段与用户授权。

部署命令、双 Redis 隔离、CLB 代理、监控与恢复见[生产部署](production-deployment.md)。身份、通知及政策全部配置见[身份与通知](identity-notifications.md)；桶权限、元数据及上传恢复见[存储](storage-integration.md)。

## 微信支付参数

| 环境变量                                                | 用途                                                   |
| ------------------------------------------------------- | ------------------------------------------------------ |
| `WECHATPAY_MCH_ID`                                      | 发起合单的服务商商户号                                 |
| `WECHATPAY_SERIAL`                                      | 商户 API 证书序列号                                    |
| `WECHATPAY_PRIVATE_KEY_PATH`                            | 容器内商户私钥 PEM 路径                                |
| `WECHATPAY_PUBLIC_KEY_PATH` / `WECHATPAY_PUBLIC_KEY_ID` | 微信支付公钥 PEM 路径与对应公钥 ID                     |
| `WECHATPAY_API_V3_KEY`                                  | 32 字节 APIv3 密钥，用于解密通知                       |
| `WECHAT_APPID` / `WECHAT_SECRET`                        | 小程序应用身份与登录密钥                               |
| `WECHAT_MP_APPID` / `WECHAT_MP_SECRET`                  | 微信内 H5 付款及 OAuth 对应的公众号                    |
| `PUBLIC_BASE_URL`                                       | 不带结尾斜杠的本站 HTTPS 源，生成支付及 OAuth 回调地址 |

商户私钥和微信支付公钥职责不同，不能互换；更换微信支付公钥时同步修改公钥 ID、文件和部署配置。使用官方 SDK 完成签名、验签和解密，不关闭响应验签。

服务商及参与子商户均需获得相应产品权限，详见[官方接入准备](https://pay.wechatpay.cn/doc/v3/partner/4013461849)。开户与产品授权由微信审核。

## 支付流程与接口

| 接口                                                  | 行为                                                                        |
| ----------------------------------------------------- | --------------------------------------------------------------------------- |
| `GET /api/v1/user/payment-capabilities`               | 查询当前提供模拟还是微信渠道                                                |
| `POST /api/v1/user/checkouts/{id}/payment-intents`    | 携带 `Idempotency-Key` 与 `channel/version`，保存唯一支付意图并获取调起动作 |
| `GET /api/v1/user/checkouts/{id}/payment`             | 查询持久化支付状态、过期时间和调起动作                                      |
| `POST /api/v1/user/checkouts/{id}/cancel`             | 请求取消；存在渠道请求时先关单，返回当前结算状态                            |
| `POST /api/v1/payments/wechat/notify`                 | 微信支付通知；验签、解密、核对主体、单号、币种和各子单金额后落账            |
| `POST /api/v1/payments/wechat/refund-notify`          | 服务商退款通知；核对 `sp_mchid`、子商户及退款金额，成功后由任务确认业务退款 |
| `GET /api/v1/platform/payment-exceptions`             | 平台查询结果未知、关单中及人工核对状态                                      |
| `POST /api/v1/platform/payment-exceptions/{id}/retry` | 平台对人工核对状态重新查单，不重新生成资金请求编号                          |

`channel` 支持 `MINI_PROGRAM/JSAPI/H5/NATIVE`。前端动作分别为 `requestPayment/wechatBridge/redirect/qrCode`。支付成功以服务端验签回调或主动查单结果为准。

同一结算单最多一个支付意图，重试必须使用原编号与内容。在其他设备打开时继续查询原支付；如需更换支付场景，先取消并确认原结算已关闭，再重新结算，不并行创建可付款渠道订单。

支付异常先查原单；确认未付款后关单，收到关闭确认才释放库存和优惠。网络超时保留未知状态与预占。后台每次处理受预算约束，渠道异常最多自动检查5次后进入 `REVIEW`；已转入退款、渠道失败或未识别的交易状态也走该有界核对流程，不当作正常待支付清零次数，且不据此释放预占。平台核对后仍沿用原单查单。零元结算在本地确认，不向微信发送零金额子单。[渠道状态定义](https://pay.wechatpay.cn/doc/v3/partner/4012761049)

退款由现有售后审批产生，每笔使用稳定的退款编号。申请受理只表示 `PROCESSING`，只有回调或查询确认成功才更新已退款金额与账务；查询明确 `RESOURCE_NOT_EXISTS` 时才以原单原参数重发。银行退款异常进入人工处理任务。[官方退款处理规则](https://pay.wechatpay.cn/doc/v3/merchant/4014959631)

## 商家与渠道账单

- `GET /api/v1/merchant/stores/{store}/payment-config` 查询本店配置；平台通过 `PUT /api/v1/platform/stores/{store}/payment-config` 保存子商户号、启用状态和版本。
- 平台创建跨店优惠后，在尚未领取前通过 `PUT /api/v1/platform/coupons/{id}/participants` 设置参与店铺。优惠按现有确定性算法分摊，由参与店铺承担，不产生平台补贴打款。
- `POST /api/v1/merchant/stores/{store}/channel-reconciliations` 携带 `businessDate`，下载已结束日期的账单，验证渠道摘要及商户归属后核对。历史交易使用保存的原子商户号，同日更换子商户也分别取账单。
- 支付按渠道成功日期，退款按渠道创建/受理日期核对。退款已出账不表示最终银行到账，业务退款成功仍按查询或通知处理。[官方账单说明](https://pay.wechatpay.cn/doc/v3/merchant/4013071246)
- 工作台展示手续费及渠道独有、本地独有、金额差异；平台填写人工核对依据，不自动修改历史账务。原始账单仅保存在受限数据库记录中，不通过公开接口返回。

## 小程序生产构建

在安装了仓库 CI 所用 Node.js 版本的构建环境中执行。以下变量必须进入运行 npm 的进程环境；脚本**不会自动读取 `.env.production`**。先安装用户端依赖，再设置实际值并构建：

```powershell
npm ci --prefix frontend/client --include=optional
$env:WECHAT_APPID='<实际小程序AppID>'
$env:PUBLIC_BASE_URL='https://campuseats.example.com'
npm run build:mp-weixin:production --prefix frontend/client
```

该命令编译 HTTPS API 源并更新生成目录中的微信项目 AppID、开启合法域名检查。导入 `frontend/client/dist/build/mp-weixin` 到微信开发者工具，再配置 request/uploadFile/downloadFile 域名、隐私授权和订阅模板。AppID 与域名通过构建参数配置；构建后在真机验证登录、付款、订阅及举证访问。

## 接入状态的含义

- **未配置（`UNCONFIGURED`）：**该提供商的当前启用条件未满足。
- **配置完整（`CONFIGURED`，界面名称）：**该提供商已选择或启用，尚无可统计的渠道记录。生产启动另行检查必需参数的完整性，凭据、模板和桶权限在真实接入时验证。
- **连通验证通过（`CONNECTIVITY_VERIFIED`）：**当前数据库已有对应渠道记录，例如非零微信预支付/成功支付、带渠道编号的业务短信、受理的订阅消息或 `COS READY` 文件。状态按记录产生的环境解读：本地签名 HTTP 记录对应适配器契约验证，真实云记录对应外部连通验证。
- **真实业务验收通过（`BUSINESS_VERIFIED`）：**已有渠道记录，并存在平台管理员为当前 `APP_REVISION` 登记的人工验收报告。报告须来自真实账号、设备和已授权商家的本次业务旅程，记录时间、结果及证据。

当前本地适配器、状态流程和部署验证结果见[验证记录](verification/current.md)。

平台工作台“渠道收款与对账”展示 `GET /api/v1/platform/integrations` 的逐项状态。`provider` 使用 `WECHAT_PAY/TENCENT_SMS/WECHAT_SUBSCRIPTION/COS`。真实生产环境完成验收后，平台管理员通过 `POST /api/v1/platform/integrations/{provider}/acceptance` 提交 `evidenceReference`，登记本版本的人工验收报告位置。该操作要求已有渠道记录；微信还要求四种非零付款场景、成功退款和匹配账单，COS要求商品与举证文件，短信要求业务通知的送达回执。验证码发送不写入业务通知投递表，因此仅验证码登录成功不会满足短信验收门槛。

渠道计数覆盖当前库全部历史，验收时需核对记录对应的版本、凭据与商户。构建时的 `SOURCE_REVISION` 会成为镜像内 `APP_REVISION`，生产应使用与不可变制品对应的唯一提交或源码版本，不长期复用开发日期标签。升级、换凭据或换商户后重新执行实际旅程，并登记本次报告。登记接口限生产环境使用，保存维护者提交的报告位置；报告内容、真机结果与商户资质由操作者核实。
