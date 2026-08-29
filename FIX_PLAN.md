# YunX 修复任务计划

> 基线：`4e0fa74`（versionName 1.2.5 / versionCode 9）
> 代码规模：`app/src/main/kotlin` 约 31,840 行 Kotlin / 142 文件，单测 7 个文件

---

## 0. 前置约束

这些约束决定了计划的形态，动手前请先确认认同：

1. **测试基建缺失**。`app/build.gradle.kts:58` 只有 `junit:junit:4.13.2`，无 Robolectric / mockk / coroutines-test / MockWebServer，也没有 `androidTest` 源码目录。当前只有纯 JVM 函数可测。任何"给 DownloadManager 补测试"的任务都要先加依赖，那本身是一项独立工作（见 P3）。
2. **编译回归有保障，行为回归没有**。CI（`.github/workflows/ci.yml:44-48`）跑 `assembleDebug` + `testDebugUnitTest`，能挡住编译错误，挡不住逻辑退化。因此 P0/P1 每项都必须有**真机验证步骤**。
3. **单人开发，中断成本高**。计划按"每阶段可独立发版"切分，不要求一次做完。任何阶段中途停下，代码都应处于可发布状态。
4. **P2 之前不要顺手改重复代码**。6 份近似重复的 UI 文件，每改一次要乘以 6，且会让后续抽象时的 diff 更难对齐。P1-3 走快路已是一次明确的妥协，不宜再增加。

---

## P0 — 正确性与安全（必须先做）

### P0-1 DownloadManager 单例化 + scope 生命周期

- [x] 新建 `data/download/DownloadManagerHolder.kt`（object 单例，`@Volatile instance` + 双检锁，用 applicationContext 构建）
- [x] `ChunkDownloader`、`SettingsRepository` 一并单例化
- [x] **6 个 Api 实例（`MainScreen.kt:177-182`）一并单例化** —— 见下方「附带影响」
- [x] `MainScreen.kt:217-233` 改为从 Holder 获取
- [x] `storagePermissionProvider` 赋值（`MainScreen.kt:243-256`）改为 `DisposableEffect` 内 set、`onDispose` 置回 no-op
- [x] `xunleiApi.refreshTokenProvider` 赋值（`MainScreen.kt:294-300`）同上处理
- [x] 对齐并发默认值：`DownloadManager.kt:114` 的 3 改为 1，与 `SettingsRepository.kt:29` 一致

**问题**：`MainScreen.kt:217` 用 `remember` 创建 DownloadManager，而 `MainActivity` 在 `AndroidManifest.xml:30-38` 未声明任何 `configChanges`。旋转屏幕 / 切深色模式 / 改字体缩放即重建 Activity → 新建第二个 DownloadManager；旧实例的 `scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`（`DownloadManager.kt:125`）**没有任何 cancel 时机**，旧协程继续跑。

**后果**：两个引擎对同一个 `download_tmp/$id/part_i` 各自 `seek(自己看到的 length)` 写入，交错写入导致内容损坏；而 `finishDownload` 只校验总长度不校验内容（`DownloadManager.kt:907`），**损坏文件能通过校验落盘**。此外旧实例 `activeTaskCount` 归零时会 `DownloadService.stop`，掐掉新实例仍在依赖的前台服务与 WakeLock；新实例的 `activeJobs` 里没有旧 job，`pause`/`remove` 对旧下载完全无效（后台幽灵下载）。

**附带影响（不只是下载器）**：同一个 `remember` 问题也作用于 `MainScreen.kt:177-182` 的 6 个 Api 实例。其中 `Pan123Api` 持有构造时生成的设备标识 `loginuuid`（`Pan123Api.kt:42-43`），**每次 Activity 重建即更换** —— 详见 P1-6。本项修完可消除"重建导致标识变化"，但 `loginuuid` 的跨进程持久化仍需在 P1-6 单独处理。其余 5 个 Api 无实例状态，单例化只是省掉重复分配。

**注意**：单例化后 Context 生命周期变长，必须逐一确认 DownloadManager 内所有 context 用法只依赖 applicationContext（`cacheDir` / `externalCacheDir` / `ContentResolver` / `startForegroundService` 均可）。第 5、6 条不是可选项——不改的话，问题会从"内存泄漏"升级为"持有已销毁 Activity 的引用并反复覆写"。

**验证**：真机开始一个 500MB+ 下载 → 连续旋转屏幕 5 次 → logcat 中 `分片规划: id=` 只应出现一次 → 下载完成后校验文件 MD5 与源一致。

**风险**：中。

---

### P0-2 进程被杀后的状态恢复

- [x] `YunXApp.onCreate()` 中起一次性协程调用 `db.downloadTaskDao().markInterruptedAsPaused()`
- [x] `runTask` 写入 `STATUS_DOWNLOADING` 后补一次 `dao.updateError(id, "")`

**问题**：`DownloadTaskDao.markInterruptedAsPaused()`（`DownloadTaskDao.kt:29-30`）已定义但**全仓库零调用**（已 grep 确认）。`DownloadService` 返回 `START_NOT_STICKY`，无 BOOT_COMPLETED / WorkManager / JobScheduler。进程被杀后 DB 里任务永远停在 `status=1`，UI 显示"下载中"并给出暂停按钮，实际没有任何协程在跑；用户必须先点暂停再点继续才能恢复。

**为什么放 Application 而不是 MainScreen**：必须在 DownloadManager 有任何机会调用 `runTask` 之前执行，否则会与 `updateStatus(DOWNLOADING)` 竞态。

**顺带修**：`errorMsg` 成功后从不清空（DAO 方法 `updateError` 已存在于 `DownloadTaskDao.kt:36`，只是没人在成功路径调用），旧失败原因会残留在已恢复的任务行上。

**明确不做**：自动恢复下载。那是新功能，且会引入用户不知情的后台流量，应单独讨论。本项目标仅是让**显示状态与实际状态一致**。

**验证**：下载中从最近任务列表强杀应用 → 重进 → 任务显示"已暂停"，点"继续"能从断点续传。

**风险**：低。

---

### P0-3 更新 APK 完整性校验

- [x] 新建 `data/update/ApkVerifier.kt`：安装前比对待安装 APK 与当前应用的签名证书 SHA-256
- [x] 从 release body 正则提取 `SHA-?256[:\s]*([0-9a-fA-F]{64})`，能提取到则比对文件哈希，提取不到则跳过（兼容旧 release）
- [x] 镜像下载通道加二次确认对话框
- [ ] 发布流程开始在 release body 附带 APK 的 SHA-256

**问题**：`UpdateChecker.MIRROR_PREFIX = "https://cdn.gh-proxy.org/"`（`UpdateChecker.kt:20`，用法见 `:23`）是一个完整的 APK 供应链中间人，能返回任意字节。下载完成后直接调系统安装器（`DownloadScreen.kt:887-921`），应用侧**零校验**。实际防线只剩系统的"同签名才允许覆盖升级"——对覆盖升级有效，对首次安装 / 卸载重装无效。

