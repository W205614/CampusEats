# 腾讯云生产部署与多实例运行

本指南交付部署代码和配置。没有提供商户、云账户和真实域名，因此不代表已经完成腾讯云、微信真机、真实付款或云端恢复验收。云接入项目的具体变量和凭据见生产接入说明；默认本机演示与原来相同，本指南不启用现有本机备份任务。

## 固定拓扑与准备

CLB HTTPS 入口连接两台 CVM 的 Web 端口，每台 CVM 运行同一制品的 Web 和 Java；Java 连接托管 MySQL、控制 Redis、缓存 Redis、COS。两台 CVM 使用不同 `INSTANCE_ID`，共享业务数据库、JWT 密钥、支付配置和对象存储配置。Redis 控制实例使用 `noeviction`，缓存实例可使用 `allkeys-lru`；不同逻辑 DB 无法隔离淘汰策略，不能代替两个实例。

创建资源与权限在腾讯云、微信后台完成：配置服务商与子商户权限、域名与证书、私网与安全组、数据库与 Redis TLS、COS 私有桶及版本保留策略。运行账户使用业务库最小权限；迁移窗口使用具备 DDL 权限的独立数据库账户。数据库客户端启用 `sslMode=VERIFY_IDENTITY`，将数据库 CA 导入挂载的 Java truststore。云产品版本、可用区、备份保留策略和恢复时间需在真实账户中确认。

