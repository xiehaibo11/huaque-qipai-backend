## 南北娱乐 Java 后端

Spring Boot 4、Java 21、Maven 与 PostgreSQL 17 的可运行后端骨架，包含用户、登录、JWT、刷新令牌和支付订单基础流程。

### 已实现

- 手机验证码登录、微信官方 Open SDK 服务端 code 换取和手机一键登录接口
- JWT Access Token 与可轮换 Refresh Token
- 用户身份绑定与当前用户接口
- 浙江区域玩法目录与用户最后选择持久化
- 原版证据驱动的房间游戏/规则目录、六位房号创建、加入、首局房卡结算与解散
- 原版证据驱动的 30400 金币场目录、金币上下限校验、join/matching 幂等等待态，以及 local/QA 测试自动牌局入口
- “我的比赛场”鉴权列表、六位场号创建、幂等持久化与购买房卡划入
- 每日/每周任务目录、04:00 中国时区周期、进度事件、活跃度、阶段奖励与幂等领取
- 鉴权财运状态、求财运、聚宝盆一次/五次抽取和请财神；钱包、三小时宝物、十级上限及写操作幂等由服务端事务维护
- 支付商品、订单幂等创建、订单查询
- 原生商城 70 商品目录、钱包兑换、背包、首充/每日限购和支付后幂等发货
- 版本化会员须知配置和鉴权读取接口，内容由 PostgreSQL/Flyway 管理
- 本地 Mock 支付，以及易收米支付宝 H5（固定 `payType=11`）下单与验签回调
- Flyway 数据库迁移和支付 Outbox 表
- Refresh Token、订单幂等键和 Webhook 事件的数据库并发串行化
- OpenAPI、Actuator、统一 Problem Details 错误响应
- PostgreSQL Testcontainers 端到端集成测试

微信和一键登录在 `local` 环境使用 Mock 适配器；支付保留本地 `MOCK`，生产易收米必须通过
`YISHOUMI_PAYMENT_ENABLED`、AppID 和 AppSecret 显式启用，非本地环境不会静默回退到 Mock。
生产微信登录通过 `WECHAT_LOGIN_ENABLED`、`WECHAT_APP_ID` 与 `WECHAT_APP_SECRET` 显式启用，
AppSecret 只由服务端调用微信开放平台时使用。一键登录仍保持不可用适配器。

正式手机号验证码已接入阿里云短信 SDK，生产开关默认关闭；只有签名、模板审核通过并配置
`ALIYUN_SMS_ENABLED=true` 后才会发送真实短信。

### 环境要求

- JDK 21
- Docker Desktop
- 项目自带 Maven Wrapper，无需单独安装 Maven

### 本地启动

```bash
cd /Users/mosc/Downloads/ZJYX/backend
cp .env.example .env
docker compose up -d --wait
set -a && source .env && set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

服务默认地址：

- API：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI：`http://localhost:8080/v3/api-docs`
- 健康检查：`http://localhost:8080/actuator/health`

Android Debug 构建默认连接生产 API，避免模拟器依赖未启动的本机后端。需要调试本机
8080 服务时显式执行：

```bash
./gradlew :project:host-shell:assembleDebug \
  -PnanbeiDebugApiBaseUrl=http://10.0.2.2:8080
```

### 本地登录示例

本地固定验证码默认为 `246810`，可通过 `LOCAL_OTP` 修改。

```bash
curl -X POST http://localhost:8080/api/v1/auth/otp/request \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"+8613800000000"}'

curl -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"+8613800000000","code":"246810"}'
```

登录响应包含 15 分钟 Access Token 和 30 天轮换型 Refresh Token。Android 客户端会在
Access Token 剩余时间不足 60 秒、已经过期或受保护接口首次返回 401 时，静默调用：

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refresh-token>"}'
```

刷新成功后必须同时替换本地 Access Token 和 Refresh Token；并发业务请求会合并为一次
刷新，避免旧 Refresh Token 被重复使用。临时网络错误保留本地会话，只有刷新凭据被拒绝
或刷新后的业务请求再次返回 401 时才要求重新登录。

微信登录和手机一键登录的本地 Mock credential 只需使用非空值：

```bash
curl -X POST http://localhost:8080/api/v1/auth/providers/wechat/login \
  -H 'Content-Type: application/json' \
  -d '{"credential":"local-wechat-code"}'