**为什么签名校验是主防线**：不需要改发布流程即可生效，且恰好覆盖镜像通道这个最大风险点。哈希校验是辅助。

**实现注意**：`getPackageArchiveInfo` 在 API 28 以下没有 `GET_SIGNING_CERTIFICATES`，需按 SDK 分支回退到 `GET_SIGNATURES`（minSdk 23）。校验失败时拒绝安装并明确提示"安装包签名与当前应用不一致，可能已被篡改"。

**验证**：用不同 keystore 签一个 APK 放进下载目录，走安装流程，应被拦下。

**风险**：低（纯新增校验路径，失败时降级为拒绝 + 提示）。

---

### P0-4 崩溃日志脱敏

- [x] `CrashHandler.buildCrashLog`（`CrashHandler.kt:39-56`）的堆栈部分逐行过 `LogRedactor.line`
- [x] `saveCrashLog`（`:58-64`）加保留策略：保留最近 10 个、删除 7 天前的
- [x] 补全 `LogRedactor` 词表（`LogRedactor.kt:8-10`）：`os_sso_sid`、`pass_code_token`、`share_fid_token`、`fids_token`、`sekey`、`randsk`、`userdata`、`bdstoken`
- [x] `LogRedactorTest` 补对应用例

**问题**：`CrashHandler` 直接 `printStackTrace()` 原样落盘，并传给 `CrashActivity` 全文渲染（`CrashActivity.kt:140`）、支持一键复制到剪贴板（`:164-167`）。异常 message 里可能携带带签名的直链或非法 URL。崩溃文件永不清理，且 `file_paths.xml:7` 已为 `files/crash` 声明了 FileProvider 路径。

`LogRedactor` 现有能力完全够用（`LogRedactor.line` 在 `LogRedactor.kt:24-29`），只是当前仅在导出 logcat 时被调用。改动约 3 行。

词表补全里 `Os_SSo_Sid` 优先级最高——它是 139 网盘的核心登录态，当前不在脱敏列表内。

**验证**：单测覆盖新增词表；手工触发一次崩溃，确认展示内容无 `<redacted>` 以外的凭证痕迹。

**风险**：低。

---

### P0-5 备份安全收紧

- [x] 导出改走 `ActivityResultContracts.CreateDocument`（SAF），由用户选位置
- [x] 口令下限从 8 位提到 12 位（`AuthCrypto.kt:32`），UI 加强度提示
- [x] 导入前弹确认清单，列出备份文件实际包含的平台
- [x] 明文 JSON 导入改为二次高危确认；`AuthBackupManager.exportJson` 收窄为 `internal`

**问题**：加密备份写入公共 Downloads 目录（`AuthBackupManager.kt:239-271`）。一个备份文件等于 6 家网盘的完整会话，任何有存储读权限的应用都能拿走做离线爆破，且无速率限制。导入侧接受明文 JSON 和 V1（10k 迭代）格式，只校验 `app` 字段（`AuthBackupManager.kt:138`、`:145`）——存在凭证注入 / 钓鱼面。`exportJson` 当前是 public 且可产出明文（UI 未调用，但 API 可被误用）。

**明确不做**：换 Argon2id。需引入新依赖；PBKDF2-SHA256 210k 在配合"移出公共目录 + 口令下限提升"后，对当前威胁模型够用。

**验证**：导出后确认文件不在公共 Downloads；用 8-11 位口令应被拒绝；导入明文备份应有高危提示。

**风险**：低。

---

## P1 — 用户可感知的功能缺陷

### P1-1 面包屑跳转跳错目录

- [x] `openFolder`（`ResolveViewModel.kt:538`）改为 `dirStack.addLast(file.fid)`
- [x] `goBack`（`:556`）改为弹栈后取 `dirStack.lastOrNull() ?: currentDefaultDirFid()`
- [x] `navigateToLevel`（`:592-593`）同上取值
- [x] `backToInput`（`:576-581`）补 `dirStack.clear()`、`currentDirFid` 重置、`multiSelectMode = false`、`_selected.clear()`
- [x] 四处栈操作抽成一个私有 `DirStack` 内部类，走同一份逻辑

**问题**：`ResolveViewModel` 的 `dirStack` 存的是"进入某目录前所在目录"即**父目录**（`:538` `dirStack.addLast(currentDirFid)`）。`navigateToLevel` 弹栈后取 `dirStack.last()`，拿到的是**祖父目录**。

举例：`根 → A → B` 时 `dirStack = [根, A]`。点 level=1（想回到 A）后 `dirStack = [根]`，`currentDirFid = 根` —— 实际加载根目录内容，而 `pathNames` 已被截成 `[A]`，面包屑显示 A。level=0 恰好正确，所以问题只在层级 ≥1 时出现。

**对照**：6 个 CloudViewModel 的同名函数用的是"存自身 fid"语义（`QuarkCloudViewModel.kt:158` 是 `dirStack.addLast(file.fid)`），是正确的。本项就是把 ResolveViewModel 对齐过去。

**顺带修**：`backToInput` 当前不清 `dirStack` / 不退出多选模式 / 不清 `_selected`，返回输入页后 `multiSelectMode` 仍为 true，下次解析进详情页会直接显示带旧选中数的多选栏。

**风险**：低但需仔细。这三个函数加 `startResolve`（`:525`）的栈操作**必须一起改**，漏一处就是新 bug。抽 `DirStack` 内部类不是锦上添花，是防漏的手段。

**验证**：夸克分享嵌套 3 层，逐级点面包屑，确认每次加载的内容与面包屑显示一致；返回输入页后再次解析，确认无残留多选栏。

---

### P1-2 子目录加载失败不再踢回输入页

- [x] `ResolveUiState.Detail` 增加 `errorBanner: String?` 字段
- [x] `loadFiles` 失败时若 `session != null`，保留 `Detail` 状态 + 设 errorBanner + 回滚栈（弹掉刚 push 的目录、`pathNames` 截回）
- [x] 仅 `startResolve` 阶段的失败才置 `Error` 回输入页
- [x] `goBack`（`:561`）与 `navigateToLevel`（`:596`）凭证为空时的静默 `return@launch` 改为设置错误提示

**问题**：`ResolveScreen.kt:206` 的 `else -> ResolveInputContent` 把 `Error` 态也接了进去，而任何列表请求失败都置 `Error`（`ResolveViewModel.kt:746`）。在第 5 层目录一次网络抖动 = 整个浏览上下文丢失，且此时 `dirStack` / `pathNames` / `session` 都还是脏的。

更糟的是 `goBack` 和 `navigateToLevel`：凭证失效时先置 `Loading` 再静默 `return@launch`，界面**永久卡在转圈**。

**设计取舍**：给 `Detail` 加字段而不是新增 sealed 分支，避免 `when` 分支扩散到 6 个平台的 UI 代码里。

**验证**：进入 3 层目录后开飞行模式，点进下一层 → 应看到错误提示且仍停留在当前目录；清除登录态后点返回上级 → 应看到明确提示而非无限转圈。

**风险**：低。

---

### P1-3 网盘浏览补分页（走快路）