数据库按本仓库已验证的 MySQL 8.4 / InnoDB 选择支持该版本的高可用规格，实际地域和规格以控制台为准；[腾讯云创建说明](https://cloud.tencent.com/document/product/236/46433)列明支持版本。控制与缓存 Redis 分别使用兼容单实例协议的**标准、非分片架构**读写端点。控制实例使用 `noeviction`，凭据允许 `INFO memory` 和现有 Lua 命令，不能替换为只读端点。

当前控制客户端及验证码原子脚本按单分片实现，脚本涉及多个不同业务 Key，未实现分片槽位命名策略。腾讯云集群架构要求同一 Lua 脚本的 Key 位于同一 Slot，因此不能仅更换地址就把分片集群视为已兼容；见[标准架构](https://cloud.tencent.com/document/product/239/31959)与[命令准则](https://cloud.tencent.com/document/product/239/90961)。这一资源选择属于本方案接入条件，不通过新增集群兼容层改变当前业务范围。

模板通过 `JAVA_TOOL_OPTIONS` 设置全局 `javax.net.ssl.trustStore`，它会替代 JRE 默认公共信任库，不会自动合并。应以候选镜像对应 JRE 的默认 `cacerts` 公共根 CA 为基础生成 PKCS12，再增加经云数据库官方核对的 CA；不能新建一个只包含数据库 CA 的全局信任库，否则微信、短信和 COS 的 HTTPS 证书也会验证失败。可用 `keytool -importkeystore` 导入原信任库，再用 `-importcert` 增加数据库 CA，并用 `-list` 核对内容；实际文件类型与 `trustStoreType`、读取口令方式必须匹配。这里只保存 CA 证书，不放商户私钥，不关闭主机名校验或改为信任全部证书。[Java 21 JSSE 信任库规则](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html)、[keytool 命令](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html)

`DB_URL` 保留 `serverTimezone=Asia/Shanghai` 或 `connectionTimeZone=Asia/Shanghai`。生产连接池每个新连接执行 `SET SESSION innodb_lock_wait_timeout=3, time_zone='+08:00'`，使 SQL 的业务日期、Outbox 时间与上海业务时钟一致，不依赖托管数据库主机的系统时区。集群租约显式使用 `UTC_TIMESTAMP`，保持独立的 UTC 协调语义。

安全组只允许 CLB 访问 Web 端口；MySQL、Redis 只允许应用 CVM 访问；监控监听默认只发布在本机 loopback。`CLB_CIDR` 必须填写实际 CLB 来源网段，不填写整个互联网或整个 VPC；Nginx 仅信任这个来源提供的客户端 IP，并覆盖转发请求头。业务公网入口拒绝监控和 API 文档路径。CLB 的 HTTP 健康检查使用 `/healthz`，不使用会重定向的首页。

## 配置与首次部署

将经过现有制品校验流程验证的应用镜像推送到自己的镜像仓库，生产配置引用不可变内容摘要，不使用 `latest`。两台 CVM 均安装 Docker Compose 与 PowerShell 7，并部署仓库中的 Compose 和 `deploy/production` 目录。复制 `deploy/production/env.example` 为受保护的 `.env.production`，填入真实值；密钥文件放入 `PRODUCTION_SECRETS_DIRECTORY` 并只读挂载。再将 `deploy/production/integrations.example.yml` 复制到 `PRODUCTION_INTEGRATIONS_FILE` 指定的受保护路径，填写政策正文和实际审核模板字段，作为必需的只读 Spring additional-location 挂载。该文件显式映射普通云环境变量，缺文件会拒绝启动；不需要改 Java 或前端源码。`.env.production`、实际接入配置与密钥均不入库，不将其内容粘贴到日志或 issue。

Java 容器以 `UID:GID=10001:10001` 运行。在 Linux 应用 CVM 上，挂载目录须允许这个身份遍历，私钥、truststore 和接入配置须允许读取；只读挂载本身不会补足宿主文件的读取权限。以下以模板默认路径为例，由 root 或具备 sudo 权限的维护者执行：先创建目录并放入实际文件，再设置文件权限。

```bash
sudo install -d -o root -g 10001 -m 0750 .local/production-secrets .local/production
# 将真实密钥、truststore 和 integrations.yml 放入对应目录后执行。
sudo chown root:10001 .local/production/integrations.yml \
  .local/production-secrets/wechatpay-private-key.pem \
  .local/production-secrets/wechatpay-public-key.pem \
  .local/production-secrets/mysql-truststore.p12
sudo chmod 0640 .local/production/integrations.yml \
  .local/production-secrets/wechatpay-private-key.pem \
  .local/production-secrets/wechatpay-public-key.pem \
  .local/production-secrets/mysql-truststore.p12
```

因此目录为 `root:10001`、`0750`，上述文件为 `root:10001`、`0640`；应用组只能读取文件，不能改写。宿主机 `.env.production` 由执行 Compose 的维护账号持有并设为 `0600`，该账号还须能读取预检需要的接入文件，可使用受限的 10001 组权限或以 root 执行维护命令，不通过开放其他用户权限解决访问问题。实际生成的 `restore-evidence.json` 也按应用配置文件设置组和读取权限。监控 CVM 使用自己的 Grafana 密码目录，按其容器身份单独授权，不复制应用的支付私钥到监控目录。

`PRODUCTION_INTEGRATIONS_FILE` 在容器中固定对应 `/run/config/integrations.yml`；`PRODUCTION_SECRETS_DIRECTORY` 对应 `/run/secrets`，`PRODUCTION_CONFIG_DIRECTORY` 对应 `/run/operations`，三处均只读挂载。`WECHATPAY_PRIVATE_KEY_PATH`、`WECHATPAY_PUBLIC_KEY_PATH` 和 `JAVA_TOOL_OPTIONS` 中的 truststore 路径须使用这些**容器路径**，文件名须与实际文件一致。改变宿主目录时同步修改对应 `PRODUCTION_*` 变量，不把宿主绝对路径误填为容器内证书路径。

生产模板统一使用 `WECHAT_MP_APPID` / `WECHAT_MP_SECRET` 映射公众号 OAuth 与 JSAPI 的同一应用；小程序使用独立 `WECHAT_APPID` / `WECHAT_SECRET`。外部业务通知默认关闭，验证码短信独立启用。配置至少一个审核通过的业务模板与对应字段映射后，再设置 `EXTERNAL_NOTIFICATIONS_ENABLED=true` 并验收真实发送；模板中的两个示例字段仅适用于实际审核字段完全相同的模板。

先执行只读检查：

```powershell
pwsh -NoProfile -File ./scripts/production-preflight.ps1 -EnvFile .env.production
docker compose --env-file .env.production -f compose.production.yaml config --quiet
```

先在隔离环境验证迁移和恢复。在两台应用都未运行的维护窗口，只在第一台 CVM 使用迁移账户执行一次迁移；关闭任务且不启动 Web。`prod` 默认关闭 Flyway，避免滚动发布时多个节点同时修改 schema。

```powershell
$env:MIGRATIONS_ENABLED='true'
$env:TASKS_ENABLED='false'
docker compose --env-file .env.production -f compose.production.yaml up -d server
docker compose --env-file .env.production -f compose.production.yaml logs --tail 100 server
```

确认迁移成功和 readiness 正常后，使用普通业务数据库账户恢复配置。将 `MIGRATIONS_ENABLED` 恢复 `false`、`TASKS_ENABLED` 恢复 `true`，重新创建第一台 server，再启动 Web；随后按相同制品和普通账户启动第二台。

```powershell
$env:MIGRATIONS_ENABLED='false'
$env:TASKS_ENABLED='true'
docker compose --env-file .env.production -f compose.production.yaml up -d server web
docker compose --env-file .env.production -f compose.production.yaml ps
```

生产运行不能同时启用 `demo`、`trial`、`docker`；启动检查会拒绝。也不能把原本的演示数据库直接认作真实资金数据库；历史 `MOCK` 交易始终保持模拟归属。

Flyway V1 只创建表，不插入员工账号。演示账号由仅在 `demo` profile 生效的初始化器创建；`prod` 使用独立初始化器，锁定数据库初始化记录，在没有员工的新库创建一个平台管理员并强制首次改密，同时记录初始化完成。并发启动只创建一次；首次生产初始化若发现已有员工但没有生产初始化记录会拒绝启动，不沿用、重置或删除演示账号。首次成功后移除 `BOOTSTRAP_ADMIN_PASSWORD`，以后重启不会改变已有管理员密码。

## 滚动更新与失败处置

新版本先运行迁移兼容和隔离恢复验证。涉及不兼容旧程序的 schema 变更时执行维护窗口，不以逐台更新冒充兼容发布。兼容变更按以下顺序操作：从 CLB 摘除节点一、等待现有连接排空、配置匹配的新镜像摘要并更新该节点、确认健康和定向业务验收后重新加入 CLB，再更新节点二。先完成版本对应的迁移，业务节点继续保持 Flyway 关闭。

应用回退只在旧程序与新 schema 兼容时进行。迁移失败保持停止写入并保留现场，按已独立验证的恢复集处理，禁止自动执行反向 SQL 或覆盖数据库。恢复后重新检查渠道支付状态和差异账单，再开放交易；恢复数据库不能撤销已经发生的微信付款。

两个应用实例共同处理数据库任务；租约和完成令牌阻止过期执行者覆盖新执行结果。菜单变更在同一事务递增数据库版本，Redis key 包含版本；迟到的旧回填不会成为新请求的缓存。实时事件经过 Redis Pub/Sub 到各实例，连接仍保存在本实例，断线通过站内消息和订单查询补读。Pub/Sub 成功不是客户端送达或阅读确认。

本地双后端回归使用独立项目、全新合成数据卷与两个 loopback 端口；提前构建本次候选镜像后执行 `pwsh -NoProfile -File ./scripts/verify-cluster.ps1 -ServerImage <候选镜像>`。脚本核验项目归属、独立卷及端口，以真实 HTTP 验证跨节点会话、目录更新、票据、WebSocket 广播、下单幂等及停掉一个节点后的读取与任务恢复。结束后停止该隔离项目，保留诊断材料和卷，不启动日常服务。该场景使用演示支付与本地上传卷，COS 和真实渠道必须通过独立适配器测试与云端验收。

## 监控

`monitoring` Compose profile 提供 Prometheus、Grafana 与 Alertmanager，不默认启动。若使用专用监控 CVM，先配置两台应用 CVM 的 `MONITOR_BIND_ADDRESS` 为各自私网地址，安全组仅允许监控机访问 19090；将 `deploy/production/monitoring/prometheus.yml` 默认的 `server:9090` 替换为这两台应用的实际私网地址与 19090 端口。默认 `server:9090` 只适用于 Prometheus 与应用 server 在同一 Compose 网络的部署，专用监控机上不会自动发现远程应用。

监控机的 `PRODUCTION_SECRETS_DIRECTORY` 指向仅存放 `grafana-admin-password` 的独立目录，不复制应用密钥目录。生成强随机密码后，按选定 Grafana 镜像的实际 UID/GID 授予该目录遍历权和密码文件只读权限，可先通过 `docker run --rm --network none --entrypoint id <Grafana镜像摘要>` 核对身份；不要直接套用 Java 容器的 10001 权限。文件路径在容器中保持 `/run/secrets/grafana-admin-password`，不把密码放在命令行或日志。完成这些配置后启动：

```powershell
docker compose --env-file .env.production -f compose.production.yaml --profile monitoring up -d prometheus alertmanager grafana
```

通过 SSH 隧道访问监控机本机 Grafana 13000、Prometheus 19091、Alertmanager 19093。若选择监控与应用同机运行，也须单独解决 Grafana 身份对其密码文件的读取权限，不向 Grafana 授予支付私钥读取权限。生产 Java 的 9090 只提供健康和 Prometheus，其他管理请求拒绝；不将这个监听映射到公网地址。

默认 Alertmanager 接收器明确标记为 `unconfigured`，没有向外发送通知。填写自己的 webhook 或邮箱接收配置并执行一次告警与恢复测试后，才可记录为“外部告警已验收”。Dashboard 展示实例、请求率、P95、任务积压和 JVM；HTTP 分位数依赖应用直方图配置，不能用没有样本的空面板证明正常。

监控模板采用的版本在 2026-11-06 根据 [Prometheus 官方下载](https://prometheus.io/download/)和 [Grafana 官方下载](https://grafana.com/grafana/download?edition=oss)核实：Prometheus 3.13.5 LTS、Alertmanager 0.34.2、Grafana 13.3.0，并通过 Docker Registry 元数据固定清单摘要。模板未运行这些镜像的安全扫描，不能将摘要固定或配置语法检查当作漏洞扫描通过。

## 备份、PITR 与对象恢复

生产 MySQL 开启托管自动备份与 binlog/PITR，保留期按业务要求配置；COS 使用不可变对象标识并启用版本保留，禁止生命周期规则提前删除仍可能被恢复数据库引用的对象。数据库备份和 COS 对象不是同一种备份，均须验证。此配置不改变本机 `DISABLED_BY_USER` 状态、不注册 Windows 或 Linux 定时任务。

恢复演练使用新的托管数据库实例和隔离的对象命名域，不覆盖在线库、不使用在线商户进行写入测试。选择数据库恢复时点，读取该时点文件元数据，对每个对象核对可读性、大小、摘要和私有权限。检查订单、退款、优惠、库存约束及账务差异，并关闭所有定时交易任务和外部通知。恢复出的支付状态须以微信查单与渠道账单重新核对后才能接回生产。

保存恢复时点、来源备份/日志范围、隔离目标、对象核对数量和摘要、执行命令版本、结果、实际耗时。仅开启云备份不能标记为“独立恢复通过”，也不自行承诺 RPO/RTO。真实恢复验收通过后，将操作者核实过的回执写入受保护配置目录的 `restore-evidence.json`，包含 `status: "PASSED"`、`isolated`、`databaseChecked`、`objectsChecked`、`channelReconciled` 四个 `true` 标记，以及带时区的 `verifiedAt`。模板挂载该目录为只读，应用每分钟读取回执并发布备份时间指标；不存在、格式错误或未来时间均置为无效，超过48小时触发告警。软件检查只核验回执格式，不证明填报内容，必须附有实际演练的来源与核对材料。也可用 `production-preflight.ps1 -RestoreEvidence <回执路径> -RequireLiveEvidence` 检查回执新鲜度；本轮没有替真实云恢复生成成功回执。