curl -X POST http://localhost:8080/api/v1/auth/providers/one_tap/login \
  -H 'Content-Type: application/json' \
  -d '{"credential":"local-one-tap-token"}'
```

### 区域玩法接口与建表

公开区域目录直接读取 PostgreSQL：

```bash
curl http://localhost:8080/api/v1/regions
```

登录用户保存最后选择：

```bash
curl -X PUT http://localhost:8080/api/v1/regions/selection \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/json' \
  -d '{"lobbyId":900023}'
```

`db/migration/V2__region_catalog.sql` 会创建并导入：

- `region_cities`：11 个地图城市与标记坐标
- `region_lobbies`：18 个逆向恢复的真实 lobbyID 和显示顺序
- `user_region_selections`：登录用户最后选择

生产部署启动应用时 Flyway 会自动执行该脚本；也可以用标准 PostgreSQL 客户端预审脚本，
不需要额外创建模拟数据或手工补表。

### 每日与每周任务 API

以下接口均要求 Bearer Access Token：

- `GET /api/v1/missions`：返回可用任务页摘要。
- `GET /api/v1/missions/pages/{pageCode}`：返回 DAILY/WEEKLY 当前周期、剩余时间、活跃度、
  阶段奖励、按“可领取→进行中→已领取”排序的任务和权威钱包。
- `POST /api/v1/missions/tasks/{taskCode}/claim`：领取已完成任务，必须携带 `Idempotency-Key`。
- `POST /api/v1/missions/pages/{pageCode}/milestones/{target}/claim`：领取已解锁阶段奖励，
  必须携带 `Idempotency-Key`。

`V22__daily_missions.sql` 创建任务页、任务定义、阶段定义、奖励、用户周期进度、阶段领取和幂等请求表。
周期按中国时区每日 04:00、每周一 04:00 切换，数据库仍保存 UTC。登录事件已接入查询链路；具体游戏服务应调用内部 `MissionProgressService.record(...)` 上报对局、胜利和互动道具事件，不向客户端公开修改进度端点。该实现参考原版客户端行为，但属于南北娱乐自建后端。

### 定时登录有礼 API

以下接口均要求 Bearer Access Token：

- `GET /api/v1/time-login/state`：返回当前活动自然日的时段列表（含 `rewardFlag` 原版字面量
  `CanReward/Rewarded/CanSupple/NotInTime/OverTime`）、`goldOver` 金币上限、`supplementCnt`
  补领次数、`daySecond` 与 `serverTime`（供客户端跑倒计时），以及八格转盘的进度与奖池和权威钱包。
- `POST /api/v1/time-login/claims`：领取某个时段奖励，必须携带 `Idempotency-Key`。
- `POST /api/v1/time-login/wheel-draws`：抽一次转盘，必须携带 `Idempotency-Key`。

两个写端点都返回原版 `ClaimFlag` 字面量（`Success`/`Not_In_Time`/`Already_Claim`/`Gold_Over`/
`Wheel_Cnt_Lack`/`Failed`），失败分支不改动钱包。活动自然日按 `Config.lua` 的 `EVENING = 82800`
在 23:00 切分，跨零点的早间时段因此与次日归为同一天。转盘中奖格由服务端按权重选出并随
`wheelSliceIndex` 返回，概率不下发、客户端不参与。

`V36__time_login_act.sql` 创建活动、时段、转盘奖池、领取记录与幂等表，并用两个部分唯一索引
保证「同一活动自然日每个时段只能领一次、转盘只能抽一次」。迁移里的三段时段、
1000/1000/1200 金币、5 万金币上限、3 次解锁与八格奖池取自 1.5.4 实机截图的单次观测，
属南北娱乐自建配置，不是原版服务端事实；证据与未闭合项见
`android/docs/ORIGINAL-TIME-LOGIN-ACT-EVIDENCE.md`。

### 创建房间与房卡结算

下列接口均要求 Bearer Access Token：

- `GET /api/v1/rooms/games?lobbyId=...`：返回当前大厅已启用的建房游戏。
- `GET /api/v1/rooms/rule-config?lobbyId=...&gameId=...`：返回服务端权威规则配置。
- `POST /api/v1/rooms`：必须携带 `Idempotency-Key`，服务端重新校验分类、可见/禁用联动、
  选择节点、人数、局数、支付方式和 centi 房卡成本；创建时只预检余额，不扣房卡。
- `GET /api/v1/rooms/{roomNumber}`、`POST .../join`：仅参与者可查询；加入受房间锁、容量和
  AA 每人余额预检约束。
- `POST .../first-round`：仅房主可调用且必须满员；ALL 从房主扣一次，AA 在同一事务内按
  参与者逐人扣除；余额不足整笔回滚，重复调用不重复扣款。
- `POST .../dissolve`：仅房主可调用；首局前关闭不扣卡，首局后关闭不退款，关闭后可再建房。

房号由 PostgreSQL 序列生成六位数字并由唯一约束兜底；同一房主的建房通过 advisory lock
串行，同一幂等键的并发请求只保留一间房。`V21__complete_box_room_chain.sql` 完成房间参与者、
结算流水与 900038 规则，`V23__seed_taizhou_box_rooms.sql` 写入 900023 的 16 个可创建游戏。
这些服务只实现房间创建和房卡账务，不等同于已经恢复具体游戏牌局服务器。

### 金币场目录、匹配等待与 QA 自动牌局

下列接口均要求 Bearer Access Token：

- `GET /api/v1/gold-rooms/games?lobbyId=...`：返回当前大厅已启用的金币场玩法。
- `GET /api/v1/gold-rooms/games/{gameId}?lobbyId=...`：返回原版 `roomFlags` 顺序和
  `roomLevelInfos` 等价档位；底分/准入文案由 Android 按 `ChooseRoom.lua` 渲染。
- `POST /api/v1/gold-rooms/games/{gameId}/join`：必须携带 `Idempotency-Key`，请求体为
  `{"lobbyId":900023,"roomNameFlag":3}`。服务端按权威钱包金币校验 `minRich/maxRich`：
  余额不足返回 `GOLD_LOW_LIMIT`，超过低档上限返回 `GOLD_HIGH_LIMIT`；默认通过时返回
  `GOLD_QUEUING`、`MATCHING`、`DISPATCH_QUEUE`、匹配票据和档位原始数值；local/QA 开关
  `nanbei.gameplay.qa.enabled=true` 且金币场映射到 30109 时，返回
  `GOLD_QA_AUTO_ROUND_READY`、`QA_AUTO_MATCH`、六位 `roomNumber` 和 `autoGameplay=true`。

该接口只实现原版 50 模式进入 dispatch queue 的南北娱乐现代兼容边界，并把结果幂等记录在
`gold_room_join_operations`。local/QA 自动牌局会创建测试房间、`game_sessions`、测试假人座位和
QA 事件链，方便 Android 渲染牌桌；该能力是测试/调试专用，不表示台州麻将金币场生产队列撮合、
生产洗牌发牌、摸打动作、胡牌或结算已经闭合。

### 台州麻将牌局底座

下列接口均要求 Bearer Access Token，当前只接受 `gameId=30109`：

- `POST /api/v1/game-sessions/{roomNumber}`：房主首次创建会话；已存在会话可由房间成员幂等打开。
- `GET /api/v1/game-sessions/{roomNumber}`：返回当前成员的等待桌快照、固定座位和从权威
  `game_rule` 解析出的 `autoReady`。
- `POST /api/v1/game-sessions/{roomNumber}/commands`：以 `Idempotency-Key` 和 `expectedRevision`
  提交 `READY`/`UNREADY`/`START_ROUND`；local/QA 开关启用时可提交 `QA_AUTO_ROUND` 生成测试假人自动牌局事件链；
  同一键不同请求拒绝，过期 revision 拒绝。
- `GET /api/v1/game-sessions/{roomNumber}/events?afterRevision=...`：按 revision 和 event order
  返回当前成员可见的已提交事件，用于 Android 增量恢复。

会话、座位、命令和事件由 `V28__gameplay_foundation.sql` 持久化；`V31__gameplay_seat_score.sql`
为现有与新增座位增加服务端权威牌局分数，南北娱乐当前初始值为 1000。座位资料与规则摘要来自真实房间和
用户数据；30109 新房按仓库内台州麻将 Lua 的规则顺序生成摘要，旧房摘要为空时在牌局快照读取阶段按
权威 `game_rule`、人数和局/圈数补算。原版规则启用 `autoReady='1'` 或
`UserRule='AutoReady=true;'` 时，一次 `READY` 会在同一 revision 内把全部已入座席位收敛为准备并按座位号写入有序事件；
这是南北娱乐为无独立客户端的测试席位实现的现代服务端收敛，不按测试昵称识别机器人。
正式 `START_ROUND` 已接入南北娱乐 `SERVER_AUTHORITY` 服务端权威回合：只允许房主在满员、全员
ready、revision 匹配且无测试假人的房间触发，成功后持久化牌墙、发牌、补花、摸打、动作 offer、
吃碰杠胡和结算事件；手牌以座位私有事件隔离。`QA_AUTO_ROUND` 仍是 local/QA 测试/调试能力，
会补测试假人并写入 `qaDisclosure`，不得与生产撮合混用。当前牌局引擎和初始分数都是南北娱乐
自研服务端规则，不得描述成恢复出的原版服务端源码或完整原版服务端算法。等待桌 `EARLY_START`
（房主、WAITING 阶段受理，QA 链路补测试假人视为同意；原版多人同意流未实现）与结算后 `NEXT_ROUND`
（`ROUND_RESULT` 且局数未尽受理，局尽返回 `GAME_ACTION_NOT_ALLOWED`）复用同一幂等键 +
expectedRevision 命令链路。

### 我的比赛场接口

- `GET /api/v1/match-arenas`：返回当前用户仍有效的比赛场成员列表。
- `POST /api/v1/match-arenas`：必须携带 `Idempotency-Key`；服务端校验当前地区、900023
  模式与消耗组合、备注、每日上限、`LEGACY` 未分级语义和原版隐藏配置；自动补卡档位不得
  超过创建时的购买房卡余额。
- 创建在同一事务内写入 `match_arenas`、房主成员关系和初始划卡流水；只允许从
  `player_wallets.room_card_centi` 划入购买房卡，不扣赠送房卡。
- 六位场号由 PostgreSQL 非循环序列分配；同一房主创建使用 advisory lock 串行，同一幂等键
  重试返回同一比赛场。原版未确认未分级比赛场的服务端数量上限，当前 `LEGACY` 每人最多 10 个
  是南北娱乐现代后端保护值；该后端不是原版服务端源码。

### 真实头像 API、BYTEA 与后续对象存储切换

登录后的头像链路直接使用真实用户、真实 API 和 PostgreSQL，不写模拟头像数据：

- `PUT /api/v1/profile/avatar`：接收 JPEG/PNG，服务端校验后中心裁剪并压缩为 512×512 JPEG。
- `GET /api/v1/avatars/{avatarKey}`：鉴权读取头像，返回私有缓存头、ETag，并支持 `If-None-Match`/304。
- `db/migration/V4__player_avatar.sql`：创建 `player_avatars`，以 PostgreSQL `BYTEA` 保存图片，
  同时记录 SHA-256、媒体类型、尺寸和更新时间。
- `AvatarBlobStore`：业务层只依赖存储接口；当前实现是 `PostgresAvatarBlobStore`，后续可新增
  OSS/S3 实现而不修改 Controller、头像处理和用户资料流程。

Android 客户端通过系统图片选择器读取真实照片，在本地按 EXIF 方向校正、中心裁剪和压缩后上传；
读取时使用内存缓存、应用私有磁盘缓存和 ETag。用户没有上传头像时使用恢复的原版默认头像素材。

### 测试

Docker Desktop 运行时执行：

```bash
cd /Users/mosc/Downloads/ZJYX/backend
./mvnw verify
```

该命令会由 Spring 测试上下文管理一个临时 PostgreSQL 容器，并在四个集成测试类之间
复用同一数据库连接配置。测试覆盖迁移、区域目录、用户区域选择、三类登录、JWT、
刷新令牌轮换、支付订单幂等、回调验签与 Outbox 入库。

并发集成测试会制造真实 PostgreSQL 锁等待并验证：

- 同一个旧 Refresh Token 并发轮换时只有一个请求成功，复用请求触发 token family 撤销策略。
- 同一用户和 `Idempotency-Key` 的并发下单只创建一个订单，两个请求返回同一订单。
- 同一 `provider + eventId` 的并发 Webhook 全局串行；相同原始报文只履约一次，事件号相同但
  报文摘要不同的回调会被拒绝。

### 阿里云正式短信

服务使用阿里云默认凭据链，不在配置文件中保存 AccessKey。生产 Compose 允许
`ALIBABA_CLOUD_ACCESS_KEY_ID` 和 `ALIBABA_CLOUD_ACCESS_KEY_SECRET` 为空，从而继续尝试
OIDC、Profile 或 ECS RAM Role 等默认凭据来源；推荐给运行环境配置 RAM 角色。本地临时验证
也可显式使用环境变量：

```bash
export ALIBABA_CLOUD_ACCESS_KEY_ID=...
export ALIBABA_CLOUD_ACCESS_KEY_SECRET=...
export ALIYUN_SMS_ENABLED=true
export ALIYUN_SMS_REGION_ID=cn-hangzhou
export ALIYUN_SMS_SIGN_NAME=南北娱乐
export ALIYUN_SMS_TEMPLATE_CODE=SMS_511015069
```

当前正式模板内容计划为：

```text
您正在登录南北娱乐，验证码为${code}，5分钟内有效，请勿泄露给他人。
```

没有已审核的 `TemplateCode` 时必须保持 `ALIYUN_SMS_ENABLED=false`。正式环境同时需要配置
至少 32 字节的 `JWT_SECRET`、PostgreSQL 连接与 HTTPS 反向代理。

### 支付与 Outbox 当前边界

当前保留 `local` Profile 的 `MOCK` 适配器，并实现生产 `YISHOUMI` 适配器。易收米下单固定使用
支付宝 H5 `payType=11`；商品名称和金额只读服务端数据库。下单严格按官方 PHP SDK 使用
`application/json`，其中 `total`、`time`、`payType` 保持 JSON 数字类型；异步通知从原始 JSON
读取并校验 `hash`。服务端异步通知是支付成功的唯一权威
依据，官网和 Android 回跳只查询订单状态，不能开通会员。生产联调确认易收米建单成功响应的
`sign` 回显原请求签名，Provider 使用原请求字段验签，并继续核对商户订单号和 HTTPS 支付地址。

支付成功与唯一 `PAYMENT_SUCCEEDED` Outbox 事件会在同一数据库事务中写入；`SXVIP_*` 商品由
会员履约处理器幂等激活或顺延会员并发放购买礼包；商城人民币商品由商城履约处理器更新钱包
或背包。首充等限购商品在创建支付订单前通过数据库事务锁和服务端计数校验，客户端不能绕过；
其他未定义履约处理器的支付商品仍不得仅记录日志或直接填写 `published_at` 来冒充真实发货。

### 原生商城接口

以下接口均要求 Bearer Access Token：

- `GET /api/v1/shop/catalog`：返回 10 个分类、70 个商品和房卡/金币/钻石/礼券余额；记牌器页提供原版七档商品。
- `POST /api/v1/shop/exchanges`：请求体为 `{"productCode":"..."}`，必须携带
  `Idempotency-Key`；允许免费、房卡、钻石和礼券商品，人民币商品返回 `SHOP_PAYMENT_REQUIRED`。
- `GET /api/v1/shop/inventory`：返回互动、道具、装扮等非钱包资产数量。

`V16__native_shop.sql` 创建商品、奖励明细、购买记录和背包表，
`V17__add_native_shop_seven_day_membership.sql` 把既有 `SXVIP_7_DAYS` 支付商品加入原生商城，
`V19__add_original_recorder_products.sql` 增加 2 小时、1/3/7 天与 1/10/20 局记牌器商品。
商城显示可先使用 Android 内置的只读原版证据目录，但余额、限购、扣款和发货只能采用这些接口的服务端结果。首充礼包支付成功后
发放 100 钻石、20000 金币和 1 天记牌器；每日 6 元礼包按当日第 1/2/3 次分别发放对应档位金币。

### 会员须知接口

`GET /api/v1/membership/notice` 要求 Bearer Access Token，返回 `version`、原版四条须知、
权益变更说明、第一方协议元数据和 `updatedAt`。权威内容存放在
`membership_notice_configs`，由 `V18__membership_notice.sql` 创建并初始化；Android 打包的
同文案仅用于网络失败时展示，不替代服务端配置。

### 微信正式登录

Android 客户端使用微信官方 Open SDK 获取一次性授权 code，并调用：

```text
POST /api/v1/auth/providers/wechat/login
```

生产后端固定访问微信官方 `https://api.weixin.qq.com/sns/oauth2/access_token`，有 `unionid`
时优先作为稳定身份，没有时使用 AppID 命名空间隔离的 `openid`。微信 access token、
refresh token 和一次性 code 不写入数据库或日志，客户端最终只收到南北娱乐自己的
Access/Refresh Token。

