# YunX 优化方案

## 1. 目标与原则

本方案基于当前 YunX 的业务结构制定，目标不是盲目增加抽象，而是降低多平台协议变化带来的维护成本，同时提升下载可靠性、账号安全性和问题定位效率。

优化目标：

- 解析、浏览、转存、取链、下载流程边界清晰。
- 新增网盘时尽量只增加平台适配代码，不复制整套 UI 和 ViewModel。
- 下载任务在进程重建、网络抖动和 CDN 行为异常时保持状态准确。
- Cookie、Token、Referer 等敏感数据不出现在日志、普通缓存和错误提示中。
- 所有关键行为都有可重复的单元测试、集成测试或真机验收步骤。
- 平台接口失败时能够区分认证失效、限流、提取码错误、网络错误和协议变更。

优化原则：

1. 先修正确性和数据安全，再做性能和抽象。
2. 先提取稳定的业务边界，再提取重复实现。
3. 平台差异必须显式建模，不通过大量隐式 `when` 分支传播。
4. 网络层不直接操作 UI 状态，UI 不直接拼接平台请求参数。
5. 不以降低平台检测能力或规避平台处置为优化目标，只做稳定、透明、合规的客户端治理。

## 2. 当前问题概览

### 2.1 架构问题

- `MainScreen` 负责依赖组装、页面导航、权限申请、更新检查、电池优化引导和多个登录覆盖层，职责过重。
- `ResolveViewModel` 仍然包含六个平台路由、目录状态、多选、批量下载、批量转存和清理逻辑。
- `CloudFileSource` 已建立，但部分调用仍通过具体 API 和平台分支完成，抽象尚未成为唯一入口。
- 分享解析和个人网盘浏览使用了不同的状态模型与调用路径，公共能力仍有重复。
- 平台 API 的错误通常被包装为普通 `IllegalStateException`，上层难以可靠分类。

### 2.2 稳定性问题

- 平台接口返回结构变化只能在运行时暴露。
- 分享会话、临时转存文件和下载任务之间的生命周期关联主要靠内存回调维护。
- 下载完成清理失败后主要依赖下次启动清扫，缺少统一的待清理任务记录。
- 前台服务由下载管理器计数驱动，进程被杀后只能将任务置为暂停，不能自动恢复。
- 关键的 Repository 和 ViewModel 流程缺少完整行为测试。

### 2.3 性能问题

- 多平台首页、配额、账号状态可能同时触发凭证解密和网络请求。
- 下载任务进度和列表状态更新仍需要避免不必要的全量重组。
- 目录列表可能一次性拉取多页并全部放入内存。
- 批量下载和批量转存以串行循环为主，大量文件时耗时较长，且缺少统一的限流和取消抽象。
- 网络客户端、请求超时和连接池策略缺少统一的可观测配置。

### 2.4 安全与隐私问题

- 下载任务需要持久化请求头，其中可能包含长期有效的 Cookie 或 Referer。
- 部分平台协议参数、错误信息和 URL 仍需持续检查是否进入日志。
- 认证备份拥有跨平台集中凭证，泄露影响范围较大。
- WebView Cookie 的生命周期、域名清理和页面导航策略需要各平台保持一致。

## 3. 优先级与阶段安排

| 阶段 | 优先级 | 主要内容 | 目标 |
|---|---|---|---|
| Phase 0 | P0 | 基线、日志、关键回归测试 | 建立可验证的改造起点 |
| Phase 1 | P0 | 下载和认证状态可靠性 | 降低任务损坏、状态错误和凭证泄露风险 |
| Phase 2 | P1 | 平台适配边界统一 | 减少 `when` 分支和重复业务代码 |
| Phase 3 | P1 | 网络与下载性能优化 | 提升大文件和批量任务体验 |
| Phase 4 | P1 | 可观测性和错误治理 | 缩短故障定位时间 |
| Phase 5 | P1 | 文案资源化与国际化 | 消除 UI 硬编码，支持多语言和统一文案治理 |
| Phase 6 | P2 | 工程化和持续演进 | 降低新增平台和版本发布成本 |

每个阶段都应保持可编译、可安装，并独立完成验证，不建议将所有阶段合并为一次大重构。

### 当前进度

截至 `2026-08-29`，已完成 Phase 1 中的两项实施任务：

