# 源码与第三方许可

项目自有源码、文档、演示 SVG、工具链 QR 夹具和演示截图以根目录 [MIT License](../LICENSE) 发布，版权人为 `W205614`。维护者于 2026-11-07 确认源码为自行实现、开发中仅参考教程思路，素材为其自有内容，并授权采用 MIT；第三方依赖不因此改变许可证。

## 来源与第三方组件

项目没有把 `node_modules`、第三方仓库或宿主机工具包纳入 Git 源码。前端依赖由 lockfile 固定；`tooling/` 中保留对 DCloud 编译器的适配说明、受控补丁及版本摘要，修改范围见[工具链说明](../frontend/client/tooling/README.md)。

| 范围           | 许可与分发记录                                                                                                                                        |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Java 运行依赖  | 构建生成 `campuseats-server/target/bom.json`，服务镜像保留 `/app/sbom.cdx.json`；嵌套 JAR 保留上游 LICENSE、NOTICE 和版权信息                                |
| 管理台与 H5    | Vite 根据实际打包依赖生成 `THIRD_PARTY_LICENSES.md`，随各自静态目录分发，可从 `/admin/THIRD_PARTY_LICENSES.md` 和 `/app/THIRD_PARTY_LICENSES.md` 获取 |
| 小程序构建     | 在 `frontend/client/dist/build/mp-weixin/` 中保留同名许可清单；微信平台、SDK 和外部服务条款仍分别适用                                                 |
| 开发与构建工具 | 以 npm 发行包的 LICENSE/NOTICE 和锁文件为准；例如 Lightning CSS 为 MPL-2.0，caniuse-lite 数据为 CC-BY-4.0，均不将项目自有源码改为其许可证             |
| 容器基础组件   | MySQL、Redis、Nginx、JRE 与操作系统包分别适用其上游许可；项目 MIT 不覆盖这些组件。重新分发完整镜像时保留相应通知及适用的源码获取方式                  |

2026-11-07 对两端锁文件与本机安装包核对：`exif-parser@0.1.12` 的包元数据未声明许可，但 `LICENSE.md` 为 MIT；`qrcode-terminal@0.12.0` 的 `LICENSE` 为 Apache-2.0。本地 `@campuseats/fast-glob-compat` 属项目自有适配代码。依赖的完整版本与实际分发内容以目标提交的构建清单为准，不以此文档代替逐组件通知。

DCloud 的开源部分采用 Apache-2.0，内嵌 Runtime/SDK 的权利仍归其所有；如扩展到原生 App 或使用闭源服务，还需遵守 [uni-app 使用许可](https://dcloud.io/license/uni-app.html)和对应产品条款。当前项目构建目标为 H5 与微信小程序。

Redis 7.4.12 使用 RSALv2 / SSPLv1 双许可，不能视为项目 MIT 的一部分。Redis 镜像保留该版本 `LICENSE.txt` 及 Docker 上游入口脚本的 BSD 版权许可，位于 `/usr/local/share/licenses/redis/`。公开 Packages 仅分发 Java、Web 应用镜像，不向 GHCR 重新发布 MySQL、Redis 镜像。重新分发或将 Redis 作为服务提供时，须按上游条款核对适用条件。

## 应用镜像与对应源码

从 v1.0.3 开始，Java、Web 应用镜像可由独立的手动推广工作流发布至 `ghcr.io/w205614/campuseats-server` 与 `ghcr.io/w205614/campuseats-web`。推广读取成功 master CI 的同一制品，只重新标记和推送，不重建镜像；`v<版本>` 和 `sha-<完整提交>` 为版本入口，精确身份使用 registry digest。公开可见性及实际拉取结果按发布回执核对。

公开镜像包含基础系统及第三方运行组件。CI 根据实际 APK 数据库中的 origin、版本、aports commit，采集该提交的 APKBUILD、补丁、安装脚本和 SHA-512 验证的源文件；不使用当前 aports 分支代替。另采集匹配 Temurin 版本的源码和构建脚本、JRE legal/NOTICE、Nginx 源码、Java 运行依赖的 sources JAR/POM 和嵌套 JAR 中的许可证/NOTICE。源码采集需要 Python 3.11+ 和上游网络，仅用于制品交付，不增加应用运行依赖。

这些材料归档为 `runtime-sources.tar.gz`，随 CI 制品保存并由 release-manifest 绑定摘要；发布 GHCR 前须将同一归档附在相应 GitHub Release，供接收者获取对应源码、补丁、构建说明及原始许可。项目的应用源码和镜像 Dockerfile 使用同一 Release 标签。归档内 `inventory.json` 记录实际包与来源，许可证仍分别适用；MIT 的 OCI 标签仅描述项目自有应用，不重新许可基础组件。LGPL 库保持动态链接，使用者可以替换它们。

## 维护要求

引入依赖或素材时记录来源、版本和授权条件，保留已有版权、许可和 NOTICE。分发前核对生成的前端许可文件和 Java SBOM；不手工编辑生成文件。修改或直接复制第三方源码时，还须遵守该组件的修改说明及适用的源码提供要求。

参考 [MIT 正文](https://opensource.org/license/mit)、[Apache-2.0 分发条款](https://www.apache.org/licenses/LICENSE-2.0)及 [MPL 官方说明](https://www.mozilla.org/en-US/MPL/2.0/FAQ/)。