微信接口的成功响应可能使用 `application/json` 或 `text/plain`。后端先按字符串读取，
再通过服务端 JSON 解析器只提取 `openid`、`unionid`、`errcode` 和 `errmsg`；响应原文、
微信 access token、微信 refresh token、AppSecret 和一次性 code 均不得写入日志。

微信开放平台审核完成并取得凭据前保持：

```dotenv
WECHAT_LOGIN_ENABLED=false
WECHAT_APP_ID=
WECHAT_APP_SECRET=
```

审核通过后将真实 AppID/AppSecret 只写入服务器 `/opt/nanbei/.env`，把开关改为 `true`，
然后只重建后端。Android APK 只注入公开 AppID，不得包含 AppSecret。

### 运营商一键登录

生产配置保持显式关闭：

```dotenv
ALIYUN_ONE_TAP_ENABLED=false
ALIYUN_ONE_TAP_REGION_ID=cn-hangzhou
ALIYUN_ONE_TAP_ENDPOINT=dypnsapi.aliyuncs.com
```

只有阿里云号码认证方案、`dypns:GetMobile` 权限、Android 三份官方 AAR 和 Scheme Key
全部就绪后，才把服务器开关改为 `true`。SDK 不可用时客户端回退到短信验证码登录；
登录成功后的长期免验证码状态由 Refresh Token 静默轮换维持。