| 状态 | 任务 | Commit | 说明 |
|---|---|---|---|
| 已完成 | 持久化下载临时清理任务 | `616c914` | 新增 `download_cleanup` 表、DAO、加密清理凭证和启动重试机制 |
| 已完成 | 抽取并测试下载状态机 | `b07c213` | 新增状态机、条件状态更新和状态迁移单元测试 |
| 已完成 | 完善下载失败和保存失败路径 | 本次提交 | 新增失败分类，区分网络、链接失效、存储、完整性和不支持类型，并仅对网络错误自动重试 |
| 已完成 | 统一敏感日志脱敏 | 本次提交 | 统一处理 URL、键值凭证、JSON 凭证和异常消息，覆盖下载、保存、HLS 和日志导出路径 |
| 已完成 | 抽取解析平台上下文 | 本次提交 | 集中 Repository、凭证刷新、根目录和平台名称路由；平台协议细节仍由各自 Repository 负责 |
| 部分完成 | 统一凭证类型 | 本次改动 | 解析平台上下文使用 `CloudCredential.Cookie` / `AccessToken`；平台 API 的历史 String 参数仍待后续逐步迁移 |
| 已完成 | 显式建模平台能力 | 本次提交 | `CloudCapabilities` 声明分享转存、临时转存取链、文件夹下载和分享视频预览能力，并接入解析平台上下文 |
| 已完成 | 统一解析页转存路由 | 本次提交 | 单项和批量转存统一通过 `ShareResolveRepository.transferFile`，批量根目录读取平台能力，并正确统计失败结果 |
| 已完成 | 统一解析页下载请求策略 | 本次提交 | 下载请求头由解析平台上下文提供，移除入队平台分支，并让弹窗关闭和下载完成清理使用取链时的平台上下文 |
| 已完成 | 加密持久化下载请求头 | 现有实现 | 请求头通过 Android Keystore 加密入库，旧明文任务读取后自动迁移，任务删除时释放内存缓存 |
| 已完成 | 普通文件 SHA-256 完整性校验 | 本次提交 | 任务提供 SHA-256 时在保存前流式校验，不匹配归类为完整性失败且不保存文件；无哈希时保留长度校验 |
| 已完成 | 统一 `CloudFileSource` 使用入口 | 本次提交 | 六个平台个人网盘的列表、取链、文件操作和分享均已迁移到各自 FileSource，ViewModel 不再直接调用平台 API |
| 部分完成 | 建立统一错误分类 | 本次提交 | 新增类型优先的 `YunxError` 分类并接入个人网盘公共加载路径；平台提取码、限流和协议变化异常仍待网络层类型化 |
| 部分完成 | Room Migration 和 DownloadManager 回归测试 | 本次改动 | 增加 v13→v14 Android SQLite migration 回归测试；DownloadManager 仍需补充可注入 DAO/Context 的集成测试基础设施 |

验证说明：当前环境未配置 `JAVA_HOME`，且找不到 `java` 命令，因此 `./gradlew testDebugUnitTest` 尚未成功执行。上述两项标记为“已完成”表示代码改造和测试代码已经提交，不表示 CI 或本机 Gradle 验证已经通过。

## 4. Phase 0：建立基线

### 4.1 固化构建与质量基线

保留现有 CI：