- [x] 6 个 `XxxCloudUiState.Loaded` 增加 `hasMore: Boolean` + `cursor: String?`
- [x] 6 个 CloudViewModel 增加 `loadMore()`
- [x] `LazyColumn` 末尾加"加载更多" item

**问题**：所有 CloudViewModel 只取第一页（夸克/UC 50 项、百度 100 项、迅雷 50 项）。139 和 123 的 API **已经返回了下一页游标，但被 `.first` 直接丢弃**（`C139CloudViewModel.kt:531`、`Pan123CloudViewModel.kt:518/541`）。目录超过 50/100 项静默截断，用户不会收到任何提示。

**为什么走快路**：正路是等 P2-1 统一 `CloudUiState` 后一次性加（只改 1 处）。但 P2-1 是大工程且可能拖很久，而"目录超 50 项看不到文件"是硬缺陷。**明确接受这部分代码在 P2-1 时要再改一遍**。

**验证**：在夸克网盘建一个 60 个文件的目录，确认能全部浏览到。

**风险**：低。

---

### P1-4 失效检测落地

- [x] 新建 `AuthExpiredException`（`data/network/`）
- [x] 各 Api 在明确的认证失效信号处抛它，替代通用 `IllegalStateException`（本版仅迅雷：panCall 401/refresh 失败、解析链 ensureFreshToken；123：HTTP/code 401。其余四家按排期观察后推进）
- [x] 6 个 AccountEntity 加 `invalidAt: Long = 0`，Room 版本 11 → 12（原计划 10 → 11 已被 P0-3 的 expectedSha256 占用），**写显式 Migration**
- [x] 捕获 `AuthExpiredException` 时标记 `invalidAt` 而非清库（保留昵称用于展示；机制经 API `authInvalidListener` 挂接 AccountRepository，迅雷/123 已接，其余四家接入即生效）
- [x] `DriveScreen` 卡片对 `invalidAt > 0` 显示"登录已过期，点击重新登录"并跳登录页

**问题**：`Pan123AccountRepository.validate()`（`Pan123AccountRepository.kt:38-43`）**无任何调用点**；所有平台 token 失效后都不清 DB、不改 UI 状态。典型体验是"首页显示已登录昵称，但每次操作都报错"，用户只能靠猜去手动登出重登。

**已有可复用的失效判据**：`BaiduApi.kt:233`（获取 bdstoken 失败）、`C139Api.kt:306`（缺少 authorization）、`XunleiResolveRepository.kt:45`（登录已过期，JWT exp 可解析）、`Pan123Api.kt`（`code != 0`）。

**迁移注意**：当前是 `addMigrations(MIGRATION_9_10)` + `fallbackToDestructiveMigrationFrom(1..8)`（`AppDatabase.kt:53-55`），这个边界是对的（v9 起凭证受保护），**不要**为了省事改成全局 destructive。

**风险**：中。涉及 DB 迁移 + 6 个平台的错误识别。识别不准导致"误判失效"比不识别更糟。**建议逐平台增量上线**，从信号最明确的迅雷（JWT exp）和 123（`code` 明确）开始，观察一个版本再推其余四家。

**验证**：手工把 DB 里某平台 cookie 改乱 → 操作应触发失效标记 → 卡片显示过期提示 → 点击进登录页 → 重登后恢复正常。

---

### P1-5 低成本修正（批量做）

每项 1-10 行，可在同一次提交完成：

- [x] **恢复时进度虚报**：`DownloadManager.kt:526-559`，把"删除不完整 seg"代码块（`:552-559`）移到统计之前。当前先累加所有 `seg_*.part` 长度并回写 DB，之后才删不完整的 seg，被删的字节已计入 → 进度虚高、速度与剩余时间失真。
- [x] **重试/降级路径 DB 写放大**：`:731`、`:792`、`:820`、`:838`、`:856` 无节流，每次回调都写 Room。套用主池已有的 256KB 节流 + CAS 模式（`:607-612`）。
- [x] **临时文件异常泄漏**：`:869-872`、`:920-931` 用 `try/finally` 包住，finally 删 `merged_$id` / `hls_$id`。当前 `DownloadSaver.save` 返回 null 抛异常时不删，单个文件可达完整大小。
- [x] **错峰延迟对每片生效**：`:587-588` 在主池 while 循环内且 `i` 持续增长，`i>=8` 后每领一片都先睡 200ms，与注释"仅影响首请求"不符。条件改为仅 `i < effectiveWorkers` 时延迟。
- [x] **Semaphore 空操作**：`:577` 容量恰等于 worker 数（`:580`），每个 worker 恒有许可。删掉；迅雷的 8 并发上限已由 `effectiveWorkers`（`:515-519`）表达。
- [x] **HLS 每请求新建 client**：`HlsDownloader.kt:28-31` 是 `get()` 属性，每次 `newCall` 都触发 `newBuilder().build()`，连接完全无法复用。改为 `by lazy`。
- [x] **HLS 错误信息不可区分**：`DownloadManager.kt:862` 所有失败都报"HLS 转码流下载失败"。加密流（`HlsDownloader.kt:50-53` 遇 `#EXT-X-KEY` 返回 false）单独报"该视频为加密 HLS 流，暂不支持下载"。
- [x] **登出全局清 Cookie**：6 处 `removeAllCookies(null)`（如 `QuarkAccountRepository.kt:70`）会清掉其他平台的 WebView Cookie。改为按域清除。
- [x] **`ignoreSslCert` 死开关**：`SettingsRepository.kt:69-74` 有存储但 `HttpClients` 不消费，而 `QuarkApi.kt:60`、`XunleiApi.kt:51`、`Pan123Api.kt:37`、`BaiduApi.kt:40` 的注释仍宣称"忽略 SSL 开关即时生效"。删开关与误导性注释——误导性注释比死代码危险。
- [x] **登录 WebView 未收紧**：4 个 Cookie 登录页（夸克/UC/百度/139）未设 `allowFileAccess=false`、`allowContentAccess=false`。照 `XunleiVerifyWebViewScreen.kt:108-110` 的写法补上。
- [x] **阻塞 IO 跑在 Default 池**：`DownloadManager.kt:342`（pause 列目录求和）、`:378-385`（remove 走 ContentResolver + `deleteRecursively`）加 `withContext(Dispatchers.IO)`。`finishDownload` 已为此专门切 IO 并留了注释（`:918-919`），这两处漏了。
- [x] **版本比较忽略后缀**：`UpdateChecker.kt:38-48` 对 `1.2.5-beta` 会把 `5-beta` 当成 0。改用 `versionCode` 比较，或正确处理预发布后缀。
- [x] **123 取链用 GET 探测重定向**：`Pan123Api.probeJsonRedirect`（`:574-594`）对真实 CDN 直链发 GET。已有 `Content-Length <= 8192` 守卫（`:582-583`）+ `.use {}` 关闭响应，**不会下载完整文件**，所以配额影响很小。真实问题是语义错误：探测应当用 HEAD，且 `followRedirectUrl`（`:564-571`）最多 5 次串行往返才开始下载。改 HEAD + 缩短跳数上限。
- [x] **123 分页恒真导致 49 次无谓请求**：`Pan123ResolveRepository.kt:47-49` 的 `optString("Next").takeIf { it != "-1" }`，缺字段时 `optString` 返回 `""`，而 `"" != "-1"` 恒真 → 只要列表非空就一直翻到 50 页上限。改为同时判空。
- [x] **139 分页判据错误**：`C139ResolveRepository.kt:47-50` 用 `batch.size == 200` 决定是否继续，但 `batch` 是 `caLst`（文件夹）+ `coLst`（文件）**合并后**的结果（`C139Api.kt:206-243`），两个列表各自受 `bNum/eNum` 约束，合并条数与分页窗口不是同一回事，可能提前停止或漏项。