### 生产部署

生产环境使用 Docker Compose 运行 PostgreSQL、Spring Boot、Vue 3 官网和 Caddy，公网入口为
`https://nanbeiyule.com`、`https://www.nanbeiyule.com` 与 `https://api.nanbeiyule.com`。
PostgreSQL 5432 和后端 8080 只在 Docker 内部网络开放，
宿主机只发布 Caddy 的 80/443；PostgreSQL 仅连接隔离内网，后端额外连接不发布端口的
`backend_egress` 出站网络访问阿里云短信 API；Caddy 自动申请和续期公开 TLS 证书。

服务器部署目录为 `/opt/nanbei`，后端和前端源码分别位于
`/opt/nanbei/backend` 与 `/opt/nanbei/frontend`。仓库根目录中的
`.env.production.example` 只列出变量名和非敏感
示例；真实密码、JWT 密钥和阿里云凭据只能写入服务器 `/opt/nanbei/.env`，并设置权限 `600`。

首次部署或升级：

```bash
cd /opt/nanbei
docker compose --env-file .env -f compose.production.yml config --quiet
docker compose --env-file .env -f compose.production.yml up -d --build --wait
```

检查容器、公开健康检查和核心 API：

```bash
cd /opt/nanbei
docker compose --env-file .env -f compose.production.yml ps
docker compose --env-file .env -f compose.production.yml logs --tail=200 backend caddy
curl --fail --silent --show-error https://api.nanbeiyule.com/actuator/health
curl --fail --silent --show-error https://api.nanbeiyule.com/api/v1/regions
curl --fail --silent --show-error https://nanbeiyule.com/
curl --fail --silent --show-error https://nanbeiyule.com/privacy
```

部署前保留上一版源目录或镜像标签。需要回滚时恢复上一版文件，然后重新执行
`docker compose --env-file .env -f compose.production.yml up -d --build --wait`。排障时不得
输出或复制 `.env` 内容。

### 停止本地数据库

```bash
cd /Users/mosc/Downloads/ZJYX/backend
docker compose down
```

---

## 仓库导航（南北娱乐全平台）

| 端 | 仓库地址 |
| --- | --- |
| 后端（Spring Boot / Java 21 / PostgreSQL） | https://github.com/xiehaibo11/huaque-qipai-backend |
| 前端官网（Vue 3 / TypeScript / Vite） | https://github.com/xiehaibo11/huaque-qipai-frontend |
| 安卓客户端（Android，架构对齐浙江游戏大厅） | https://github.com/xiehaibo11/huaque-qipai-android |
| UI 设计源（PSD 源文件 / 生图方案，Git LFS） | https://github.com/xiehaibo11/huaque-qipai-ui |
| 浙江游戏大厅逆向资料（原版设计证据） | https://github.com/xiehaibo11/zhejiang-game-hall |

克隆任意一端后，按上表地址补齐其余仓库即可组成完整工作区；各仓库均为私有仓库，需要账号 xiehaibo11 授权访问。
