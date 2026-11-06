# 图片与举证存储接入

默认 `local` 后端继续使用 `campus.uploads`；生产多实例选择腾讯云 COS。商品图片保持 `/uploads/{uuid}.png`，售后举证保持 `/api/v1/evidence/{uuid}`，前端不保存 COS 临时签名地址，也不需要修改页面来切换存储。

## 腾讯云资源和参数

创建两个不同的 COS 存储桶，分别存商品图片和举证。两个桶均设置为**私有读写**，禁止公共读取及匿名写入，并启用版本控制。应用通过官方 Java SDK 上传和读取；不需要配置桶 CORS，也不向浏览器发放腾讯云密钥。

| Spring 配置                      | 环境变量建议                     | 值从哪里取得                           |
| -------------------------------- | -------------------------------- | -------------------------------------- |
| `campus.storage.backend`         | `CAMPUS_STORAGE_BACKEND`         | 演示可用 `local`；生产模板固定为 `cos` |
| `campus.storage.region`          | `CAMPUS_STORAGE_REGION`          | 存储桶地域，如 `ap-shanghai`           |
| `campus.storage.product-bucket`  | `CAMPUS_STORAGE_PRODUCT_BUCKET`  | 商品桶完整名称，包含 APPID 后缀        |
| `campus.storage.evidence-bucket` | `CAMPUS_STORAGE_EVIDENCE_BUCKET` | 举证桶完整名称，包含 APPID 后缀        |
| `campus.storage.secret-id`       | `CAMPUS_STORAGE_SECRET_ID`       | 专用 CAM 子账号或密钥身份              |
| `campus.storage.secret-key`      | `CAMPUS_STORAGE_SECRET_KEY`      | 对应 SecretKey，仅放服务端受保护配置   |

环境变量使用 Spring Boot 的 relaxed binding；也可按部署模板映射为上述属性。CAM 权限限制到两个指定桶的 `images/*` 对象，授予上传、读取对象及读取指定历史版本所需权限；上传指定对象 ACL 为 private，因此身份还需要设置对象 ACL 的权限。不要授予删除桶、修改桶策略或账号管理权限。COS 客户端使用 HTTPS，连接超时 2 秒、读取超时 5 秒，不自动重试 PUT。

COS API、Java SDK 和权限清单以[官方文档](https://cloud.tencent.com/document/product/436/65935)为准；SDK 来源为 [tencentyun/cos-java-sdk-v5](https://github.com/tencentyun/cos-java-sdk-v5)，MIT 许可。

## 文件生命周期和权限

上传仍执行 PNG/JPEG 解码、4096 边长限制、5 MiB 编码结果限制及本地临时空间预算。实际对象上传发生在业务锁事务之外。数据库 `stored_image` 保存后端、对象键、桶、版本、字节数、SHA-256、可见性和上传归属；共享预算行使多个实例共同遵守存储字节配额与商品图数量上限。

同一用户在同一订单最多保留20张待绑定举证。已达到上限的请求在解码及对象写入前拒绝，提交时在订单锁内再次检查，避免并发上传越过业务上限。

商品图片由应用同源代理读取并设置缓存头。举证首先校验当前账号及订单权限，再获取私有对象，采用 16 KiB 分块输出和 `Cache-Control: no-store`；配送员不能读取售后举证。当前店铺及人员权限继续由服务端实时检查。

带店铺路径的商品上传将已鉴权的目标 `storeId` 写入文件元数据，平台管理员代指定店上传时同样如此。新增商品或更换图片只接受该店 `READY + PUBLIC` 文件；私有举证、其他店文件、状态未确认或不存在的文件不能绑定。旧商品保持数据库中原图片 URL 不变时仍可编辑，以保留演示数据兼容；变更 URL 时执行新图片校验。默认演示图及空图片继续按既有规则使用。

对象上传、元数据提交或业务提交的网络结果不明时，不把文件删除当作回滚，也不立即释放预算。文件状态与业务关联分开判断：

| 文件状态       | 含义                                                 |
| -------------- | ---------------------------------------------------- |
| `UPLOADING`    | 已预留共享预算，尚未确认对象写入及元数据完成         |
| `UNKNOWN`      | 写入或完成确认结果不明，保留预定对象位置与摘要供核验 |
| `READY`        | 对象及元数据已确认；商品或售后关联另查业务记录       |
| `UNREFERENCED` | 已发布对象未被本次业务接受，保留待人工核查           |

若举证事务的提交回执丢失且数据库暂时也无法查询，可能保留 `READY` 对象，以免删除已经提交的举证；仍须核对 `evidence` 等业务关联。平台管理员可以调用：

```text
GET  /api/v1/platform/storage/images?state=UNKNOWN
GET  /api/v1/platform/storage/images?state=UNREFERENCED
POST /api/v1/platform/storage/images/{id}/verify
```

`verify` 接受 `UNKNOWN/UPLOADING/READY`，读取已记录对象并检查大小、SHA-256 及 COS 版本；匹配后恢复或保持 `READY`，不会重新上传、改变原内容或自动补建业务关联。`UNREFERENCED` 文件必须先核查业务归属，不能通过该接口直接恢复。检查失败保留原状态。没有自动永久删除接口或自动清理任务；运维人员处理保留对象时需另行确认恢复、回收及保留策略。

## 已有本地数据与恢复

缺少元数据的历史图片仍按原 UUID 从当前实例的本地目录读取。生产部署使用独立新库和 COS；已有员工但缺少生产初始化记录的数据库会被生产启动检查拒绝。

如需将已有本地记录迁入生产，先确定允许迁入的业务记录及初始化方案，再在维护窗口迁移对象、核对摘要、补全版本/归属元数据与共享预算，并独立恢复验收。该迁移按实际旧库单独实施。生产存储检查会拒绝 `LOCAL READY` 对象，以及未匹配 `COS + READY` 和正确可见性的商品/举证引用，迁移时逐项核对上述条件。

备份与恢复需要同时保留数据库中的文件元数据和对应对象版本。COS 版本保留期限不得短于数据库可恢复时间范围。恢复时逐项检查数据库引用、对象版本、大小和 SHA-256；本机现有备份停用设置不受本功能改变。COS 配置、HTTP 测试与真实桶验收结果分别记录。