---

### P1-6 设备标识治理

**先明确这一节在做什么、不做什么。** 这里处理的是**工程缺陷**：各平台的设备标识策略不一致，存在两种相反的错误形态。这不是"规避风控"，修复目标是让每台设备有**稳定且唯一**的标识，使行为可归因到真实设备。

两种错误形态：

- **形态 A：全体用户共用一个抓包指纹。** 平台看到"一台设备、几千个 IP、几千个账号"，是极强的滥用信号，且**任何一个用户触发风控，其余所有人连带受损** —— 把个体风险放大成集体风险。
- **形态 B：同一用户不断表现为新设备。** 标识每次启动都变，看起来像设备农场或规避行为，同样是风控信号。

两者都违反同一条原则，所以放在一节处理。

判断依据在 `XunleiDeviceFingerprint.kt:13` 的注释里已写明："开源分发后每台设备独立指纹，避免所有用户共享一个官方指纹被迅雷风控识别/连带封禁"。迅雷已经做对了，本节是把同样的模式推广到其他平台。

**关键点：正当性来自"稳定唯一"，"随机"只是达成唯一性的手段。** `XunleiDeviceFingerprint.kt:9` 的"此后永久复用（进程重启不变）"才是这个设计成立的核心 —— 缺了持久化就退化成形态 B。频繁轮换指纹是规避追责，与本节目标相反，见文末「明确不做」。

#### 现状盘点

已逐一核实（在 `QuarkApi.kt` / `UCApi.kt` 中 grep `device|uuid|cuid|devuid|machine|serial|imei|android_id|fingerprint` 零命中；139 与 123 见下方说明）：

| 平台 | 设备标识现状 | 形态 | 位置 | 处理 |
|---|---|---|---|---|
| 迅雷 | 每设备生成 + 持久化 | 正确 | `XunleiDeviceFingerprint.kt:40-67` | 无需处理 |
| 夸克 / UC | **无设备标识**，仅 UA + 账号 Cookie | 不适用 | `QuarkConstants.kt`、`UCConstants.kt` 全文无设备常量 | 无需处理 |
| 百度 | 全体共用抓包常量 | A | `BaiduApi.kt:285-290` | 待验证可行性 |
| 139 | 共用浏览器指纹 `2cdaf7ada9e...` | A | `C139Constants.kt:72`、`:75` | 可做（暴露面窄，见下） |
| 123 | `loginuuid` 每次构造随机、**不持久化** | **B** | `Pan123Api.kt:42-43` | 可做 |

**夸克 / UC 说明**：身份凭据只有账号 Cookie（`__pus`/`__puus`，每人不同）+ UA（客户端标识）。没有设备维度的东西，因此不存在设备指纹问题，本节不涉及。

**139 暴露面比表面看起来窄**。三套设备头的实际内容：

```
X_DEVICEINFO       = "||9|7.17.9|chrome|116.0.0.0|2cdaf7ada9e353c70eba99092e177991||windows 10||zh-CN|||"
X_CLIENT_INFO      = "||9|7.17.9|chrome|116.0.0.0|2cdaf7ada9e353c70eba99092e177991||windows 10||zh-CN|||dW5kZWZpbmVk||"
SHARE_X_DEVICEINFO = "||3|12.27.0|||||chrome 150.0.0.0|360X444|zh-cn|||"
```

注意第三条对应位置是**空的，不含指纹 hex**。对照使用点：

| 头 | 使用位置 | 含共享指纹 |
|---|---|---|
| `X-Deviceinfo` + `x-yun-client-info` | `C139Api.kt:629-630` 个人网盘管理（列目录/重命名/移动/删除/取链） | 是 |
| `x-deviceinfo`（SHARE 版） | `C139Api.kt:677`、`:703` 分享读取与取链 | 否 |

即共享指纹**只影响个人网盘操作，不影响分享解析** —— 而分享解析是本 App 的主流程。据此下调优先级。

#### 任务

- [x] **123 设备标识持久化（形态 B，可直接做）**
      `Pan123Api.kt:42-43` 的 `loginuuid` 是**实例字段**，构造时随机生成且不持久化；而 `Pan123Api()` 建在 `MainScreen.kt:182` 的 `remember {}` 里 → **每次启动 App 换一个，每次 Activity 重建也换一个**（旋转屏幕、切深色模式都会触发）。
      注释写"进程级固定即可"，但进程级不够 —— 设备标识应跨进程稳定。
      修法与迅雷完全一致：SharedPreferences 持久化 + 幂等 init。
      **依赖关系**：P0-1 把 Api 单例化后，`loginuuid` 会先变成进程级稳定（消除 Activity 重建导致的变化），但仍需持久化才算修完。两项可分开做，顺序不限。
      已确认 `loginuuid` 不参与签名（`Pan123Api.kt:61-74` 的 `makeSign` 只用 `path` / `ts` / `random`），改动不影响鉴权。
      **已落地**：新建 `Pan123DeviceId`（init + 持久化 + 未初始化回退），`YunXApp.onCreate` 初始化，`Pan123Api.loginuuid` 改读它。

- [x] **139 设备指纹（形态 A，低风险）**
      `C139Constants.X_DEVICEINFO`（`:72`）与 `X_CLIENT_INFO`（`:75`）中的 32 位 hex `2cdaf7ada9e353c70eba99092e177991` 改为每设备生成 + 持久化。
      **已验证不会破坏签名**：`C139Api.calSign`（`:70-76`）只对 `bodyJson + ts + rand` 计算，设备头不参与。
      `SHARE_X_DEVICEINFO`（`:108`）不含指纹，无需改动；但分享接口缺任一设备头即报 9530，改动时不要误删。
      因只影响个人网盘路径，可排在 123 之后。
      **已落地**：新建 `C139DeviceFingerprint`（每设备 hex + 持久化 + 回退原共享指纹），`C139Api` 个人网盘两处设备头改调它；`SHARE_X_DEVICEINFO` 未动。

