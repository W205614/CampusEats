# 用户身份、隐私与外部通知接入

本文说明手机号、微信身份和外部通知的配置与使用。默认关闭外部身份和通知入口；准备好真实账号后按下文启用并验收。

## 手机验证码登录

使用腾讯云短信官方 Java SDK 的 `SendSms`（2021-01-11）。先在腾讯云控制台开通短信应用，申请短信签名和正文模板，准备有发送权限的子账号凭据及套餐额度。验证码模板参数按顺序为“验证码”和“有效分钟数”，例如 `您的验证码为{1}，{2}分钟内有效`。这是模板参数约定，需让审核通过的模板与它一致。

生产配置文件加入以下内容，密钥通过受限的环境变量或配置文件注入，不能放入前端构建或 Git：

```yaml
campus:
  identity:
    phone-enabled: true
    policy-version: '2026-11-06-v1'
    operator: '实际运营主体名称'
    contact: '实际客服与隐私联系渠道'
    privacy-text: |
      部署前填写运营方正式确认的隐私政策全文。
    terms-text: |
      部署前填写运营方正式确认的用户协议全文。
  notifications:
    sms:
      secret-id: ${TENCENT_SMS_SECRET_ID}
      secret-key: ${TENCENT_SMS_SECRET_KEY}
      app-id: ${TENCENT_SMS_APP_ID}
      sign-name: ${TENCENT_SMS_SIGN_NAME}
      otp-template-id: ${TENCENT_SMS_OTP_TEMPLATE_ID}
      region: ap-guangzhou
```

`GET /api/v1/user/auth/identity-capabilities` 返回手机入口是否已配置以及当前政策版本。默认不启用手机入口，验证码不会返回给浏览器，也不会写入应用日志。生产开启后，用户在登录页输入手机号，勾选政策同意后使用短信验证码登录；首次使用创建新用户和政策同意记录。

验证码用控制 Redis 保存，五分钟过期，同一手机号发送间隔六十秒，首次申请起的二十四小时窗口最多十次；同一来源每小时最多三十次申请，进入限流后因冷却被拒绝的申请也会占用来源额度。已登录用户的 `BIND`、`CLOSE` 还共用按账号计的二十四小时十次额度，更换手机号或来源 IP 也不能绕过；超限返回 `429 / ACCOUNT_RATE_LIMITED`。账号来自服务端验证的会话，客户端不能指定。账号与手机号额度的校验、计数和有效期由同一 Redis 脚本执行，后续申请不会延长首次申请时确定的窗口。`LOGIN` 尚未识别账号，按手机号与 IP 限制。错误验证最多五次，成功后原子消费。三种用途的验证码相互隔离，后两种还绑定当前用户。Redis 故障时拒绝验证。短信 SDK 的底层连接故障重试与重定向也被关闭，请求总时限七秒；发送结果不明不会自动再次发送，用户在冷却期后可主动重新申请。

## 微信身份与公众号付款授权

小程序继续配置 `campus.wechat-appid` / `campus.wechat-secret`。后端通过 `jscode2session` 换取身份，生产每次微信登录都要求提交当前 `policyVersion`，并按用户与版本保存同意记录，已有账号同样提交该参数，openid 由服务端从微信获取。

身份表按 `provider + appid + openid` 唯一保存。小程序和公众号的 openid 属于不同应用，不能互换。支付服务使用 `UserIdentityService.openid(userId, appid)` 获取准确的付款身份；缺少绑定时返回 `WECHAT_IDENTITY_REQUIRED`。

微信内 H5 使用公众号 OAuth 获取公众号付款身份，需要开通相应公众号权限并在微信后台配置网页授权域名、商户号与 AppID 绑定关系。增加：

```yaml
campus:
  identity:
    oauth-appid: ${WECHAT_MP_APPID}
    oauth-secret: ${WECHAT_MP_SECRET}
    oauth-redirect-uri: https://your-domain.example/api/v1/user/auth/wechat/oauth/callback
```