```text
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

建议增加：

- `detekt` 或同类 Kotlin 静态检查。
- `ktlint` 或项目统一格式检查。
- Release 构建验证。
- Room Migration 测试。
- 测试报告和 APK 构建产物归档。

验收标准：

- Pull Request 必须通过编译、单测和静态检查。
- 所有数据库版本升级必须有迁移测试。
- 静态检查新增问题数量为零，历史问题单独登记。

### 4.2 建立关键指标

建议在不记录凭证和完整 URL 的前提下统计：

- 分享解析成功率。
- 各平台列表请求成功率。
- 获取直链成功率。
- 下载任务完成率。
- Range 降级次数。
- HLS 加密流拒绝次数。
- 下载保存失败次数。
- 登录失效次数。
- 平均首个文件列表耗时。
- 平均获取直链耗时。

初期可以只写入脱敏本地日志，后续如接入远程统计必须单独评估隐私和用户授权。

## 5. Phase 1：下载与认证可靠性

### 5.1 将下载任务生命周期从内存回调升级为持久化关系【已完成】

当前 `DownloadLink.cleanupDirFid` 和 `DownloadManager.taskCallbacks` 主要依赖内存。进程被杀后，下载任务仍可恢复，但临时云端目录清理回调会丢失。

当前实现：

1. 新增独立的 `download_cleanup` Room 表，保存任务 ID、平台、资源 ID、创建时间和加密凭证。
2. 入队时将夸克临时目录信息与下载任务一起写入 Room。
3. 下载成功或删除任务时尝试执行持久化清理。
4. 清理成功后删除清理记录，清理失败则保留记录。
5. 应用启动时扫描并重试遗留清理记录。
6. 数据库从 v13 迁移到 v14。

当前代码位置：

- `app/src/main/kotlin/com/yunx/app/data/db/DownloadCleanupEntity.kt`
- `app/src/main/kotlin/com/yunx/app/data/db/DownloadCleanupDao.kt`
- `app/src/main/kotlin/com/yunx/app/data/download/DownloadManager.kt`
- `app/src/main/kotlin/com/yunx/app/data/download/DownloadManagerHolder.kt`

原计划：

1. 在下载任务表增加可选的清理信息：

```text
cleanupPlatform
cleanupResourceId
cleanupState
```

2. 入队时将清理信息和任务一起写入 Room。
3. 下载成功后先标记任务完成，再执行清理。
4. 清理成功后更新 `cleanupState`。
5. 应用启动时扫描未完成清理任务并重试。
6. 清理失败不影响用户已保存文件，也不阻塞下载任务状态。

这样可以解决“取链成功、进程被杀、云端临时文件永久残留”的问题。

### 5.2 明确下载任务状态机【已完成】

当前已将状态迁移约束集中到纯 Kotlin 对象 `DownloadTaskStateMachine`，并由 `DownloadManager` 的状态写入路径统一校验：

```text
PENDING      -> DOWNLOADING / PAUSED / FAILED
DOWNLOADING  -> COMPLETED / PAUSED / FAILED
PAUSED       -> DOWNLOADING / DELETED
FAILED       -> DOWNLOADING / DELETED
COMPLETED    -> DELETED
```

所有非法状态迁移会被拒绝；DAO 的状态更新同时使用当前状态条件，避免并发下载回调覆盖暂停或完成状态。

已补充测试：

- 正常 `PENDING -> DOWNLOADING -> COMPLETED` 流程。
- `DOWNLOADING <-> PAUSED` 暂停恢复流程。
- `FAILED -> DOWNLOADING` 失败重试流程。
- `COMPLETED` 和其他非法回退迁移。
- `requireTransition` 对非法迁移抛出异常。

当前代码位置：

- `app/src/main/kotlin/com/yunx/app/data/download/DownloadTaskStateMachine.kt`
- `app/src/test/kotlin/com/yunx/app/data/download/DownloadTaskStateMachineTest.kt`

### 5.3 统一下载凭证策略【已完成】

当前请求头中可能保存 Cookie。建议按平台区分：

- 必须长期保存的请求头：加密存入任务。
- 可以重新获取的请求头：恢复任务时通过凭证 Provider 动态生成。
- 只在取链阶段使用的短期参数：不写入下载任务。

优先减少任务表中的长期 Cookie。对于必须保存的凭证，增加：

- 加密字段版本。
- 加密失败后的明确任务错误。
- 用户注销时是否清理关联任务凭证的策略。
- 任务删除时同步清理内存缓存。

### 5.4 完善普通文件完整性校验【已完成】

当前普通下载主要校验长度，无法识别“内容长度正确但内容错误”。建议按可用信息分层：

1. API 返回哈希时保存 `expectedHash` 和算法。
2. 下载完成后流式计算哈希。
3. 只有校验成功后才将任务标记为 `COMPLETED`。
4. 未提供哈希时继续使用长度校验，不阻塞普通下载。
5. APK 更新始终强制签名校验，若 release 提供 SHA-256 则强制哈希校验。

## 6. Phase 2：统一平台适配边界

### 6.1 以 `CloudFileSource` 作为平台业务唯一入口

现有 `CloudFileSource` 已包含目录、直链、文件操作、分享和配额能力。下一步应逐步迁移 ViewModel 对具体 API 的直接调用。

目标结构：

```text
CloudViewModel / ResolveViewModel
        ↓
CloudFileSource
        ↓
平台 Adapter
        ↓