- [ ] **百度设备指纹（形态 A）—— 先验证可行性，结论未知**
      `BaiduApi.kt:285-290` 的 `rand` / `devuid` / `cuid` / `deviceid` / `psign` 全是抓包常量，注释（`:275`）称"psign 为写死常量"。
      **但如果 `psign` 实际是对 `devuid`/`cuid`/`deviceid` 的签名，只改设备值不改 psign 会直接导致请求失败。**
      验证方式：固定其他参数，只改 `devuid` / `cuid` 发一次请求，看是否仍返回有效直链。
      - 若可行 → 新建 `BaiduDeviceFingerprint`，完全照 `XunleiDeviceFingerprint` 的结构（SharedPreferences 持久化 + 幂等 init + 异常回退到原常量）
      - 若不可行 → 需先搞清 psign 算法；**搞不清就做不了，这是硬约束，不要强行推进**

- [x] **平台级请求节流（当前完全没有）**
      现状：只有迅雷被压到 8 并发（`DownloadManager.kt:515-519` 的 `RANGE_WORKERS_CAP`），**百度反而走满并发上限 32** —— 而百度是最敏感的那个。
      - 新增 `PlatformRateLimiter`：按平台限制**控制面 API 请求**的最小间隔（与下载分片限速 `SpeedLimiter` 是两回事，不要混用）
      - 百度下载并发单独设上限，参照迅雷处理
      - 转存 → 取链 → 删除三步之间加小延迟，避免同一秒内打完
      **已落地**：
      - 新建 `PlatformRateLimiter`（`data/network/`）：平台级最小起点间隔门（Mutex 排队 + 间隔，只隔起点不串行整个 HTTP）；百度配置 500ms，其余平台暂不配置（风险低于百度，接入只需加一行 map 条目）
      - 百度风控敏感操作（transfer/locateDownload/deleteFile/deleteFiles/createShare）经节流器，转存→取链→删除三步自动各间隔 ≥500ms（全链 ≥1s，不再同一秒打完）；读操作（列表/verify/昵称）不节流，浏览体验不受影响
      - 百度下载分片并发封顶 `BAIDU_WORKERS_CAP = 8`（检测 UA 含 `netdisk` 或 URL 含 `baidu`），参照迅雷处理

- [ ] **调研：百度是否存在免转存的取链路径**
      当前百度必须转存后才能取链（`BaiduResolveRepository.kt:98-120`）。若存在不转存的路径，**这比任何指纹工作都有效** —— 它直接消除了风险行为本身，而不是掩饰它。
      我不知道是否存在，需要调研。调研无果就如实标注，不要用其他手段"替代"。

#### 不在本节范围：客户端身份冒用

`quark-cloud-drive/2.5.20`（`QuarkConstants.kt:28-30`）、`uc-cloud-drive/1.6.1`（`UCConstants.kt:77-79`）、`netdisk;12.24.6`（`BaiduConstants.kt`）、139 的 `YUN_CHANNEL_SOURCE=10000034` / `MCLOUD_CLIENT=10701`（`C139Constants.kt:51`、`:57`）—— 这些声称自己是官方客户端。

这与设备指纹是**两回事**，不要混为一谈：

- 设备指纹：全体用户共用同一个**设备**标识 → 与事实不符，且造成连带风险 → 本节修复
- 客户端标识：全体用户共用同一个**客户端**标识 → 与事实相符（大家确实都在跑 YunX），问题在于这个标识声称的是别的软件

后者无法通过工程手段"修复" —— 它是整个项目方法论的固有前提（见 `README.md:69-71` 关于协议逆向的说明），改掉就等于放弃这条技术路线。**列在这里只为避免与设备指纹混淆，不作为待办项。**

#### 明确不做

- **账号被限制后换指纹重来。** 这是从"避免误伤"跨到"规避处置"，性质不同。且实际效果很差：平台对"换设备后立刻恢复同样行为"的检测远比对首次行为严格，通常导致更快更彻底的封禁，还会促使检测规则收紧、殃及正常用户。
- 给用户提供"一键换指纹"入口。
- 把指纹改成每次启动随机 —— 这正是 123 当前的形态 B 缺陷，是要修的问题，不是方案。
- 多账号轮换来摊薄单账号风险。

---

### P1-7 风险披露前置

**这一节不动网络层，可以最先做，且是本计划中对用户实际帮助最大的一项。**

核心业务流程是：**转存他人分享 → 取直链 → 立刻删除**（百度 `BaiduResolveRepository.kt:98-120`，迅雷 `:140-155`，夸克 `QuarkResolveRepository.kt:107-128`）。

这个模式之所以被平台识别，是因为它**确实**与正常用户行为不同 —— 正常人不会在数秒内转存一个文件再删掉，一天数十次。这不是指纹问题、不是 UA 问题、不是频率问题，是**行为语义**问题。

**没有技术手段能让它看起来正常，因为它本来就不正常。** 任何声称能解决这一点的方案实质上都是对抗升级，而对抗升级的结果通常是平台收紧检测规则，最终所有用户处境更差。因此这部分的正确处理方式是产品层面的知情告知，而非技术对抗。

#### 任务

- [x] **把风险警告移进应用内**
      `README.md:7` 已有"不建议用百度网盘，可能导致账号被风控！！！"，但它埋在 README 里 —— **装了 APK 的用户根本看不到**。
      **已落地**：DriveScreen 百度卡片未登录时描述以警示色显示"风控风险高，可能导致账号被限制"，替代与其他平台一致的"点击登录，支持解析下载"。

- [x] **百度登录页加前置说明**，说清三件事：
      1. 机制：转存-删除模式会被平台识别
      2. 后果：账号可能受限
      3. 定性：这是使用本工具的固有代价，不是 bug、修不掉
      **已落地**：登录页风险弹窗升级为三要素全文，且不可点击外部关闭，必须显式选择"我已了解，继续"（进教程）或"暂不使用"（退出登录页）。

- [x] **考虑百度默认关闭**，需用户显式开启（配合上一条的说明）
      **落地判断**：以"登录页强制知情确认门"实现——未确认前无法进入登录流程，即事实上的默认关闭 + 显式开启；未引入设置级开关（对单一平台的开关属过度设计）。

- [x] **风控命中时给出准确提示**，而非笼统的"操作失败" —— 让用户知道实际发生了什么。可复用 P1-4 引入的 `AuthExpiredException` 分类机制，但风控与登录失效是两种不同状态，需分开表达。
      **已落地**：`BaiduApi.checkErrno`（覆盖 verify/列表/转存/取链/分享全部关键路径）对服务端风险类文案关键词（风控/风险/频繁/封禁/封号/异常/安全验证/受限）明确归因为"可能触发百度风控"+ 降频建议，与登录失效分开表达；未含关键词的失败保持原始错误、不做猜测。errno 级精确细分需真机风控样本，留待观察后推进。

#### 说明

这不是免责声明式的甩锅。`README.md:61-63` 已有免责声明，那是法律层面的；本节要解决的是**用户能否做出知情选择** —— 用户在点"登录百度"之前，应当知道自己在承担什么。

---

## P2 — 结构性重复（中期投入）

**这部分不修 bug，只降后续成本。** 判断依据：现在加第 7 个网盘要改 7+ 处（`ResolveViewModel` 的构造函数、Factory、`currentCredential`、`currentRepo`、`currentDefaultDirFid`、`platformName`、`canSave`、`saveToCloud` / `batchSaveToCloud` 两个 switch），外加复制 4 个 700+ 行的 UI 文件。

