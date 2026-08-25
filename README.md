# CampusEats

一个基于 Spring Boot + MyBatis + Redis 的前后端分离式校园外卖系统，包含管理端（admin） 与 用户端（user / C 端） 两套后台接口，支持微信登录、微信支付、WebSocket 实时消息推送、数据统计报表等能力。

---

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 2.7.3、MyBatis、MyBatis PageHelper |
| 数据存储 | MySQL、Redis |
| 安全与鉴权 | JWT（jjwt 0.9.1）、Spring MVC 拦截器 |
| 接口文档 | knife4j（Swagger UI 增强版） |
| 第三方集成 | 微信小程序登录、微信支付（wechatpay-apache-httpclient）、阿里云 OSS |
| 实时推送 | WebSocket |
| 工具库 | Lombok、Fastjson、Apache POI（报表导出）、Druid 连接池、commons-lang |
| 构建工具 | Maven（多模块工程） |

---

## 项目结构

Maven 多模块工程，三个模块依赖关系：`sky-common` ← `sky-pojo` ← `sky-server`。

```
campus-eats
├── pom.xml                         # 父 POM，统一依赖管理
├── sky-common                     # 公共模块：通用工具、常量、异常、结果封装
│   └── src/main/java/com/sky
│       ├── constant                # 常量（状态、JWT Claims、消息、密码等）
│       ├── context                 # BaseContext 线程上下文（保存当前登录用户 id）
│       ├── enumeration             # 枚举（如操作类型）
│       ├── exception               # 自定义异常体系（BaseException 及其子类）
│       ├── json                    # Jackson 序列化配置
│       ├── properties              # 配置属性（Jwt、阿里 OSS、微信）
│       ├── result                  # 统一返回（Result、PageResult）
│       └── utils                   # 工具类（Jwt、阿里 OSS、HttpClient、微信支付）
├── sky-pojo                       # 实体/传输对象模块
│   └── src/main/java/com/sky
│       ├── dto                     # 数据传输对象（登录、菜品、订单、套餐、购物车等）
│       ├── entity                  # 数据库实体（Employee、Dish、Orders、User 等）
│       └── vo                      # 视图对象（给前端/接口返回的数据）
└── sky-server                     # 服务端模块（唯一可运行的模块，含启动类）
    └── src/main
        ├── java/com/sky
        │   ├── annotation          # 自定义注解（如 @AutoFill 公共字段自动填充）
        │   ├── aspect              # AOP 切面（AutoFillAspect 自动填充创建/更新时间等）
        │   ├── config              # 配置类（OSS、Redis、WebMvc、WebSocket）
        │   ├── controller          # 控制器
        │   │   ├── admin           # 管理端接口（员工、分类、菜品、套餐、订单、报表等）
        │   │   ├── user            # 用户端接口（用户、购物车、下单、地址簿等）
        │   │   └── notify          # 支付回调通知接口
        │   ├── handler             # 全局异常处理
        │   ├── interceptor         # JWT 登录校验拦截器（admin / user 双端）
        │   ├── mapper              # MyBatis Mapper 接口
        │   ├── service             # 业务层（接口 + impl 实现）
        │   ├── task                # 定时任务（订单超时处理、WebSocket 定时消息）
        │   ├── websocket           # WebSocket 服务端
        │   └── SkyApplication.java # 启动类
        └── resources
            ├── application.yml     # 主配置
            ├── application-dev.yml # 开发环境配置（已 gitignore，不入库）
            └── mapper              # Mapper.xml（手写 SQL）
```

---

## 功能特性

### 管理端（admin）

- **员工管理**：登录、退出、分页查询、启用/禁用、编辑、修改密码
- **分类管理**：菜品/套餐分类的增删改查与分页查询
- **菜品管理**：新增、分页查询、删除、批量起售/停售、编辑；含口味（DishFlavor）管理
- **套餐管理**：套餐与菜品关联（SetmealDish）、起售/停售、分页查询
- **订单管理**：条件分页查询、各状态订单数量统计、接单、拒单、取消、派送、完成、催单（WebSocket 通知）
- **数据统计**：运营数据报表（营业额、订单、菜品销量、用户数）、导出 Excel 报表（POI）
- **工作台**：今日运营数据概览、待办事项（待接单/待派送/已派送订单数）
- **店铺营业状态**：设置/查看营业状态（Redis 存储）
- **文件上传**：阿里云 OSS 图片上传

### 用户端（user / C 端）

- **微信登录**：通过微信授权 code 换取用户身份，签发 JWT
- **浏览点餐**：按分类浏览菜品、查看套餐及所含菜品（DishItemVO）
- **购物车**：加入/删除/清空/查看购物车
- **下单支付**：提交订单、微信支付（含支付成功回调）、查看订单历史
- **地址簿**：新增、编辑、删除、查询收货地址，可设置默认地址
- **店铺状态**：查看当前店铺是否营业

### 通用/系统能力

- **JWT 双端鉴权**：admin / user 两套拦截器与鉴权逻辑
- **公共字段自动填充**：`@AutoFill` 注解 + AOP 切面，自动维护创建/更新时间、创建/修改人
- **全局异常处理**：`GlobalExceptionHandler` 统一封装错误响应
- **WebSocket 实时通信**：催单提醒、店铺状态通知
- **定时任务**：自动取消超时未支付订单
- **接口文档**：集成 knife4j，启动后可按需访问在线文档