平台 Api
```

ViewModel 不应再了解：

- `stoken` 的具体含义。
- 百度的 `uk` / `sekey`。
- 迅雷的 `captcha` 和设备参数。
- 139 的任务轮询细节。
- 123 的签名和 URL 解码细节。

这些内容应由 Adapter 或 Repository 封装。

### 6.2 将平台能力显式建模

现有 `CloudCapabilities` 可以继续扩展，但只加入真实存在的差异：

```kotlin
data class CloudCapabilities(
    val supportsShareDownload: Boolean,
    val requiresTransferForDownload: Boolean,
    val supportsFolderDownload: Boolean,
    val supportsVideoPreview: Boolean,
    val supportsHls: Boolean,
    val supportsBatchTransfer: Boolean,
    val maxSharePasscodeLength: Int?,
    val rateLimitProfile: String
)
```

不要把所有平台参数都塞进一个超大配置类。协议细节仍应留在平台适配器内部。

### 6.3 统一凭证类型

当前接口参数经常用 `String` 同时表达 Cookie、Token、Authorization。建议引入内部类型：

```kotlin
sealed interface CloudCredential {
    data class Cookie(val value: String) : CloudCredential
    data class AccessToken(val value: String) : CloudCredential
    data class Xunlei(val accessToken: String, val deviceId: String, val captchaToken: String) : CloudCredential
    data class C139(val cookie: String, val authorization: String?) : CloudCredential
}
```

收益：

- 减少 Token 和 Cookie 传错的可能。
- 平台特有凭证不再通过多个独立参数散落传递。
- 便于统一脱敏和日志处理。

### 6.4 减少 `ResolveViewModel` 中的路由分支

建议先抽取一个解析平台上下文：

```text
ResolvePlatformContext
  - platform
  - credentialProvider
  - repository
  - rootDirectory
  - displayName
  - capabilities