用户先以手机号登录 H5，然后从个人资料绑定公众号身份，或由微信内付款流程发起授权。`POST /api/v1/user/auth/wechat/oauth/start` 返回微信授权地址；回调中的一次性 `state` 在控制 Redis 保存五分钟，并绑定发起用户及原始登录会话。回调再次检查会话与账号状态后绑定身份，固定跳回订单页。state 已使用、超时或原会话已撤销都会明确失败。

手机号已绑定其他账号、微信身份已属于其他账号，或当前账号已绑定同一应用的另一个微信身份时返回冲突，不自动搬迁订单、售后或资金记录。需要先登录原账号再处理绑定。更换手机号目前交由平台人工核验处理，不能以新手机号验证码绕过原账号所有权。

数据库唯一约束决定并发绑定的最终归属；另一账号同时提交同一手机号或微信身份时，败者明确收到 `PHONE_ALREADY_BOUND` / `WECHAT_ALREADY_BOUND`，原身份与个人资料保持不变。昵称修改、身份绑定、政策确认、订阅授权和注销在写入前按用户行、会话行顺序加锁并重新验证会话；已经退出、到期或因密码重置而失效的会话不能在网络或数据库等待后继续完成这些修改。

旧版 `user.openid` 没有 AppID 来源记录，不会默认猜测归属。只有确认旧环境使用的原始小程序 AppID 后，配置 `campus.identity.legacy-wechat-appid` 为该 AppID，登录时才将相符旧身份迁移到新映射；不要把新应用 AppID 填成旧身份来源。

## 隐私与注销

`GET /api/v1/user/auth/privacy-policy` 提供版本、运营主体、联系方式及政策正文，用户可从登录和个人资料页面阅读。默认文字面向本机演示，生产必须填写实际运营方确认的正文和联系信息。政策内容是运营配置，不需要修改前端源码。

用户可在个人资料更新昵称、绑定手机号、查看身份与通知。注销要求验证当前绑定手机号；未完成订单、活动售后和未完成退款会阻止注销。通过检查后撤销全部用户会话、停用账号、清理当前个人资料及身份绑定、匿名化当前地址簿。订单历史、订单地址快照、售后举证与交易审计按运营方保留政策保存；运营方配置托管数据库和对象存储的实际保留策略。注销账号不能通过本地账号管理重新启用或重置密码。

## 微信订阅消息

在小程序后台选择符合实际业务类目的订阅模板，记录模板 ID 及字段名。可订阅事件为 `ORDER_PAID`、`ORDER_READY`、`ORDER_PICKUP`、`ORDER_COMPLETE`、`ORDER_COMPLETED`、`ORDER_CANCELLED`、`ORDER_EXCEPTION_CANCELLED`、`ORDER_REFUNDED`，没有模板配置的事件不会产生外部投递任务。

例如“餐品已备好”模板包含 `thing1` 和 `character_string2`：

```yaml
campus:
  notifications:
    enabled: true
    wechat:
      templates:
        ORDER_READY: '${WECHAT_READY_TEMPLATE_ID}'
      fields:
        ORDER_READY: '{"thing1":"summary","character_string2":"orderNumber"}'
```

映射值只允许 `summary`、`orderNumber`、`time`，字段名必须与实际审核模板一致。若模板对长度或内容格式有额外要求，应选择匹配业务摘要的模板，供应方拒绝时会记录 `WECHAT_MESSAGE_REJECTED`，可在投递记录查看。

用户在小程序“个人资料与通知”中主动触发 `requestSubscribeMessage`，每次最多三个不同模板，服务端记录本次选择。发送时消费一条本地授权记录，微信仍独立验证真实下发权限。用户拒绝或额度不足会跳过，不将订阅消息作为订单成功的前提。H5 不调用小程序订阅授权，继续使用站内消息。

微信 token 使用 `stable_token` 接口获取，缓存于控制 Redis；多个实例通过短锁协调更新。生产发送 `formal` 小程序消息，非生产发送 `trial` 消息。公众号 OAuth token 和小程序 access_token 不混用。

