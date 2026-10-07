# v1 部署、迁移与恢复

## 新部署

启动脚本只依赖项目源码，Docker分别编译后端和两端页面。管理端/admin/、H5/app/共用Nginx；MySQL/Redis/Java不暴露主机端口。默认绑定127.0.0.1。

配置存放本机.env，不进入Git或镜像。首次创建demo管理员需DEMO_ADMIN_PASSWORD；以后修改该项不会重置账户。JWT_ADMIN_SECRET至少32字节。默认Compose使用docker,demo配置，明确模拟支付。

启动脚本在替换已有v1镜像前增加`rollback-时间戳`标签，保留可直接使用的旧镜像；不要只保存未打标签的镜像ID，部分Docker存储后端在标签替换后可能无法再次引用它。直接调用`docker compose build`时也应先手动打保留标签。回退涉及数据库迁移时，仍须用已验证备份和旧版本成套恢复。

后端存活与就绪分离，就绪检查MySQL。Redis故障允许缓存回源和本地限流，票据返回503。持久卷包括MySQL、Redis与上传图片。Java容器非root，资源上限768MB。

镜像构建Maven使用公开构建镜像源，可通过build arg MAVEN_MIRROR_URL改为https://repo.maven.apache.org/maven2。仓库版本和lockfile固定；首次下载需要网络。

## 微信小程序

1. 运行scripts/build-frontends.ps1，导入frontend/client/dist/build/mp-weixin。
2. 本地开发可使用游客AppID、关闭合法域名校验进行工具预览；这不支持发布。
3. 真机把frontend/client/.env.local的VITE_API_BASE设为设备可访问的HTTPS域名，重新编译。
4. 配置自己的微信AppID、后端WECHAT_APPID/WECHAT_SECRET及合法request域名。不要把Secret放入前端。
5. 记录开发者工具、真机、微信登录的实际验收结果，不能拿H5或编译结果代替。

H5使用同源接口。开发服务器仅用于本机开发，部署只提供编译后的静态文件。uni统计显式关闭。

## 已有数据库

禁止直接把不明结构的非空数据库自动baseline。先保存旧镜像、记录持久卷，再备份。

1. scripts/backup.ps1保存SQL、上传文件、本机配置及SHA256。
2. 从备份恢复独立克隆，用scripts/migration-preflight.ps1检查重复订单号、用户标识及孤立用户归属，并对照V1列结构。
3. 仅当克隆与V1一致，显式将Flyway基线设为版本1；之后执行V2、V3、V4。可使用应用环境变量SPRING_FLYWAY_BASELINE_ON_MIGRATE=true与SPRING_FLYWAY_BASELINE_VERSION=1完成这一次克隆启动，完成后移除这两个变量。
4. 检查历史订单、金额、账户及地址来源。重复记录必须人工决定如何处理，迁移不会自动删除。
5. 克隆验收通过后，在维护窗口用同一已验证流程升级目标库。失败时恢复独立备份和旧镜像。

新增长字段、索引、角色和事务表均为增量改造。utf8mb4转换保留二进制排序语义。旧购物车保留在shopping_cart，新版使用cart_item，不把旧口味格式伪装成有效新选择。历史订单不制造配额流水；地址补录仅标记LEGACY_BACKFILL。

## 备份恢复

运行backup.ps1，再restore-check.ps1。备份前后核对业务表计数及16张表校验值，写入期间变化则保留失败记录并要求重做。恢复脚本核对文件哈希、把SQL恢复到带专用标签的新临时MySQL容器、检查计数、表校验值和归属/配额约束；上传恢复至独立目录核对哈希。最后仅清理该标签容器，不操作业务持久卷。

本机备份包含密码哈希、运行密钥和收货信息，仅保留在受保护.local目录；Windows脚本限制目录访问。外部备份应存放用户控制的加密存储。

## 容量

scripts/load.ps1使用独立campuseats-load-时间戳项目，100个独立用户、10000历史订单、50000明细。先20并发预热30秒；20/100并发默认各2分钟，50并发默认10分钟。指标包括菜单、订单页、下单P95以及非预期错误、重复订单和配额一致性。

具体结果见v1-verification.md。退出码未通过阈值时保留日志，不能修改测试标准冒充通过。测试结束停止负载容器，保留数据及结果用于复核。

## 故障演练

仅在本机模拟环境运行 `./scripts/recovery-check.ps1`。脚本会创建演练订单，暂停任务调度并注入过期的处理中租约，然后更换后端进程验证退款及Outbox恢复；临时停止Redis验证降级，再停止MySQL验证就绪和业务503。finally恢复依赖容器。此过程会短暂中断本机演示服务，应保留完整日志。演练没有真实外部支付网关，不能推导真实资金退款已完成验收。