**量化基线**（`git diff --no-index` 实测）：

| 家族 | 现状行数 | 两两差异 | 可消除 |
|---|---|---|---|
| 5 个非夸克 CloudScreen | 3685 | 50 增 / 124 删（774 行文件） | ~2800 |
| 6 个 CloudViewModel | 3444 | 109-145 行 | ~2200 |
| 6 个 SaveSheet | 1400 | 17 增 / 22 删（236 行，93% 相同） | ~1150 |
| 6 个 AccountSheet | 1413 | 7 增 / 8 删（290 行，97% 相同） | ~1100 |

合计约 9,900 行中 **7,000-7,500 行是可消除的近似重复**，占 `ui` 包 22,370 行的约 32%。抽样 diff 确认差异几乎只有标识符和文案替换（`C139CloudUiState` → `XunleiCloudUiState`、`"139网盘"` → `"迅雷网盘"`、`dirId` → `dirFid`）。

推进顺序（每步独立提交，都不改行为）：

### P2-1 统一目录标识与 UiState（前置，其他都依赖）

- [x] `dirFid` / `dirPath` / `dirId` 收敛为统一命名
      **已落地**：UiState 层面统一为 `Loaded.dir`（语义注释保留：夸克/UC/迅雷 fid、百度路径、139 fileId、123 目录 id）；P1-3 加的 `hasMore`/`cursor` 自然并入。
      ViewModel 内部局部变量与 API 参数名（`currentDirFid` 等）保留——属 P2-4 `BaseCloudViewModel` 的抽取范围，本项不动。
- [x] 6 份同构 sealed interface 合并为 `CloudUiState { Loading; Loaded(files, pathNames, dir, cursor, hasMore); Error }`
      **已落地**：新建 `ui/viewmodel/CloudUiState.kt`（字段顺序 files/pathNames/dir/hasMore/cursor，与既有位置参数构造兼容）；
      6 个 ViewModel 删除各自 sealed interface 并全量替换引用；6 个 CloudScreen + 7 个 SaveSheet/CloudFileSheets 的类型引用与 `.dirXxx` 属性访问同步迁移。
      行为零变化（纯类型合并与字段改名），P1-3 的分页字段随迁移并入统一类型。

这是所有泛化的前提——目前字段名不同导致 UI 里 `(state as? XxxUiState.Loaded)?.dirXxx` 无法共用。

### P2-2 抽 SaveSheet 与 AccountSheet（最高性价比）

- [x] `CloudSaveSheet(state, callbacks)` 替掉 6 份
      **已落地**：新建 `CloudSaveSheet.kt`（平台名 + 根目录 fallback + `CloudDirBrowser` 最小接口参数化）。
      `CloudDirBrowser`（uiState/loadRoot/openFolder/back/navigateToLevel）定义在 `CloudUiState.kt`，6 个 CloudViewModel 声明实现（方法签名已验证一致）。
      `ShareDetailScreen` 调用点收敛为 Triple 分支选 (平台名, 根目录, 浏览器) + 单次调用。
- [x] `CloudAccountSheet(account: CloudAccountUi)` 替掉 6 份
      **已落地**：新建 `CloudAccountSheet.kt`，`CloudAccountUi` 数据类 + 6 个 `Entity.toAccountUi()` 映射扩展
      （真实差异全部数据化：迅雷设备号版无凭证区、123 Token 版含登录账号行、其余 Cookie 版；文案/剪贴板标签逐字保留）。
      `DriveScreen` 6 处调用点改为 `CloudAccountSheet(account.toAccountUi(), …)`。
- [x] 写一个把 6 种 Room Entity 映射成统一展示模型的适配层
      （即上述 `toAccountUi()` 扩展，与 CloudAccountSheet 同文件）

约消除 2,250 行。顺带修掉 UC / 迅雷 AccountSheet 是简化版（144 / 114 行 vs 其余 290 行）导致的体验不一致。
**实测消除 12 文件约 3,100 行，新增 2 文件约 620 行；UC/迅雷升级为完整版体验（迅雷保持不展示 token 的原决策）。**

### P2-3 抽 `CloudFileSource` + `CloudCapabilities`

- [x] 接口最小集：`list(dir, cursor)` / `downloadLink(file)` / `downloadHeaders()` / `rename` / `move` / `delete` / `createShare` / `quota`
      **已落地**：`data/network/CloudFileSource.kt` 定义接口（`downloadHeaders(credential)` 携带可选凭证——夸克/UC/百度直链与登录态绑定）+ `ShareRequest`（有效期归一为天数/null=永久，提取码可空）。
      本项只建接口层与 adapter，**不迁移 VM 调用**（属 P2-4），保证每步独立可验证。
- [x] 6 个 `XxxApi` 各写一个 adapter
      **已落地**：`data/network/adapters/` 6 个 FileSource：
      - Quark/UC：页码游标化（page+1 字符串）、expired_type 枚举映射、createShare→getShareInfo 两步
      - Xunlei：token/deviceId/captcha 三 provider + cacheUserId；原生批量 move/delete；expiration_days "-1"/"1"/"7"/"30"
      - Baidu：路径型 dir/fidToken；period=天数直传；强制 4 位码（capability 声明，UI 保证输入）
      - C139：异步任务 pollTask 轮询内联（500ms+800ms×30）；coIDLst/caIDLst 文件目录分列
      - Pan123：next 游标直传；有效期天数→ISO8601 绝对时间（+08:00 手动拼接，低版本 Android 兼容）
      UC 视频特殊取链（分享链路原始直链）暂留 VM，注释标明 P2-4 迁移。
- [x] 真实平台差异用 `CloudCapabilities` 数据类描述
      **已落地**：name/rootDir/shareRequiresPasscode（百度）/sharePasscodeLength（4）/shareSupportsPasscode（139/迅雷系统生成）。
      大文件限速提示（BAIDU_LIMIT_BYTES）与 UC 视频取链标记留待 P2-4 接入时按需扩展字段。

需要数据化的真实差异：是否强制提取码（百度必须 4 位，`BaiduCloudScreen.kt:759`）、支持的有效期档位、大文件限速提示（`BAIDU_LIMIT_BYTES`，`BaiduCloudScreen.kt:84`）、是否需要视频特殊取链（`UCCoudViewModel.kt:240-263`）。这些是唯一真实的平台差异，应该数据化而非复制整份文件。

### P2-4 `BaseCloudViewModel` + `CloudBrowserScreen`