```

解析流程只依赖上下文，不在每个操作里重复判断平台。

推荐顺序：

1. 先抽取只读属性和凭证获取。
2. 再抽取目录列表和直链获取。
3. 最后迁移转存、批量操作和临时清理。
4. 每一步完成后删除对应的 `when` 分支。

## 7. Phase 3：性能优化

### 7.1 分页改为真正的增量加载

当前部分 Repository 会一次性循环请求多页并返回完整列表。对于大目录，应改为：

```text
首次请求 → 返回第一页 + cursor
用户触底 → 请求下一页
下一页追加到当前列表
```

建议：

- Repository 返回 `PageResult<T>`。
- ViewModel 保存当前 cursor。
- UI 仅在接近列表尾部时触发加载更多。
- 对重复 fid 去重。
- 对连续失败保留已加载内容，不回退整个页面。
- 设置最大页数和最大文件数，避免异常接口无限翻页。

### 7.2 统一网络客户端配置

在 `HttpClients` 统一管理：

- 连接超时。
- 读取超时。
- 写入超时。
- 总调用超时。
- Dispatcher 最大并发数。
- 每个 Host 最大请求数。
- 连接池大小和 keep-alive 时间。
- 是否启用网络日志。

下载客户端和控制面客户端应分离：

- 控制面客户端：连接数较少、请求超时明确、便于重试。
- 下载客户端：支持高并发 Range、较大的读取超时和独立连接池。

### 7.3 批量任务增加统一调度器

目前批量下载和批量转存分别在不同 ViewModel 中串行处理。建议抽取轻量级 `BatchTaskRunner`，统一提供：

- 并发度。
- 任务进度。
- 取消信号。
- 单项失败是否继续。
- 成功数、失败数和取消状态。
- 平台 RateLimiter 接入。

默认仍建议串行或低并发，不应为了速度直接提高平台请求并发。

### 7.4 降低 Compose 无效重组

重点检查：

- 下载列表是否因某一个任务进度更新而全列表重组。
- 是否将大列表和实时统计合并成高频 StateFlow。
- 是否在 Composable 中反复创建 Repository、回调和大型集合。
- 是否将只读平台配置作为稳定对象传递。
- 文件列表是否使用稳定且唯一的 key。

建议将下载列表拆分为：

```text
任务基本信息流：低频，来自 Room
任务实时统计流：高频，来自内存
```

列表项只订阅自身任务 ID 的统计，避免一个任务变化导致所有条目重组。

### 7.5 清理启动阶段工作

`YunXApp.onCreate()` 当前会启动状态修复、临时目录清扫和设备标识初始化。建议：

- 设备标识初始化保持同步且幂等。
- 数据库状态修复必须优先完成。
- 云端清扫不阻塞首屏显示。
- 云端清扫增加超时和单次最大删除数。
- 失败记录脱敏日志，下次启动继续处理。

不建议在启动阶段执行大规模云端目录扫描。

## 8. Phase 4：错误治理与可观测性

### 8.1 建立统一错误分类

建议定义内部错误模型：

```kotlin
sealed interface YunxError {
    data object NotLoggedIn : YunxError
    data object AuthExpired : YunxError
    data object InvalidPasscode : YunxError
    data object RateLimited : YunxError
    data object NetworkUnavailable : YunxError
    data object LinkExpired : YunxError
    data object RangeUnsupported : YunxError
    data object StorageDenied : YunxError
    data object IntegrityCheckFailed : YunxError
    data class ProtocolChanged(val platform: String) : YunxError
    data class Unknown(val message: String) : YunxError
}
```

UI 层只负责将错误映射为用户文案，不能依赖字符串关键词判断业务类型。

### 8.2 统一请求上下文日志

每次解析和下载使用一个脱敏的 operation ID：

```text
resolve-xxxx
download-xxxx
```

日志只记录：

- 平台。
- operation ID。
- 请求阶段。
- HTTP 状态码。
- 重试次数。
- 耗时。
- 脱敏后的错误类型。

禁止记录：

- Cookie 原文。
- Token 原文。
- 完整签名直链。
- 完整提取码。
- 完整 Authorization。
- 用户文件完整路径中的敏感部分。

### 8.3 平台协议探针

不建议在生产环境自动发送大量探测请求。可以在 Debug 构建增加协议诊断工具，输出：

- 接口名称。
- 请求参数字段名，不输出敏感值。
- 响应状态码。
- 响应字段结构。
- 关键字段是否缺失。

当平台接口返回结构变化时，优先提示“平台接口可能已变化”，而不是统一显示“操作失败”。

### 8.4 错误信息分层

分为三层：

1. 用户文案：简短、可操作。
2. 诊断信息：用于日志和开发排查。
3. 原始服务端信息：仅在确认不包含凭证时保留。

例如：

```text
用户文案：百度网盘请求被限制，请稍后重试并降低操作频率
诊断信息：platform=baidu phase=locate code=...
```

## 9. 文案资源化与国际化

当前 `strings.xml` 只有 `app_name`，全仓库没有 `stringResource` 引用，约 22,000 行 UI 文案直接硬编码。这会导致文案修改容易漏改、无法支持多语言、动态文本缺少统一格式约束，以及无法通过资源工具发现重复或未翻译文案。

本项应独立于业务重构推进，不建议对全仓库进行无差别字符串替换。迁移按页面和业务域拆分，每批改动保持可编译、可回归。

### 9.1 资源目录和命名规范

第一阶段只建立默认中文资源，保持现有用户体验不变：

```text
app/src/main/res/values/strings.xml
```

默认资源稳定后再增加：

```text
app/src/main/res/values-en/strings.xml
app/src/main/res/values-zh-rTW/strings.xml
```

资源 key 按“类型 + 业务域 + 含义”命名：

```text
action_download
action_cancel
nav_resolve
status_downloading
error_auth_expired
settings_download_threads
quark_login_title
```

命名规则：

- 按钮使用 `action_`，导航使用 `nav_`，状态使用 `status_`，错误使用 `error_`。
- 页面标题和设置项使用明确业务域前缀。
- 不使用 `text1`、`label2`、`msg_x` 等无语义名称。
- 同一语义使用同一个资源 key。
- 用户文件名、URL、API 参数和服务端原始数据不资源化。

### 9.2 迁移顺序

按风险从低到高分批处理：

1. 公共导航、返回、取消、确定、保存、删除、重试、刷新和空状态。
2. 下载页、下载状态、下载通知、进度、速度和任务数量。
3. 解析页、链接提示、解析错误和批量操作进度。
4. 设置页、备份、更新和电池优化提示。
5. 六个平台的登录标题、登录状态、操作菜单和平台提示。
6. 动态数量、文件大小、速度、时间、多行说明和富文本。

每批只覆盖一个页面或紧密业务域，避免与网络、下载和数据库逻辑改动混在一起。

### 9.3 Compose 使用方式

静态文案：

```kotlin
Text(stringResource(R.string.action_download))
```

带参数的文案使用资源占位符，不在 Kotlin 中拼接：

```xml
<string name="download_progress">已下载 %1$s / %2$s</string>
```

数量文案使用 `plurals` 和 `pluralStringResource`，不要手动拼接“个/项”。需要粗体、链接或点击事件的文本，使用 `AnnotatedString` 组合资源片段。

### 9.4 迁移与扫描规则

- `Text`、`Button`、Snackbar、Toast、Dialog、Notification、`contentDescription` 中的用户可见文案优先迁移。
- 日志、异常内部标识、API 参数、网盘文件名、URL 和正则表达式不迁移。
- 服务端错误先经过错误分类和 UI 映射，再决定用户文案。
- 测试断言中的用户文案与资源 key 同步调整。
- 每批提交前扫描新增 UI 字符串字面量、未使用资源、重复资源值和缺失翻译。

不要求一次性清零全仓库字符串字面量，但已迁移页面必须达到“用户可见文案无硬编码”。

### 9.5 推荐拆分任务

1. 建立资源 key 规范和检查脚本。
2. 迁移 `MainScreen` 和导航。
3. 迁移 `DownloadScreen` 和下载通知。
4. 迁移 `ResolveScreen`。
5. 迁移 `SettingsScreen`、更新和备份流程。
6. 迁移六个平台登录和网盘浏览页面。
7. 处理动态数量、时间、速度和大小文案。
8. 增加英文资源并完成布局回归。
9. 在 CI 中增加硬编码扫描，防止新增 UI 文案回退到源码。

### 9.6 验收标准

- `strings.xml` 不再只有 `app_name`。
- 已迁移页面的用户可见文案全部通过 `stringResource` 或 `pluralStringResource` 获取。
- 动态文案使用资源格式化，默认中文显示与迁移前一致。
- 英文资源切换后不出现 key 名、截断或明显布局溢出。
- 横屏、竖屏、深色模式和无障碍描述完成验证。
- 资源 key 无重复定义，无明显未使用资源。

## 10. 测试建设

### 9.1 单元测试

继续保持纯逻辑可测试，重点覆盖：

- `ShareLinkParser` 的平台和提取码边界。
- 平台分页结束条件。
- 目录栈和面包屑。
- 下载状态迁移。
- 分片规划和断点状态。
- Range / Content-Range 判定。
- HLS 播放列表解析。
- 下载路径安全策略。
- 认证失效分类。
- 临时清理任务状态。

### 9.2 MockWebServer 集成测试

为每个平台至少覆盖：

- 正常登录态。
- 提取码正确。
- 提取码错误。
- 分页。
- 空目录。
- 认证失效。
- 服务端限流。
- 响应字段缺失。
- 网络超时。
- 转存任务超时。
- 获取直链失败。

不需要在 JVM 测试中复刻真实平台全部接口，只模拟项目实际消费的响应字段和关键错误。

### 9.3 DownloadManager 集成测试

建议覆盖完整任务生命周期：

```text
enqueue → downloading → completed
enqueue → pause → resume → completed
enqueue → fail → retry → completed
enqueue → remove
range ignored → single stream fallback
partial files → resume
plan changed → clear and redownload
save failed → failed
```

### 9.4 真机验收

每个版本至少验证：

- Android 23、Android 10、Android 14 或以上。
- 横竖屏切换。
- 深色模式切换。
- 锁屏后台下载。
- 最近任务强杀。
- 存储权限拒绝和重新授权。
- SAF 自定义目录。
- MediaStore 同名文件。
- 500 MB 以上文件。
- 多任务并发。
- 账号注销后任务恢复行为。

## 11. 安全优化清单

### 10.1 WebView

- 所有登录 WebView 统一关闭文件访问和内容访问。
- 只允许必要域名导航。
- 页面离开可信域名时清除敏感 JS Bridge。
- 禁止不必要的 JavaScript 接口。
- 登录完成后按域名清理 Cookie。
- 不在 WebView 日志中输出 Cookie。

### 10.2 认证备份

- 默认只支持加密导出。
- 备份文件使用 SAF 保存。
- 导入前展示实际平台列表。
- 明文导入必须显式二次确认。
- 不在错误日志中输出备份内容。
- 建议增加备份格式版本兼容测试。
- 建议支持用户选择导出平台，减少单文件凭证集中度。

### 10.3 下载任务

- 请求头字段加密存储。
- 不把完整 URL 放入普通日志。
- 删除任务时清理请求头、临时目录和完成回调。
- 任务完成后尽早释放敏感请求头内存。
- 下载链接过期时给出明确提示，不无限重试。

## 12. 不建议做的优化

以下方向不建议纳入技术优化目标：

- 频繁更换设备指纹。
- 提供一键更换平台设备标识。
- 多账号轮换降低单账号行为频率。
- 伪装其他官方客户端来规避平台识别。
- 无限制提高 Range 并发。
- 通过延迟、代理或重试机制对抗平台处置。

这些做法会增加账号和用户群体风险，也会让代码更难维护，不能解决核心业务行为带来的平台限制。

## 13. 推荐实施顺序

### 第一批：可靠性和安全

1. 持久化临时清理任务。
2. 抽取并测试下载状态机。
3. 完善下载失败和保存失败路径。
4. 统一敏感日志脱敏。
5. 增加 Room Migration 和 DownloadManager 回归测试。

### 第二批：平台适配治理

1. 统一 `CloudFileSource` 使用入口。
2. 引入 `CloudCredential`。
3. 抽取 `ResolvePlatformContext`。
4. 将平台错误映射为统一错误类型。
5. 逐步减少 `ResolveViewModel` 的平台分支。

### 第三批：性能

1. 分页接口增量化。
2. 分离控制面和下载面 OkHttp 客户端。
3. 抽取批量任务调度器。
4. 优化下载列表的高频重组。
5. 增加耗时和重试指标。

### 第四批：工程化

1. 接入静态检查和格式检查。
2. 增加 Debug 协议诊断能力。
3. 建立平台接口 Mock Fixture。
4. 完善真机回归清单。
5. 发布流程自动生成 APK SHA-256。

### 第五批：文案资源化

1. 建立资源 key 命名规范和扫描规则。
2. 迁移公共导航、操作和状态文案。
3. 迁移下载、解析、设置和平台页面。
4. 处理动态数量和格式化文案。
5. 增加英文资源并完成多语言回归。

## 14. 验收指标

建议用以下指标判断优化是否有效：

| 领域 | 指标 |
|---|---|
| 构建 | CI 编译、单测、静态检查全部通过 |
| 数据库 | 所有版本迁移测试通过，升级不丢失 v9 之后数据 |
| 解析 | 六个平台正常链接均可识别，错误链接可解释 |
| 浏览 | 大目录分页完整，无重复项和错误跳转 |
| 下载 | 500 MB 以上文件支持暂停、恢复和断点续传 |
| 下载 | Range 异常不会生成长度错误或错误拼接文件 |
| 下载 | 任务删除后无持续网络请求和临时文件残留 |
| 认证 | 失效账号可识别并引导重新登录 |
| 安全 | 日志和崩溃报告不包含明文凭证 |
| 更新 | APK 签名不匹配时拒绝安装 |
| 性能 | 主线程不执行大文件 IO 和凭证加解密 |
| 可维护性 | 新增平台不复制完整 UI 页面和 ViewModel |
| 国际化 | 已迁移页面无用户可见硬编码文案 |
| 国际化 | 动态数量和参数文案使用资源格式化 |
| 国际化 | 默认中文与目标语言资源可正常编译和切换 |

## 15. 总结

YunX 当前最需要的不是继续增加平台数量，而是把现有核心链路治理稳定：

```text
凭证安全
→ 分享会话
→ 平台适配
→ 直链生命周期
→ 下载状态机
→ 文件完整性
→ 错误分类
→ 可观测测试
→ 文案资源化
```

最优先建议实施“持久化临时清理任务 + 下载状态机测试 + 统一错误模型”三项。这三项分别解决资源残留、行为不可验证和错误不可解释的问题，改动相对可控，且能为后续平台抽象和性能优化提供稳定基础。文案资源化应作为独立的 P1 工程持续推进，优先从公共 UI、下载页和设置页开始，避免一次性改动约 22,000 行 UI 文案。

现有 `FIX_PLAN.md` 中已经完成的 P0、P1 和 P2 项不应重复实施。本方案主要作为后续路线图，具体改动仍应按阶段拆分为独立提交，并在每一阶段完成编译、自动化测试和真机验证。