仅在共享 token 刷新锁被其他实例占用、且本次尚未发送时，投递任务保留授权额度并延后十秒重新领取；最多领取三次，仍争抢失败则记录 `FAILED / WECHAT_TOKEN_BUSY`，可在投递列表查询。取得 token 和构造字段后才消费授权。已开始发送后的超时、异常响应或进程中断仍记录 `UNKNOWN`，不会使用这一延期规则重新发送。

## 业务短信与发送状态

在验证码配置之外，可以为少量必要业务事件添加单独模板，例如：

```yaml
campus:
  notifications:
    enabled: true
    sms:
      templates:
        ORDER_READY: '${TENCENT_SMS_READY_TEMPLATE_ID}'
      fields:
        ORDER_READY: '{"01":"orderNumber","02":"summary"}'
```

短信参数按映射键的字典顺序排列，使用补零的 `01`、`02` 保持顺序；实际模板必须按相同顺序审核。仅向当前已验证且未注销的手机号发送，不从历史订单地址提取号码。

业务事件与通知任务在同一数据库事务创建，按事件、用户、渠道去重；网络调用由后台任务在事务外执行。状态含义如下：

| 状态                   | 含义                                           |
| ---------------------- | ---------------------------------------------- |
| REQUESTED / PROCESSING | 待发送 / 本次发送正在执行                      |
| ACCEPTED               | 渠道已受理；用户阅读状态由渠道回执能力决定     |
| SUCCEEDED              | 短信运营商回执确认送达                         |
| SKIPPED                | 无授权、无验证手机号或账号已注销               |
| FAILED                 | 明确未受理或回执确认失败                       |
| UNKNOWN                | 请求结果或进程中断结果无法确认，不自动再次发送 |

短信通过 `PullSmsSendStatusByPhoneNumber` 查询回执；需确认腾讯云账号已开通相应状态查询能力。受理后约一分钟进入首轮查询，尚未取得回执时后续间隔五分钟；具体查询时间受任务积压和单轮预算影响。超过七天仍没有可用回执转为未知。微信订阅消息没有用户已读回执，成功发送后保留 `ACCEPTED`。后台发送进程崩溃后的过期租约转为 `UNKNOWN`，不会重新领取并造成重复通知。

模板 ID、事件及字段映射在创建投递任务时保存快照，后续修改配置只影响新任务。短信接入状态和人工验收门槛使用业务投递记录；验证码发送不产生该记录。若需要登记短信真实验收，除验证登录短信外，还须启用一个已审核业务模板并取得对应的送达回执。

用户在个人资料页查看最近通知；平台可使用 `GET /api/v1/platform/notifications/deliveries` 分页查看状态。未提供任意号码发送接口，也不提供对未知消息的一键重发；需要先在渠道核实并按真实授权重新处理。站内消息和订单查询始终是业务状态依据。

## 验证与接入验收

本地 `IdentityNotificationIntegrationTest` 使用真实 MySQL、Redis 和 HTTP 测试服务，驱动官方短信 SDK 签名发送，以及微信登录、OAuth 和订阅发送实现。覆盖验证码单次使用、用途隔离、错误次数、并发归属冲突、AppID 隔离、state 重放、网络等待期间撤销会话、注销约束、共享 token 锁争抢及通知防重。

拿到真实账号后，依次验证短信到达、重复申请冷却、原账号绑定冲突、小程序登录、公众号授权、真实订阅授权与发送、短信回执、停用/注销后的访问权限。只有保存真实环境验收结果后，才能将对应能力从“配置完整/本地契约验证”更新为“真实业务验收通过”。

官方参考：[短信 Java SDK](https://cloud.tencent.com/document/product/382/43194)、[短信状态查询](https://cloud.tencent.com/document/product/382/55976)、[腾讯云 SDK 仓库](https://github.com/TencentCloud/tencentcloud-sdk-java)。