- [x] 抽 `BaseCloudViewModel`：dirStack / nameStack / 多选 / moveState / 批量循环 / 中断标志（**第一刀，已完成**）
      **已落地**：新建 `BaseCloudViewModel.kt`（339 行）：状态流（uiState/moveUiState）、主/移动双目录栈（存自身 fid 语义）、
      多选全套、actionFile/cloudMessage/isOperating/folderProgress/downloadTriggered/shareResult 等全部公共状态、
      refresh/loadMore 框架（`listFiles(dir, cursor)` 单一抽象注入点）、下载中断标志、`delayThenReload` 统一延迟刷新。
      6 个 CloudViewModel 改为继承基类（各 366-436 行，原 598-754 行）：仅保留平台注入点（platformLoginHint/rootDir/listFiles）
      与文件操作（download/rename/move/delete/share + 批量版）；**公有 API 与 Screen 调用面完全一致（29 个方法逐一核对）**，
      Screen 层零改动。平台特殊行为逐一保留：迅雷 creds 三元组与 collectFolderFiles 凭证复用、UC 视频分享链路取链（fidToken 补齐）、
      139 pollTask 异步轮询、百度无凭证检查语义、123 next 游标+页码双轨（loadMore 覆写，页码由已加载条数推导）、
      123 无延迟刷新（delayAfter* = 0）。
- [x] 5 份 CloudScreen 收敛为 `CloudBrowserScreen(state, capabilities, callbacks, brandTitle)`（第二刀，已完成）
      **已落地**：新建 `CloudBrowserScreen.kt`（420 行共享骨架：三态列表/多选栏/面包屑/下拉刷新/加载更多/
      删除确认/处理中弹窗/分享结果弹窗/返回键与返回顶部），平台差异经 `CloudBrowserCallbacks` 注入
      （ActionSheet/RenameDialog/MoveSheet/ShareSheet 弹窗族 + onBatchDownload 等意图回调）。
      5 份平台 Screen 重写为「回调接线 + 平台弹窗」（139:733→424、123:775→466、迅雷:744→434、百度:807→506、UC:780→458），
      平台特有行为保留：百度 >300MB 限速拦截（单文件/批量均经 maybeShowBaiduLimit）、各家 ShareSheet 差异
      （百度强制 4 位码/迅雷枚举档位/123·UC 可选码/139 系统生成）、123 根目录 "0"/迅雷 ""。夸克 CloudDriveScreen
      暂未切换（其结构已走 CloudFileSheets 共享，收益低、改动面大，留待 P2 收尾时评估）。
- [x] 各平台自写的 ActionSheet / MoveSheet / ShareSheet / RenameDialog 收敛为一个 `CloudActionSheet`（第三刀，已完成）
      **已落地**：新建 `CloudActionSheets.kt`（301 行）：`CloudActionSheet`（share 描述参数化：139/迅雷「自动带提取码」、
      其余「可设提取码/有效期」）、`CloudRenameDialog`、`CloudMoveSheet`（rootDirFallback 参数化：139 "/"、123 "0"、迅雷 ""）。
      5 份平台 Screen 缩至 170-259 行（139:424→170、123:466→215、迅雷:434→183、百度:506→259、UC:458→208），
      各文件仅保留真实差异（ShareSheet 提取码语义/档位值类型 + 百度限速拦截）。
      ShareSheet 本身未强行统一：五家档位值类型不同（Int? 1/7/30、Int 0-30、枚举 1-4）且提取码语义互斥
      （无/可选/强制/系统生成），统一需引入配置 DSL，收益低于维护成本——保留平台私有实现是正确取舍。
- [x] 同批修正 `UCCoudViewModel` / `UCCoudScreen` 拼写（应为 `Cloud`，已在 15 处引用中固化）
      **已落地**（随第二/三刀分步完成）：`UCCoudScreen.kt`→`UCCloudScreen.kt`（第二刀），
      `UCCoudViewModel.kt`→`UCCloudViewModel.kt` + 类名/Factory（第三刀，同步 MainScreen/ShareDetailScreen/
      DriveScreen/ResolveScreen/UCFileSource 5 文件 20 处引用）。全仓库 `UCCoud` 拼写清零。

约消除 5,000 行。对比 `BaiduCloudViewModel.kt:84-205` 与 `QuarkCloudViewModel.kt:99-235` 可见这些逻辑逐字节相同。当前只有夸克走共享的 `CloudFileSheets.kt`（1024 行），其他 5 家各自重写——这个分叉是抽象缺口的核心。

**拼写修正必须并入本项**，不要单独提交：单独改会产生一个纯改名的大 diff，之后又要再动一次。

### P2-5 `QuarkApi` / `UCApi` 去重

- [x] 抽 `AliCookieDriveApi` 基类：`CookieUtil` / `get` / `postJson` / `parseData` / `pollTask` / `createFolder` / `getQuota`
      **已落地**：新建 `AliCookieDriveApi.kt`（`AliCookieUtil` 合并两家逐字相同的 Cookie 工具 + 抽象基类）。
      基类收敛**逐字相同**的方法：get/postJson/parseData/mergeCookieFromResponse/createFolder/pollTask/
      pollShareTask/renameFile/moveFile/refreshSession/getShareInfo；平台差异经 8 个抽象属性注入
      （taskUrl/fileUrl/renameUrl/moveUrl/configUrl/shareInfoUrl/apiUserAgent/referer）。
      `getQuota` 两家请求头不同（UC 多 Origin/Referer/CLOUD_UA），保留子类各自实现——不强凑。
      QuarkApi 570→383 行、UCApi 757→570 行。
- [x] （顺带修复）UCApi 抛 `QuarkApiException` 的跨平台异常类型泄漏
      **已落地**：基类引入 `AliDriveApiException`，`QuarkApiException` 改为继承它（夸克侧既有 catch 全兼容）；
      UCApi 全部 18 处 throw 改为 `AliDriveApiException`。

两者近乎逐行复制，且 `UCApi` 抛的是 `QuarkApiException`（`UCApi.kt:369`、`:734`、`:740`）——跨平台异常类型泄漏。

### P2-6 顺带清理（可随时做）

- [x] ~~`items/CustomFabMenu.kt`（166 行）整个文件无调用方~~ **保留**：计划编写后 CrashActivity 崩溃页已采用它（复制/重启/退出菜单），不再是无调用方死代码
- [x] `components/PlaceholderScreen.kt`（76 行）已删除（无调用方）
- [x] `Theme.kt` 约 150 行未使用的对比度配色方案（`mediumContrastLight` / `highContrastLight` / `mediumContrastDark` / `highContrastDark`）+ 死参数 `dynamicColor` + 3 个未使用 import
      **已落地**：连带删除 Color.kt 中仅被这 4 个方案引用的 `*MediumContrast` / `*HighContrast` 常量（两文件合计约 -300 行）
- [x] `DriveScreen.kt` 的 `others = emptyList()` 死列表与渲染块、`DriveAccount` 上的过期 TODO 已删
- [x] `QuarkConstants.kt` 被注释掉的旧 UA（`*/` 位置错乱）已删并恢复正常缩进
- [x] `DownloadTaskEntity.cleanupId` + `DownloadTaskDao.updatePlan` + `QuarkConstants.TEMP_SUBDIR_PREFIX` 三死字段
      **已落地（删两个、实现一个）**：`cleanupId` 从 Entity 删除（Room v12→v13 表重建迁移，DDL 与 Entity 逐列对齐）；`updatePlan` 从 DAO 删除；
      `TEMP_SUBDIR_PREFIX` 落地为 **YunXApp 启动清扫**——夸克「YunX临时转存」下遗留的 `tr_*` 唯一子目录（进程被杀导致延迟删除未执行）
      在每次启动时一次性清理（先收集全部分页再删、fire-and-forget、失败静默下次重试），解决云端垃圾永久残留
