# 日常维护与运行边界

以下命令均在项目根目录的 PowerShell 7 执行；初次部署见[项目首页](../README.md)，脚本用途见[脚本导航](../scripts/README.md)。

## 演示账号

全新演示库创建两个校区，每个校区有两家店。下列新员工的初始口令统一来自 `DEMO_ADMIN_PASSWORD`：源码启动脚本会另写入本机 `.local/demo-credentials.txt`；预构建镜像和宿主源码路线直接使用各自受保护环境配置中的值，不会自动生成该凭据文件。不要将其配置或内容提交。已有员工继续使用各自当前密码，不因修改环境变量重置。

| 角色       | 账号                                             |
| ---------- | ------------------------------------------------ |
| 平台管理员 | `admin`                                          |
| 店长       | `manager1` ～ `manager4`                         |
| 店铺运营员 | `operator1` ～ `operator4`                       |
| 校区配送员 | `courier1`、`courier1b`、`courier2`、`courier2b` |

新建的店长、运营员和配送员首次登录须改密。已有账号继续使用当前密码。需要独立体验时，平台管理员可创建测试用户并重置其密码。将 `APP_MODE` 改为 `trial` 会关闭共享演示登录，空试用库只创建需要首次改密的平台账号。修改运行配置后按[部署说明](platform-deployment.md)重新创建对应容器。

## 日常启停与排障