- [ ] `strings.xml` 只有 `app_name` 一条、全仓库 `stringResource` 零引用（约 22,000 行 UI 文案硬编码）——不急，但记录在案
- [x] 重复工具函数收敛
      **已落地**（P2 系列重构后实际存量比盘点少）：`formatSize` 2→1（`util/Format.kt`）、`copyToClipboard` 3→1（`util/ClipboardUtils.kt`，label 参数化）、
      `randomPasscode` 2→1（`CloudActionSheets.kt` 共享）、`GitHubCard` 2→1（`components/GitHubCard.kt`，compact 参数保留两处排版差异）；
      `InfoRow` 已随 P2-2 收敛为 1 份；`SectionLabel` 2 份为**同名不同样式**（Settings 灰/Theme 主色），属有意的视觉差异，保留不合并

---

## P3 — 测试基建

当前 7 个单测全是纯函数类（`ShareLinkParser` / `HttpRangePolicy` / `HlsRequestPolicy` / `DownloadPathPolicy` / `LogRedactor` / `XunleiVerificationPolicy` / `SecureAccountDaos`）。而缺陷最集中的 `DownloadManager`（分片规划、续传统计、fallback 决策、状态流转）、`ChunkDownloader`、`HlsDownloader`、`ResolveViewModel` **零测试**。

- [ ] 加依赖：`kotlinx-coroutines-test`、`okhttp-mockwebserver`、`room-testing`（暂不引入 Robolectric，Compose 测试成本太高）
      **注**：本轮挖出的纯函数单测仅需既有 junit（含 TemporaryFolder），未加新依赖；三项依赖推迟到 ChunkDownloader MockWebServer 测试时一并引入。
- [x] **先把纯逻辑挖出来**（最关键，且同时是 P2 拆分的第一刀）：
  - [x] `chunkCountFor`（`DownloadManager.kt:996-1011`）→ 提到 `DownloadPlanner`
  - [x] `ElasticAllocator`（`:71-96`）→ 提为顶层 internal class
  - [x] 续传统计（`:526-573`）→ 提为 `resumeState(chunkDir, plan): ResumeState` 纯函数（有了它，P1-5 的顺序 bug 就能写回归测试）
  - [x] `RetryRange` 计算（`:694-753`）
        **已落地**：新建 `DownloadPlanner.kt`（`ChunkPlan`/`RetryRange`/`ResumeState` 数据类 + `chunkCountFor`/`planOf`/`planSignature`/`resumeState`/`missingRanges` 纯函数 + `ElasticAllocator`），全部 internal；
        `runTask` 改为调用纯函数，行为逐字节一致（注释原样保留并随迁）。新增 `DownloadPlannerTest` 13 例：分片分层/线程倍增/512 封顶/1MB 下限、计划恰好覆盖 total、
        **P1-5 顺序回归**（不完整 seg 先删后统计）、钳制 total、弹性前缀只推连续段、失败区间收集、分配器顺序领块/skipTo 只前进。
- [x] `ChunkDownloader` 用 MockWebServer 覆盖三态判定：206 正常 / 200 → `RANGE_IGNORED` / `text/html` → `FAILED` / `Content-Range` 不匹配 / 写入长度不足截断
      **已落地**：新增 5 个 MockWebServer 单测，覆盖正常 206、服务器忽略 Range 的 200、HTML 错误页、Content-Range 不匹配、响应体长度不足。
- [x] `ShareLinkParser` 补边界用例：非网盘 URL 在前、`提取码 abcd`（空格分隔）、`p=` 与 `passcode=` 参数名
      **已落地**：新增 3 个边界测试，并修正多 URL 选择、空格/可选冒号提取码及 `pwd`/`p`/`passcode` 三种查询参数。

**建议**：即使不马上写测试，也先做"挖纯逻辑"这一步——它本身就是 P2 拆分的开端。

---

## 排期

| 阶段 | 内容 | 可独立发版 |
|---|---|---|
| 第 1 版 | P0 全部（5 项）+ **P1-7**（风险披露，不动网络层） | 是 —— 安全 + 正确性 + 知情告知 |
| 第 2 版 | P1-1、P1-2、P1-5 | 是 —— 用户可感知修复 |
| 第 3 版 | P1-3（快路）、P1-4（先迅雷/123） | 是 |
| 第 4 版 | P1-6（123 持久化 + 139 指纹 + 节流；百度视验证结果） | 是 |
| 持续 | P3 挖纯逻辑 → P2-1 → P2-2 → P2-3 → P2-4 → P2-5 | 每步独立提交 |

**建议起手**：P0-2。一行调用 + `errorMsg` 清空，改动最小、验证最直接，可以先跑通一次"改动 → 编译 → 真机验证"的完整闭环，再去动 P0-1 这种风险中等的改造。

**P1-7 可与 P0 并行**：它只改 UI 文案与开关，不触碰网络层，没有与其他项冲突的风险，且是本计划中对用户实际帮助最大的一项。

**P1-6 排在第 4 版的原因**：其中百度那一项结论未知（依赖 psign 逆向结果），不应阻塞前面确定能做的修复。123 持久化、139 指纹、节流三项可先行——其中 **123 优先级最高**，因为它是唯一的"形态 B"（标识每次启动都变），且与 P0-1 的 Activity 重建问题同源。

---

## 附：已确认为良好实践的部分（勿在重构中破坏）

- DAO 层透明 AES-GCM 加解密（`SecureAccountDaos.kt`），密钥来自 Android Keystore 不可导出，AAD 用 `purpose` 做域隔离，明文历史数据惰性迁移
- Room 迁移策略：`fallbackToDestructiveMigrationFrom(1..8)` 而非全局 destructive，v9 起凭证不会被静默清库
- `HttpClients` 明确拒绝提供 SSL 绕过（`HttpClients.kt:13`）
- 迅雷验证 WebView 的 origin 白名单 + JS bridge 条件注入 + 导航到不可信页即撤销 bridge（`XunleiVerificationPolicy.kt`，有单测）
- `DownloadSaver` 的"绝不覆盖用户文件"策略（冲突时用时间戳变体）
- `ChunkDownloader` 严格的 Range 校验（`text/html` 直接失败、206 必须匹配 `Content-Range`、200 判定 `RANGE_IGNORED` 绝不按分片写整文件）
- `DownloadPathPolicy` 的路径穿越防护（有单测）
- `allowBackup=false` + `cleartextTrafficPermitted=false`
- 注释质量：几乎每个反直觉的实现都写了原因（为何 merged 放内部 data 分区、为何保存必须切 IO、为何夸克要建唯一子目录绕 `to_pdir_fid` 去重）。**重构时请保留这些注释**，它们是逆向经验的沉淀，丢失后极难重建。