| 遇到的问题                                     | 处理方式                                                                                                                                                     |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 找不到 `pwsh`，或脚本运行报版本相关错误        | 安装 PowerShell 7，按[首次启动](../README.md#首次启动)运行脚本                                                                                               |
| Docker 连接失败或构建下载失败                  | 确认 Docker Desktop 已启动且处于 Linux 容器模式，并检查镜像源与依赖下载的网络连接                                                                            |
| `18083` 端口被占用                             | 修改 `.env` 的 `WEB_PORT` 和 `ALLOWED_ORIGINS`，按[部署说明](platform-deployment.md)应用配置，使用新端口访问                                                 |
| 店铺休息中，无法下单                           | 在工作台检查对应店铺的接单开关、营业时段和商品配额；新增店铺默认关闭接单                                                                                     |
| 手机打不开页面                                 | 默认只监听本机 `127.0.0.1`，请先用部署电脑的浏览器体验                                                                                                       |
| 启动失败或页面无法访问                         | 在项目根目录查看 `docker compose ps -a` 和下方日志；按需停止的容器需先启动                                                                                   |
| `start.ps1` 提示当前环境已采用 CI 制品         | 日常启停使用下方命令；升级使用 `download-release.ps1`、`release.ps1`，按[CI 制品发布说明](platform-deployment.md#已采用-ci-制品的日常环境)核对来源和依赖镜像 |
| 服务已启动，`ops-check.ps1` 提示无近期验证备份 | 本机备份按当前策略停用，该项会返回非零；逐项检查 `issues`，不要把其它服务、任务或数据异常当作同一原因                                                        |

以下命令均在项目根目录执行：

```powershell
# 查看全部容器，包括已停止的服务
docker compose ps -a

# 查看最近的日志
docker compose logs --tail=100 server web

# 暂停服务，给数据库留出退出时间，保留数据
docker compose stop --timeout 60

# 再次启动已有容器，沿用原镜像和配置
docker compose start --wait --wait-timeout 300
```

以上是日常启停命令，不用于构建新版本或应用配置修改。数据库、Redis 和上传文件使用三个 Docker 持久卷保存。不要使用 `docker compose down -v` 停止日常环境，该命令会删除数据卷。

## 运行边界

本机保持单机演示，完整数据备份、离线镜像恢复包和自动备份停用，恢复指针为 `DISABLED_BY_USER`。下文保留人工备份、复制和调度工具的用法。只读运维检查中的 `No recent independently verified backup` 对应当前停用策略，该项会返回非零；其余问题按 `issues` 逐项处理。发布时的临时恢复校验与清理规则见[部署说明](platform-deployment.md#本机保留策略)。

菜单的目录、口味和套餐组成按店批量读取，库存和活动价实时批量查询。下单、支付退款仍保留幂等与事务保护。容量测量与资源条件见[当前版本记录](verification/current.md)。

| 保护项       | 默认行为                                                                                                                            |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| 图片上传     | 商品图片与售后图片共用1个处理名额；单张输入及编码后文件最多5 MiB，边长最多4096像素                                                  |
| 图片存储     | 总配额2 GiB，磁盘剩余不足256 MiB时拒绝新上传；已有图片仍可读取                                                                      |
| 运行数据维护 | 后台任务开启时每10分钟运行，两类交替、每批200条、每类最多10批，共享5秒软预算；清理过期超过7天的会话和创建超过30天且已成功的通知任务 |
| 业务数据     | 不自动删除订单、支付退款记录、审计或业务图片；未绑定举证、疑似孤立文件只报告                                                        |
| 容器日志     | 每个容器最多保留3份日志，每份10 MiB                                                                                                 |
| 数据库等待   | 连接获取3秒、行锁3秒、非事务SQL5秒、事务10秒；JDBC建连2秒、读取12秒                                                                 |
| 请求等待     | Nginx上游等待20秒；前端普通请求25秒。写请求超时后先查询处理结果，不自动重发                                                         |
| 后台处理     | 退款、通知、订单超时和配送超时独立执行；批量上限20／50／100条，每轮5秒软预算，当前记录结束后停止继续领取                            |

上传阈值可在 `.env` 中设置 `UPLOAD_CONCURRENCY`（默认1，最多4）、`UPLOAD_QUOTA_BYTES` 和 `UPLOAD_MIN_FREE_BYTES`。上传繁忙返回 `503 UPLOAD_BUSY`，空间不足返回 `507 UPLOAD_STORAGE_FULL`；配置变更后需重建相应容器。

公开搜索（包括分页）按来源IP默认每分钟1200次，其余公开校区、楼栋、店铺、目录和评价读取合计3000次，可用 `SEARCH_REQUESTS_PER_MINUTE`、`CATALOG_REQUESTS_PER_MINUTE` 配置。登录保留20次/IP/分钟与10次/账号/分钟，订单写操作保留30次/身份/分钟。429返回 `Retry-After: 60`，客户端等待后重新申请，服务端按当时额度判定。代理清除外来Forwarded与X-Forwarded信息后仅写入可信来源；不要将Java端口直接暴露给不受信任客户端。Redis不可用时使用有容量上限的进程内限流。

所有限流请求先经过本地10000键容量的固定窗口闸门，再使用Redis计数；Redis缓存淘汰、故障或主动绕过不会放宽本进程预算。进程重启会重建本地窗口，仍需正常Redis计数共同保护；跨实例计数依赖 Redis。限流和菜单Redis操作共用最多4个在途命令；调用失败后短路5秒，再只允许一个恢复探测，后续健康调用恢复正常。首次故障调用会等待 Redis 超时，后续调用受短路保护。

菜单缓存以进程epoch和256条带代际校验，旧进程缓存、提交前的旧回填不会被新代际请求采用。商品事务提交后仅推进本地代际，持久通知任务异步删除Redis键，商家响应不再等待菜单读取锁或Redis删除；任务稍后删除新缓存只会触发重新加载，不影响数据库结果。保留单键合并、最多8个回源任务、1.5秒协调等待和独立结果副本。

每店最多500个菜品和套餐（含停售项），菜品最多20组口味，套餐最多50项组成；已有商品仍可编辑。历史超限的整店菜单返回409 `CATALOG_LIMIT`，历史数据保留，由维护者在停写并备份后整理。V11新增菜品和套餐的 `(store_id,status,id)` 有序索引，搜索以商品表驱动，保持可见性、分类和营业筛选；各分支先取有序的每页数量加1条，合并候选最多402条。广泛匹配可按索引早停，字面子串低命中或无命中仍可能扫描较多数据，扫描成本随匹配率和数据量变化。

私有举证默认全局4、每账号2个并发读取，每账号120次/分钟，可用 `EVIDENCE_READ_CONCURRENCY`、`EVIDENCE_READ_ACCOUNT_CONCURRENCY`、`EVIDENCE_READS_PER_MINUTE` 配置；全局上限可设1—32，每账号并发不得高于全局。鉴权后检查文件再开始PNG响应，固定16KiB缓冲；断线会释放文件和名额。503 `EVIDENCE_BUSY` 表示稍后重试，429表示账号预算耗尽，403表示权限拒绝；不自动重放请求，工作台允许手动重试。响应保持 `no-store`，不转为公开图片。

菜单同键并发请求合并回源，全局最多8个加载任务，协调等待最多1.5秒，繁忙返回 `503 MENU_BUSY`；数据库执行本身仍服从数据库超时。缓存失效推进代际，旧回填即使晚到也不被新代际读取，每请求获得独立结果副本。维护清理数量与预算耗尽通过 `campus.maintenance.deleted`、`campus.maintenance.budget.exhausted` 和 `campus.maintenance.budget.exhaustions` 指标观察。

V10新增 `mock_refund_receipt`，只保存模拟退款网关凭据；它与业务落账使用独立事务，进程丢失后按业务键重试仍复用回执，内容不同返回冲突。历史成功退款不会重做。该表随现有全表备份恢复，不自动清理金融业务记录。员工实时连接仍最多100个、每账号2个，通知是提示，订单查询负责补偿。

实时票据签发增加每账号30次／分钟及每IP60次／分钟预算；握手每IP60次／分钟。票据Redis读写经过同一有界并发与熔断入口，故障不放行认证：无效票据401、超限429、依赖不可用503，订单查询仍可补偿通知。

匿名健康读取仅保留根健康、readiness和liveness；operations详情需要平台身份。`ops-check.ps1` 在容器内派生端点专用维护凭证，通过curl标准输入传递，不写文件或输出凭据；服务只接受容器loopback上的GET精确operations路径，不能用于其他接口，也不替代普通Bearer认证。维护脚本保持只读，不额外建立登录会话。

新增 `/api/v1/user/stores/page`、`/api/v1/merchant/stores/page`、`/api/v1/user/search/page` 返回 `records`、`nextCursor`、`hasMore`，默认50条、最多200条。后续请求携带不透明 `cursor` 并保持原筛选及账号；筛选或账号变化应从第一页开始，失效游标返回400。原数组接口保持最多200条的兼容行为，完整检索应使用分页接口。分页按稳定ID顺序前进，不提供跨请求数据库快照；营业状态或商品变化会影响后续结果。

默认 LOCAL 演示的上传空间通过单实例进程内账本记录，包含公开图片、`.private` 举证和 `.tmp` 临时文件；公开文件数量单独计数。首次使用完整扫描，后续写入、移动和失败回滚同步更新；距上次校准60秒后的首次上传、接近容量／数量上限或用量不确定时重新扫描。每次上传与发布仍检查真实磁盘剩余空间，无法确认用量时不允许继续发布。重启后重新建立账本，不创建定时任务，也不自动清理文件。上传卷应由应用独占写入；停写后才能恢复或手工增删文件，容器外变动不保证即时识别。生产多实例使用 COS、共享数据库元数据和上传预算，不共享本地上传目录，见[文件存储](storage-integration.md)。

```powershell
# 一次性维护检查：任务推进、积压、图片完整性、磁盘和最近48小时内验证备份
pwsh -NoProfile -File ./scripts/ops-check.ps1

# 安全备份：暂停写入，备份并独立恢复验证，然后恢复服务原来的运行状态
pwsh -NoProfile -File ./scripts/safe-backup.ps1
```

检查发现异常会返回非零退出码。首次没有验证备份时会提示；只读检查不会替你创建备份或清理文件。备份会产生短暂维护窗口，成功后在 `.local/backups/` 对应目录生成 `verified.json`；失败不会生成成功标记。检查备份时验证标记、清单及文件是否齐全，重新验证文件哈希和数据库恢复使用 `restore-check.ps1`。

平台管理员可在订单工作台对已分配或配送中的配送订单改派，取餐后需确认已安排实际交接；已备餐或配送中的订单无法继续履约时，可填写原因执行「异常取消并退款」。取消复用模拟退款任务，不回补已消耗库存、不自动退券、不影响同一合单其他店铺；已有售后或退款预占须先处理。两项操作均校验版本并记录审计，普通用户和商家没有这些平台权限。

后台失败按记录隔离，超时订单轮转扫描，持续失败记录不会固定挡住后续订单。运维检查读取仅容器内部访问的 `/actuator/health/operations`：普通任务超过120秒、指标超过60秒、维护超过12分钟未完成一轮即异常；首次执行使用同样宽限期，执行失败立即可见。此健康组不影响容器readiness，也没有通过Nginx公开。检查还报告逾期待处理任务、无有效配送员的在途订单、配送超过60分钟、缺失图片及文件扫描失败；未知结果不会记为零。每个Docker读取命令最多15秒，内部HTTP检查最多5秒。

后台任务关闭时检查默认失败；仅在刻意关闭任务的隔离验收环境使用 `./scripts/ops-check.ps1 -AllowTasksDisabled`。检查不自动修复订单、重试退款或删除文件。上述超时和批量参数用于限制单轮资源消耗。

升级会先保留旧镜像、构建源码，再检查已停止或运行中的数据库。有旧数据时先暂停写入，完成备份与独立恢复检查，再执行迁移；有业务表但无 Flyway 历史时自动升级会停止。保留原数据卷，回滚需匹配数据库和文件备份。

系统定时任务、异机备份和外部告警默认关闭。需要启用时，通过下方的一次性运维入口配置实际副本目标和通知渠道，并验证恢复及告警送达。默认演示的 `PaymentGateway` 仍为模拟渠道；生产已提供独立的持久支付、微信验签、查询关单、异步退款及渠道对账流程，按[生产接入](production-readiness.md)配置和真实验收。

对外部署前配置独立用户身份、关闭共享演示登录、启用 HTTPS 与异机备份；真实收费使用生产微信渠道。

平台介入入口按“商家拒绝或48小时未处理”的规则显示，支付与退款继续使用模拟实现。当前源码测试与日常部署核验的范围见[当前版本验证与维护记录](verification/current.md)；完整角色回归以最终提交的 CI 结果为准。

## 可配置的一次性运维入口

```powershell
Copy-Item ./deploy/ops-config.example.json ./.local/ops-config.json
pwsh -NoProfile -File ./scripts/ops-run.ps1 -Action Check
# 有意执行备份时才会暂停业务写入
pwsh -NoProfile -File ./scripts/ops-run.ps1 -Action Backup
```

`Check` 仅检查并更新本机运维状态，不创建备份、不停止容器；`Backup` 调用原有安全备份与独立恢复流程，再按配置复制。升级、备份和运维入口共用项目锁，同一项目重复运行会失败，不会相互恢复错误的运行状态。不同项目有独立的锁。

配置文件字段：`schemaVersion` 固定为1；`replicaDirectory` 为受控文件系统目录，`replicaRecipient` 为age接收公钥；`alertWebhookUrl` 为通用 HTTP POST 接收地址；默认 `alertTimeoutSeconds=5`、`alertMaxAttempts=2`，允许范围分别为1—10秒、1—3次。缺少配置会明确报告未接入并返回非零，不会自动寻找外部服务。配置中的地址可能包含密钥，应仅保存在 `.local` 并限制访问。

恢复材料包含数据库、上传文件、隐藏的 `.env`。本机恢复集严格限制访问；对外副本使用固定版本age加密，缺少 `replicaRecipient` 时拒绝生成，私钥不随备份打包。来源恢复标记与清单通过后才打包加密，密文哈希核验通过后发布完成标记；解密在受限目录完成并再次校验内部清单。目标目录仍须受控，密文及私钥应分开保管。`.partial-*` 为失败现场，不自动删除。异机恢复在目标环境另行演练。

`ops-schedule.ps1` 只生成并解析Windows任务定义，不注册任务：检查默认每5分钟，备份默认上海时间03:00。备份会暂停写入，正式启用前需要维护窗口、受控副本位置及通知接收端。本轮只交付配置并在隔离环境验证，不安装日常任务。`decrypt-backup.ps1` 用于指定密文及私钥的受控解密，具体参数见脚本入口。

告警只发送项目、状态、时间、问题代码及指纹，不发送配置、原始错误、订单资料或文件内容。首次正常检查保持安静；异常变化或恢复时通知，同一异常不重复通知，发送失败下次重试。接收地址要求 HTTPS，仅本地夹具允许 loopback HTTP；不跟随重定向。`.local/operations-state/` 保存本地状态和最近报告。任务调度、通知接收和停机窗口需要部署者自行安排。

## 历史异常记录

用户售后，以及商家／平台的售后、退款和通知任务支持筛选与加载更早记录。订单号和任务编号为精确匹配，日期筛选按上海时区的创建日期计算，开始与结束日期需同时填写、最多366天。每页100条按编号倒序；刷新保留已加载页与已应用筛选，切换账号或店铺会重置。权限仍按服务端用户、店铺范围检查，查询旧记录不会自动重试任务。

这些列表及订单、用户结算单接口支持可选 `limit`：默认100，允许1—1000，非法值返回400。页面刷新按已加载范围分批读取，每次最多1000条，并继续使用 `beforeId`；旧记录状态仍随约15秒轮询更新。订单页手动刷新仍回到第一页，售后／任务列表手动刷新仍保留已加载历史与筛选。请求失败保留上次成功结果，切换账号／店铺或退出页面后丢弃旧响应；业务写入与轮询互斥。

## 镜像依赖与安全清单

```powershell
pwsh -NoProfile -File ./scripts/scan-images.ps1 `
  -ServerImage campuseats-server:v1 -WebImage campuseats-web:v1 `
  -MySqlImage <当前MySQL镜像ID> -RedisImage <当前Redis镜像ID> `
  -OutputDirectory .local/security
```

入口下载固定版本并验证官方归档SHA-256，扫描应用、Web及显式指定的MySQL／Redis镜像系统包与后端Java运行时依赖。CI从实际测试容器解析MySQL／Redis镜像ID并强制扫描四服务。后端构建时由CycloneDX按实际解析的compile／runtime依赖生成清单，排除test／provided／system范围；JAR、清单及同次构建的SHA-256清单一起进入镜像。扫描器从指定镜像ID创建不启动的临时容器提取这些文件，核对JAR和清单哈希，再用Trivy扫描依赖清单；临时容器只按本次创建ID移除，不删除卷或操作运行服务。

该方式不依赖近1GiB的Java识别库，也不跳过Java依赖检查。完整系统包／Java漏洞JSON、CycloneDX清单、镜像与JAR／清单来源证据和门禁结果分别保留。任意等级的已知漏洞均阻断，包括未提供修复版本的条目；另按[上游公告](https://github.com/libexpat/libexpat/issues/1076)检查完整包清单中的Expat，拒绝仍携带未修复CVE-2025-66382的镜像，避免发行版漏洞库漏报。缺失或不匹配的运行时清单、数据库下载、扫描或转换失败也会失败。扫描结果绑定当次漏洞库、镜像和运行依赖清单。首次使用需要联网，报告保存在 `.local`，CI制品同时保留四服务扫描证据。

## 有限性能测量

只在已有独立项目、数据卷、镜像标签与loopback端口的环境执行。下面假设隔离项目为 `campuseats-perf`、端口为28086、配置文件为 `.local/perf.env`；先按[独立验收说明](platform-deployment.md#独立验收)创建独立数据库、Redis 和上传卷。

```powershell
node ./scripts/prepare-performance.mjs --project campuseats-perf `
  --config .local/perf.env --base-url http://127.0.0.1:28086 `
  --output .local/perf-sessions.json
$env:BASE_URL='http://127.0.0.1:28086'
$env:PERF_PROJECT_NAME='campuseats-perf'
$env:PERF_USER_TOKENS_FILE='.local/perf-sessions.json'
$env:ADMIN_PASSWORD=([regex]::Match((Get-Content .local/perf.env -Raw),'(?m)^DEMO_ADMIN_PASSWORD=([^\r\n]+)')).Groups[1].Value
$env:PERF_OUTPUT='.local/performance/report.json'
node ./scripts/measure-performance.mjs
Remove-Item Env:ADMIN_PASSWORD
```

身份准备先核对容器项目、工作目录、实际loopback端口和独立数据卷，再直接在隔离数据库建立30个用户及会话，以及205家店和1025个商品，按应用协议签发令牌并通过普通鉴权接口核验。用户与会话由数据库夹具准备，HTTP 鉴权用于核对其有效性。私有会话文件和匹配的metadata只保存在 `.local`，测量前需在一小时内重新准备，使用新输出文件名避免覆盖。不要提交、分享或打印其中的令牌。

默认1／5／10／30个客户端读取阶梯各60秒，每客户端交替请求菜单和分页搜索，每次间隔1秒。随后30客户端混合运行5分钟，其中5个写者按0／90／180秒各完成3笔完整模拟交易，其余时间浏览；共验收15笔下单、模拟支付、接单、取消申请、批准及退款。保留服务端真实限流和每身份24次／61秒写预算，分别记录虚拟客户端数与实际在途请求峰值。读取阶段可通过 `PERF_CONCURRENCY`、`PERF_SECONDS`、`PERF_READ_PAUSE_MS` 调整，报告始终记录实际策略。

JSON报告包含请求量、成功吞吐、HTTP／读取／交易P95、非预期错误、失败客户端、写预算等待，以及夹具店铺退款／通知积压的完整分页快照。每10秒异步采样容器资源，结束核对OOM、重启和数据库库存／退款／数量约束。目标为读取P95不超过1秒、完整交易P95不超过10秒、15笔交易均完成、无非预期错误或失败任务，结束后60秒内夹具任务清空。任一目标失败即返回非零并保留部分报告。结果记录本机短时模拟资金场景及实际资源条件。

### 午餐集中到达与历史增长

使用相同隔离部署及资源限制，身份准备时增加 `--lunch`，生成30名用户与3名同店员工的夹具会话，均通过普通HTTP鉴权核验。测量设置 `PERF_SCENARIO=lunch`：持续5分钟，在第0／90／180秒各由30名用户同时开始一笔加购、报价、下单、模拟支付、商家接单、取消申请、批准和模拟退款，共90笔。每名员工负责10名用户，保留真实账号写预算，不通过提高限流制造容量。

用户在波次之间按1秒间隔混合读取菜单、命中／无命中搜索和100条订单历史；另1个工作台观察者每15秒读取商家历史、流水及对账。实际在途峰值、每类接口P95、交易P95和任务排空分别记录。读取整体及各类读取接口P95均须不超过1秒，交易P95不超过10秒，90笔均完成，且没有非预期错误、约束异常、OOM或重启。该场景测量合成数据下的集中交易与读取，登录和浏览器渲染另行验收。

先测无合成历史的基线，再对同一夹具递增至5万、20万笔历史订单。下面只展示已有隔离环境中的命令，配置、令牌与输出均须在 `.local`，不得替换为日常项目：

```powershell
node ./scripts/prepare-performance.mjs --project campuseats-perf-lunch-20261031 `
  --config .local/verification/lunch-capacity-20261031/perf.env `
  --base-url http://127.0.0.1:28096 `
  --output .local/verification/lunch-capacity-20261031/sessions.json --lunch
$env:BASE_URL='http://127.0.0.1:28096'
$env:PERF_PROJECT_NAME='campuseats-perf-lunch-20261031'
$env:PERF_USER_TOKENS_FILE='.local/verification/lunch-capacity-20261031/sessions.json'
$env:ADMIN_PASSWORD=([regex]::Match((Get-Content .local/verification/lunch-capacity-20261031/perf.env -Raw),'(?m)^DEMO_ADMIN_PASSWORD=([^\r\n]+)')).Groups[1].Value
$env:PERF_OUTPUT='.local/verification/lunch-capacity-20261031/history-0.json'
$env:PERF_SCENARIO='lunch'
node ./scripts/measure-performance.mjs
# 每次增长前先等待上一轮测量结束；目标依次为50000、200000。
node ./scripts/prepare-performance.mjs --project campuseats-perf-lunch-20261031 `
  --config .local/verification/lunch-capacity-20261031/perf.env `
  --base-url http://127.0.0.1:28096 `
  --output .local/verification/lunch-capacity-20261031/sessions.json `
  --grow-history --history-orders 50000
$env:PERF_OUTPUT='.local/verification/lunch-capacity-20261031/history-50000.json'
node ./scripts/measure-performance.mjs
# 再将增长目标改为200000，完成后用新的history-200000.json输出重复测量。
Remove-Item Env:ADMIN_PASSWORD,Env:PERF_SCENARIO
# 结束后仅停止本轮隔离项目，为测试库刷新预留60秒。
docker compose --env-file .local/verification/lunch-capacity-20261031/perf.env `
  -p campuseats-perf-lunch-20261031 stop -t 60
```

历史数据仅在核验归属的隔离库生成：分布于过去90天，80%集中在热店，包含已完成自取订单、明细、支付流水、模拟支付凭据及已消耗配额。30个账号集中历史订单以测试读取成本，SQL 夹具与本轮 HTTP 成功交易分别计数。增长只增加数据，不删除或复制日常数据，不创建备份、不启用调度。容量结果及当前保留目录见[当前版本记录](verification/current.md)。
