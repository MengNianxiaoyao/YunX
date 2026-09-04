# Agent.md 鈥?YunX锛堜簯鏋愶級AI 鍗忎綔鎸囧崡

鏈枃浠堕潰鍚戝湪鏈粨搴撳伐浣滅殑 AI 缂栫爜浠ｇ悊锛圕laude Code / Cursor / Copilot Agent 绛夛級銆?鐩爣锛氳浠ｇ悊鏃犻渶鍙嶅鎽哥储鍗冲彲鍐欏嚭**绗﹀悎鏈」鐩棦鏈夌害瀹?*鐨勪唬鐮併€?
**璇█绾﹀畾锛氭湰椤圭洰鎵€鏈変唬鐮佹敞閲娿€乁I 鏂囨銆佹彁浜や俊鎭€丳R 鎻忚堪缁熶竴浣跨敤涓枃銆?*

---

## 1. 椤圭洰閫熻

**YunX锛堜簯鏋愶級** 鏄竴涓?Android 缃戠洏鍒嗕韩閾炬帴瑙ｆ瀽涓庨珮閫熶笅杞藉簲鐢ㄣ€傜敤鎴风矘璐村垎浜摼鎺?鈫?娴忚鍒嗕韩鍐呭 鈫?鍙栫洿閾?鈫?鍒嗙墖骞跺彂涓嬭浇鍒版湰鍦般€?
| 椤?| 鍊?|
|---|---|
| 鍖呭悕 | `com.yunx.app` |
| 婧愮爜鏍?| `app/src/main/kotlin/com/yunx/app` |
| 璇█ | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| 鎸佷箙鍖?| Room锛圞SP 娉ㄨВ澶勭悊锛? SharedPreferences |
| 缃戠粶 | OkHttp 4.12.0 |
| minSdk / targetSdk / compileSdk | 23 / 34 / 36 |
| JVM target | 17 |
| 寮€婧愬崗璁?| GNU AGPL-3.0 |

鏀寔骞冲彴锛氬じ鍏嬨€乁C銆佽繀闆枫€佺櫨搴︺€?39锛堝拰褰╀簯锛夈€?23 浜戠洏銆?
---

## 2. 鐩綍缁撴瀯涓庤亴璐?
```
app/src/main/kotlin/com/yunx/app/
鈹溾攢鈹€ MainActivity.kt              # 鍗?Activity 鍏ュ彛
鈹溾攢鈹€ YunXApp.kt                   # Application锛屽叏灞€鍒濆鍖?鈹溾攢鈹€ crash/                       # 宕╂簝鎹曡幏涓庡穿婧冨睍绀洪〉
鈹?  鈹溾攢鈹€ CrashHandler.kt
鈹?  鈹斺攢鈹€ CrashActivity.kt
鈹溾攢鈹€ util/
鈹?  鈹溾攢鈹€ LogRedactor.kt           # 鈽?鏃ュ織鑴辨晱锛圲RL/Cookie/token 鎵撶爜锛?鈹?  鈹斺攢鈹€ LogExporter.kt
鈹溾攢鈹€ data/
鈹?  鈹溾攢鈹€ network/                 # 鍚勫钩鍙?API 灏佽 + 甯搁噺 + 寮傚父
鈹?  鈹?  鈹溾攢鈹€ {Quark,UC,Xunlei,Baidu,C139,Pan123}Api.kt
鈹?  鈹?  鈹溾攢鈹€ {...}Constants.kt
鈹?  鈹?  鈹溾攢鈹€ ShareLinkParser.kt   # 鈽?缁熶竴鍒嗕韩閾炬帴璇嗗埆鍏ュ彛
鈹?  鈹?  鈹溾攢鈹€ HttpClients.kt       # OkHttp 瀹㈡埛绔伐鍘?鈹?  鈹?  鈹溾攢鈹€ QuarkCdn.kt / XunleiDeviceFingerprint.kt
鈹?  鈹?  鈹斺攢鈹€ model/               # DTO锛歋hareSession / ShareFile / DownloadLink 绛?鈹?  鈹溾攢鈹€ repository/              # 涓氬姟浠撳簱灞傦紙Account* / Resolve* 鎴愬瀛樺湪锛?鈹?  鈹?  鈹溾攢鈹€ ShareResolveRepository.kt   # 鈽?瑙ｆ瀽浠撳簱鍏叡鎺ュ彛
鈹?  鈹?  鈹斺攢鈹€ {骞冲彴}{Account,Resolve}Repository.kt
鈹?  鈹溾攢鈹€ db/                      # Room锛欵ntity + Dao + AppDatabase
鈹?  鈹?  鈹溾攢鈹€ AppDatabase.kt       # 鈽?鐗堟湰鍙蜂笌 Migration 闆嗕腑绠＄悊
鈹?  鈹?  鈹溾攢鈹€ SecureAccountDaos.kt # 鈽?鍑瘉 Dao 鍔犲瘑瑁呴グ鍣?鈹?  鈹?  鈹斺攢鈹€ {骞冲彴}Account{Entity,Dao}.kt / DownloadTask* / Bookmark*
鈹?  鈹溾攢鈹€ download/                # 涓嬭浇寮曟搸锛堟湰椤圭洰鏈€澶嶆潅鐨勬ā鍧楋紝瑙?搂5锛?鈹?  鈹?  鈹溾攢鈹€ DownloadManager.kt   # 鈽?浠诲姟璋冨害 / 鍒嗙墖瑙勫垝 / 鏂偣缁紶
鈹?  鈹?  鈹溾攢鈹€ ChunkDownloader.kt   # 鍗曞垎鐗?Range 璇锋眰
鈹?  鈹?  鈹溾攢鈹€ HlsDownloader.kt / HlsRequestPolicy.kt
鈹?  鈹?  鈹溾攢鈹€ HttpRangePolicy.kt / DownloadPathPolicy.kt
鈹?  鈹?  鈹溾攢鈹€ DownloadSaver.kt / DownloadService.kt锛堝墠鍙版湇鍔★級
鈹?  鈹?  鈹斺攢鈹€ DownloadPlatform.kt  # 鈽?骞冲彴鏍囪瘑瀛楃涓插父閲?鈹?  鈹溾攢鈹€ security/CredentialCipher.kt    # 鈽?Android Keystore 鍑瘉鍔犺В瀵?鈹?  鈹溾攢鈹€ backup/                  # 璁よ瘉澶囦唤锛堝彛浠ゆ淳鐢熷瘑閽?+ AES-GCM锛?鈹?  鈹溾攢鈹€ update/UpdateChecker.kt
鈹?  鈹斺攢鈹€ prefs/SettingsRepository.kt     # 鈽?鎵€鏈夎缃」鐨勫敮涓€鍏ュ彛
鈹斺攢鈹€ ui/
    鈹溾攢鈹€ MainScreen.kt            # 鈽?涓诲鍣細搴曢儴瀵艰埅 + 瑕嗙洊灞傚紡浜岀骇椤甸潰
    鈹溾攢鈹€ SnackbarController.kt    # 鈽?鍏ㄥ眬 Snackbar 閫氶亾
    鈹溾攢鈹€ navigation/MainTab.kt    # 搴曢儴 4 Tab 鏋氫妇
    鈹溾攢鈹€ screens/                 # 涓€绾?浜岀骇椤甸潰 + 鍚勫钩鍙?Sheet
    鈹溾攢鈹€ resolve/                 # 瑙ｆ瀽缁撴灉椤碉紙ShareDetailScreen 绛夛級
    鈹溾攢鈹€ login/                   # 鍚勫钩鍙扮櫥褰曢〉
    鈹溾攢鈹€ viewmodel/               # 姣忎釜鍔熻兘涓€涓?ViewModel + 鍐呭祵 Factory
    鈹溾攢鈹€ components/ items/       # 鍙鐢ㄥ皬缁勪欢
    鈹斺攢鈹€ theme/                   # Color / Type / Theme / ThemeController
```

---

## 3. 蹇呴』閬靛畧鐨勯」鐩害瀹?
杩濆弽杩欎簺绾﹀畾鐨勪唬鐮佸嵆浣胯兘缂栬瘧锛屼篃浼氫笌鐜版湁浠ｇ爜椋庢牸鑴辫妭锛?*璇峰姟蹇呭厛璇诲悓绫绘枃浠跺啀鍔ㄦ墜**銆?
### 3.1 ViewModel锛氳嚜瀹氫箟 Factory锛屼笉鐢?DI 妗嗘灦

椤圭洰**娌℃湁** Hilt/Koin銆傛瘡涓?ViewModel 鍐呭祵涓€涓?`Factory`锛屼緷璧栫敱 `MainScreen.kt` 鎵嬪伐浼犲叆銆?
```kotlin
class BookmarkViewModel(private val dao: BookmarkDao) : ViewModel() {
    // ...
    class Factory(private val dao: BookmarkDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BookmarkViewModel(dao) as T
    }
}
```

### 3.2 鐢ㄦ埛鎻愮ず锛氱粺涓€璧板叏灞€ Snackbar

**涓嶈**鍦?ViewModel 閲屾寔鏈?`SnackbarHostState`锛屼篃涓嶈鐢?Toast銆?
```kotlin
import com.yunx.app.ui.SnackbarController
SnackbarController.show("宸叉敹钘忓埌銆?cat銆?)
```

椤甸潰渚х敤 `rememberGlobalSnackbarHostState()` 鎴?`GlobalSnackbarHost()` 娓叉煋銆?**娉ㄦ剰**锛氬叏灞忚鐩栧眰椤甸潰浼氶伄浣?`MainScreen` 鐨勫涓伙紝瑕嗙洊灞傚唴闇€鑷甫 `SnackbarHost`銆?
### 3.3 浜岀骇椤甸潰锛歚AnimatedVisibility` 鍏ㄥ睆瑕嗙洊灞傦紝鑰岄潪 NavHost

椤圭洰**娌℃湁** Navigation-Compose 璺敱琛ㄣ€備簩绾ч〉闈紙About / Theme / Bookmark鈥︼級鐨勬ā寮忔槸锛?`MainScreen` 鍐呬竴涓?`showXxx: Boolean` 鐘舵€?+ `AnimatedVisibility` 鍙犲姞涓€灞傚叏灞?Composable銆?
```kotlin
AnimatedVisibility(
    visible = showBookmarks,
    enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.96f),
    exit  = fadeOut(tween(180)) + scaleOut(targetScale = 0.96f)
) {
    BookmarkScreen(onBack = { showBookmarks = false }, /* ... */)
}
```

瑕嗙洊灞傞〉闈㈠繀椤昏嚜甯?`BackHandler { onBack() }`銆?
### 3.4 璁剧疆椤癸細鍙兘鍔犲湪 `SettingsRepository`

鎵€鏈夊亸濂借鍐欓泦涓湪 `data/prefs/SettingsRepository.kt`锛圫haredPreferences 鍚?`yunx_settings`锛夈€?鍐欐硶锛歚var` + 鑷畾涔?getter/setter锛屽€煎煙鐢?`coerceIn` 鍏滀綇锛岄粯璁ゅ€兼斁 `companion object` 甯搁噺銆?
```kotlin
var maxConcurrentDownloads: Int
    get() = prefs.getInt("max_concurrent_downloads", DEFAULT_MAX_CONCURRENT_DOWNLOADS)
    set(value) { prefs.edit().putInt("max_concurrent_downloads", value.coerceIn(1, 10)).apply() }
```

### 3.5 涓嬭浇寮曟搸锛氫緷璧栭€氳繃 Provider 闂寘娉ㄥ叆锛屼繚璇併€屾敼璁剧疆鍗虫椂鐢熸晥銆?
`DownloadManager` 涓嶇洿鎺ユ寔鏈?`SettingsRepository`锛岃€屾槸鎺ユ敹 lambda锛?
```kotlin
threadProvider     = { platform -> settings.downloadThreadsFor(platform) }
concurrencyProvider = { settings.maxConcurrentDownloads }
speedLimitProvider  = { settings.downloadSpeedLimit }
```

鏂板鍙皟鍙傛暟鏃?*娌跨敤杩欎釜妯″紡**锛屼笉瑕佸湪鏋勯€犳椂鍙栧揩鐓у€笺€?
### 3.6 鍑瘉瀹夊叏锛欳ookie / JWT 蹇呴』鍔犲瘑钀藉簱

- 璐﹀彿 Dao 涓€寰嬬粡 `SecureAccountDaos.xxx(rawDao, cipher)` 瑁呴グ鍚庝娇鐢紝**涓嶈鐩存帴鐢?`rawXxxAccountDao()`**銆?- 涓嬭浇浠诲姟鐨勮姹傚ご锛堝惈 Cookie锛夌粡 `CredentialCipher.encrypt(json, "download.requestHeaders")` 鍔犲瘑銆?- 鎵撴棩蹇楁秹鍙?URL / Cookie / token 鏃跺繀椤昏繃 `LogRedactor`锛歚LogRedactor.url(url)`銆?
### 3.7 Room 杩佺Щ锛氬繀椤诲啓 Migration锛岀姝㈢牬鍧忔€ц縼绉?
`AppDatabase.kt` 鐜颁负 **version = 13**銆傛柊澧炶〃/瀛楁鐨勬祦绋嬶細

1. `entities` 鏁扮粍杩藉姞 Entity
2. `version` +1
3. 鏂板 `abstract fun xxxDao()`
4. 鍐?`MIGRATION_N_N+1`锛堟柊澧炶〃鐢?`CREATE TABLE IF NOT EXISTS`锛屼笉鍔ㄦ棫琛級
5. 娉ㄥ唽鍒?`.addMigrations(...)`

`fallbackToDestructiveMigrationFrom(1..8)` 浠呴€傜敤浜庢棭鏈熷紑鍙戠増锛?*v9 璧峰繀椤讳繚鐣欑敤鎴峰嚟璇佷笌涓嬭浇浠诲姟**銆?
### 3.8 骞冲彴鏍囪瘑锛氱敤 `DownloadPlatform` 甯搁噺锛屼笉瑕佽８瀛楃涓?
```kotlin
object DownloadPlatform {
    const val QUARK = "quark";  const val UC = "uc";     const val XUNLEI = "xunlei"
    const val BAIDU = "baidu";  const val C139 = "c139"; const val PAN123 = "pan123"
    const val GENERIC = "generic"   // 鎵嬪姩娣诲姞 / 搴旂敤鑷洿鏂?}
```

### 3.9 鏂板骞冲彴鏀寔鏃剁殑瀹屾暣娓呭崟

鎴愬鍒涘缓 `{X}Api.kt` / `{X}Constants.kt` / `{X}AccountRepository.kt` / `{X}ResolveRepository.kt`锛堝疄鐜?`ShareResolveRepository`锛? `{X}Account{Entity,Dao}.kt` / `{X}AccountSheet.kt` / `{X}CloudScreen.kt` / `{X}LoginScreen.kt` / `{X}AccountViewModel.kt` / `{X}CloudViewModel.kt`锛屽苟鍦?`ShareLinkParser`銆乣DownloadPlatform`銆乣AppDatabase` 涓櫥璁般€?
---

## 4. 楠岃瘉


鍐欏畬浠ｇ爜鍚庨€愰」鑷煡锛岀劧鍚庝氦浠橈細

1. **import 鏄惁榻愬叏**锛氭柊鐢ㄥ埌鐨?Composable銆佸姩鐢?API銆佸浘鏍囥€佸崗绋?API 閮芥湁瀵瑰簲 import銆?2. **瀹為獙鎬?API 娉ㄨВ**锛氳涓嬫柟銆屽父瑙佺紪璇戝潙銆嶈〃銆?3. **绗﹀彿涓€鑷存€?*锛氭敼浜嗗嚱鏁扮鍚嶅悗锛宍grep` 涓€閬嶆棫绛惧悕/鏃ц皟鐢ㄧ偣锛岀‘璁ゆ棤娈嬬暀銆?4. **Room 涓€鑷存€?*锛氭敼浜嗚〃缁撴瀯鍒?`version` 宸?+1銆丮igration 宸插啓涓斿凡娉ㄥ唽銆?5. **鍛藉悕涓庨鏍?*锛氫笌鍚岀洰褰曞悓绫绘枃浠朵竴鑷淬€?


### 甯歌缂栬瘧鍧?
| 鍧?| 澶勭悊 |
|---|---|
| `FlowRow` / `FilterChip` | 闇€ `@OptIn(ExperimentalLayoutApi::class)` / `ExperimentalMaterial3Api` |
| `combinedClickable` | 闇€ `@OptIn(ExperimentalFoundationApi::class)` |
| 鍥炬爣鎵句笉鍒?| 宸插紩鍏?`material-icons-extended`锛岀‘璁ゅ浘鏍囧悕涓?`Outlined`/`Filled` 鍛藉悕绌洪棿 |
| Room 缂栬瘧鎶?schema 閿?| 妫€鏌?`version` 鏄惁 +1銆丮igration 鏄惁娉ㄥ唽 |

---

## 5. 涓嬭浇寮曟搸閲嶇偣绗旇锛堟敼鍔ㄥ墠蹇呰锛?
`DownloadManager.kt` 鏄叏椤圭洰鏈€瀹规槗鏀归敊鐨勬枃浠讹紝浠ヤ笅鏈哄埗閮芥槸涓轰慨澶嶇湡瀹炵嚎涓婇棶棰樿€屽瓨鍦ㄧ殑锛?*涓嶈闅忔剰"绠€鍖?**銆?
### 5.1 浠诲姟姹?+ 寮规€у尯妯″瀷

```
鍒嗙墖瑙勫垝锛歝hunkCount = chunkCountFor(total, threads)
          涓绘睜 = chunkCount 脳 0.7   鈫?鏂囦欢 part_0 鈥?part_{n-1}锛堢瓑鍒嗗尯闂达級
          寮规€у尯 = 鍓╀綑 30% 瀛楄妭     鈫?鏂囦欢 seg_{start}_{end}.part锛堟寜搴忛 4MB 鍧楋級
骞跺彂 worker = effectiveWorkers锛堜俊鍙烽噺 Semaphore 鍥哄畾瀹归噺锛岀粷涓嶆墜鍔?release锛?```

- worker **寰幆棰嗙墖**锛屾參鐗囦笉闃诲鍏朵粬绾跨▼ 鈫?鏍规不"灏鹃儴骞跺彂濉岀缉"銆?- 寮规€у尯鐢?`ElasticAllocator` **鎸夊瓧鑺傞『搴?*鍒嗛厤锛屾浛浠ｆ棭鏈熺殑"涓偣鍔堝垎"锛堝妶鍒嗕細瀵艰嚧涓绘睜鑰楀敖鐬棿鍏ㄩ儴绾跨▼娑屽叆銆佸尯闂磋法搴︾炕鍊嶃€佽繛鎺ュ鐢ㄧ巼宕╁ 鈫?涓悗娈垫帀閫燂級銆?
### 5.2 `chunkCountFor` 鐨勭湡瀹炶涔夛紙鏄撹璇锛?
```kotlin
val minChunkBytes = 1 * 1024 * 1024L      // 銆屽崟鐗囨渶灏?1MB銆? 鍒嗙墖鏁颁笂闄愰榾锛屼笉鏄?姣忕墖灏辨槸 1MB"
val bySize = when {                        // 鎸夋枃浠跺ぇ灏忕殑鍩虹鍒嗙墖鏁?    total < 5MB -> 1;  total < 50MB -> 8;  total < 500MB -> 32;  else -> 64
}
val want = maxOf(bySize, threads * 8)      // 姣忕嚎绋嬪钩鍧?8 鐗囩泩浣?return minOf(want, (total / minChunkBytes).toInt(), 512)   // 512 涓虹‖灏侀《
```

**瀹為檯鍗曠墖澶у皬 = `ceil(total / chunkCount)`**锛屽苟闈炲浐瀹?1MB鈥斺€斿ぇ鏂囦欢鐨勫崟鐗囪繙澶т簬 1MB锛岀嚎绋嬫暟瓒婇珮銆佸垎鐗囨暟灏侀《鍚庡崟鐗囪秺澶с€?
鍚屾椂娉ㄦ剰锛氬垎鐗囨暟杩樹細琚?`total / minChunkBytes` 澶逛綇锛屾墍浠?*灏忔枃浠剁殑鍒嗙墖鏁帮紙杩涜€屽疄闄呭苟鍙戣矾鏁帮級鍙兘浣庝簬鐢ㄦ埛璁剧疆鐨勭嚎绋嬫暟**锛岃繖鏄綋鍓嶈璁′负閬垮厤纰庣墖鍖栬€屽仛鐨勫彇鑸嶃€傛帓鏌?绾跨▼鏁拌缃病鐢熸晥"绫婚棶棰樻椂鍏堟牳瀵硅繖涓€灞傘€?
### 5.3 CDN 骞跺彂闄愬埗锛堢‖缂栫爜涓婇檺鐨勭敱鏉ワ級

```kotlin
private const val RANGE_WORKERS_CAP = 8          // 杩呴浄绛?CDN 鍗曟枃浠跺苟鍙?Range 闃堝€?private const val RANGE_IGNORED_TOLERANCE = 3    // 鍋跺彂 200 瀹瑰繊娆℃暟锛岃秴杩囨墠鍥為€€鍗曟祦
private const val STAGGER_CAP = 8; STAGGER_MS = 25L  // 閿欏嘲寤鸿繛锛屽钩鎽?TCP/TLS 绐佸彂
```

- 杩呴浄骞跺彂瓒呰繃绾?8 浼氳闄嶇骇涓?`200` 鏁存枃浠跺搷搴旓紙蹇界暐 Range锛夆啋 鏁翠换鍔″洖閫€鍗曟祦銆侀€熷害鏆磋穼銆?  鏁?`SettingsRepository.XUNLEI_DOWNLOAD_THREADS = 8` **鍥哄畾涓嶅彲鏀?*锛宍setDownloadThreads` 瀵硅繀闆风洿鎺?return銆?- 鎻愰珮浠讳綍骞冲彴鐨勫苟鍙戜笂闄愬墠锛?*蹇呴』瀹炴祴鏄惁瑙﹀彂 200 闄嶇骇**锛?骞跺彂瓒婂ぇ瓒婂揩"鍦ㄧ綉鐩?CDN 涓婁笉鎴愮珛銆?
### 5.4 鏂偣缁紶涓庡垎鐗囪鍒掔鍚?
`plan.txt` 鍐呭褰㈠ `chunks=37 total=39536652 main=25`銆?璺ㄤ細璇濇敼绾跨▼鏁版垨鏈嶅姟鍣ㄦ帰娴嬪ぇ灏忓彉鍖栦細浣挎棫 `part_i` 鍖洪棿閿欎綅 鈫?妫€娴嬪埌绛惧悕涓嶄竴鑷存椂**鏁寸洰褰曟竻绌洪噸涓?*銆傛敼鍔ㄥ垎鐗囪鍒掔畻娉曚細璁╂墍鏈夌敤鎴风殑鐜板瓨鏂偣澶辨晥锛岄渶鍦?PR 閲岃鏄庛€?
鍒嗙墖缂撳瓨鐩綍锛歚context.externalCacheDir/download_tmp/{taskId}/`
锛堝嵆 `/storage/emulated/0/Android/data/com.yunx.app/cache/download_tmp/{id}`锛?
### 5.5 杩涘害钀界洏蹇呴』鑺傛祦锛圓NR 鍘嗗彶锛?
`dao.updateProgress` 鍐欏簱浼氳Е鍙戝叏琛?Flow 閲嶅彂 鈫?涓荤嚎绋嬪叏鍒楄〃閲嶇粍銆傛棭鏈熸寜瀛楄妭锛?56KB锛夎妭娴佸鑷撮珮閫熶笅杞芥瘡绉掑啓搴撳嚑鍗佹 鈫?**ANR**銆?鐜颁负 **鎸夋椂闂磋妭娴?500ms**锛坄progressPersistIntervalMs`锛夛紝UI 杩涘害璧板唴瀛?`_stats`锛坄StateFlow<Map<Long, DownloadStats>>`锛夐珮棰戝睍绀猴紝DB 浣庨鎸佷箙鍖栥€?**涓嶈鎶婅惤鐩樻敼鍥炴寜瀛楄妭瑙﹀彂銆?*

### 5.6 骞跺彂瀹夊叏瑕佺偣

- `activeJobs` 鐨勬敞鍐?绉婚櫎鍏ㄧ▼鍦?`jobsLock` 鍐咃紝闃?start/pause/remove 鐨?TOCTOU 绔炴€併€?- `finally` 涓彧绉婚櫎**鑷繁娉ㄥ唽鐨?* deferred锛坄if (activeJobs[id] === deferred)`锛夛紝鍚﹀垯"鏆傚仠鍚庣珛鍗虫仮澶?浼氳鍒犳柊浠诲姟娉ㄥ唽銆?- `taskLocks` **涓嶅湪 finally 娓呯悊**锛屽惁鍒欎細璇垹鏂颁换鍔＄殑閿佸鑷村苟鍙戝啓鍒嗙墖銆?- 鏆傚仠鏃朵互**纾佺洏 part/seg 鐪熷疄闀垮害**鍥炲啓杩涘害锛岄伩鍏嶆仮澶嶆椂杩涘害鍥炶烦銆?- 杩涘害绱姞涓€寰?`minOf(..., total)` 閽冲埗锛岄槻鏄剧ず"宸蹭笅杞?> 鎬诲ぇ灏?銆?
---

## 6. 浠ｇ悊宸ヤ綔瑙勮寖

### 6.1 鍔ㄦ墜鍓?
1. **鍏堣鍚岀被鏂囦欢**鍐嶅啓鏂颁唬鐮侊紙濡傚姞椤甸潰鍏堣 `BookmarkScreen.kt` / `AboutScreen.kt`锛夈€?2. 娑夊強涓嬭浇寮曟搸銆丷oom 杩佺Щ銆佸嚟璇佸姞瀵嗙殑鏀瑰姩锛?*鍏堣鏄庢柟妗堝苟绛夌敤鎴风‘璁?*鍐嶆敼銆?3. 涓嶅紩鍏ユ柊渚濊禆銆佹柊鏋舵瀯锛圖I 妗嗘灦銆丯avigation-Compose銆佸叾浠栫綉缁滃簱锛夆€斺€旈櫎闈炵敤鎴锋槑纭姹傘€傝嫢蹇呴』寮曞叆鏂颁緷璧栵紝璇峰悜鐢ㄦ埛鍛婄煡锛岃鏄庡繀瑕佹€э紝骞跺彇寰楀悓鎰?
### 6.2 鏀瑰姩涓?
- 娉ㄩ噴鐢ㄤ腑鏂囷紝瑙ｉ噴**涓轰粈涔?*杩欐牱鍐欙紙灏ゅ叾鏄粫杩囨煇涓钩鍙伴檺鍒剁殑 workaround锛夛紝椤圭洰鐜版湁娉ㄩ噴鍗充负鑼冧緥銆?- 淇濇寔"鏁版嵁灞?鈫?ViewModel 鈫?UI 鈫?鍏ュ彛鎺ョ嚎"鐨勯『搴忔帹杩涳紝鏀瑰畬鍋氫竴娆＄鍙蜂竴鑷存€ф鏌ワ紙`grep` 鏃х鍚嶆畫鐣欙級銆?- 涓嶅仛瓒呭嚭浠诲姟鑼冨洿鐨勯『鎵嬮噸鏋勩€?
### 6.3 浜や粯鏃?
- 鏁版嵁搴撶増鏈彉鏇淬€佸垎鐗囪鍒掑彉鏇淬€佸苟鍙戜笂闄愬彉鏇达紝蹇呴』鍦ㄦ€荤粨閲屾樉寮忔爣娉ㄥ吋瀹规€у奖鍝嶃€?- 鎬ц兘绫绘敼鍔ㄧ粰鍑哄彲楠岃瘉鏂规硶锛堝"鐪?`鍒嗙墖瑙勫垝:` 鏃ュ織涓殑 `threads` / `effectiveWorkers`"锛夛紝涓嶈鍙０绉板彉蹇簡銆?

## 7. 闀挎湡椋庨櫓鎻愮ず

| 浜嬮」 | 璇存槑 |
|---|---|
| 杩呴浄骞跺彂鍥哄畾 8 | `XUNLEI_DOWNLOAD_THREADS` / `RANGE_WORKERS_CAP` 鏄?*鏈夋剰璁捐鑰岄潪 bug**锛屼笉瑕?椤烘墜浼樺寲"鎺?|
| 鐧惧害缃戠洏椋庢帶 | README 宸茶绀轰笉寤鸿浣跨敤锛涘ぇ鏂囦欢闄愰€熸彁绀鸿 `baiduLimitHintDismissed` |
| 鍗忚閫嗗悜鎺ュ彛鏄撳け鏁?| 鍚勫钩鍙?API 闅忓畼鏂硅皟鏁磋€屽け鏁堬紝浠ュ疄闄呰繍琛岀粨鏋滀负鍑嗭紝涓嶈鍋囪鎺ュ彛绋冲畾 |
| 鍒嗙墖瑙勫垝绠楁硶鍙樻洿 | 浼氫娇鍏ㄤ綋鐢ㄦ埛鐨勭幇瀛樻柇鐐瑰け鏁堬紙`plan.txt` 绛惧悕涓嶅尮閰?鈫?娓呯┖閲嶄笅锛夛紝鏀瑰姩闇€鍦?PR 涓鏄?|

---

## 8. 杈圭晫涓庡厤璐?
- 鏈」鐩粎渚涗釜浜哄涔犱笌鎶€鏈氦娴侊紝**涓嶅緱鐢ㄤ簬鍟嗕笟鐢ㄩ€旀垨鍊掑崠**銆?- 涓嶈鍦ㄤ唬鐮併€佹棩蹇椼€佹彁浜や俊鎭€両ssue 涓啓鍏ョ湡瀹炶处鍙枫€丆ookie銆乼oken銆佹墜鏈哄彿绛夋晱鎰熶俊鎭紱绀轰緥缁熶竴鐢ㄥ崰浣嶇銆?- 娑夊強缃戠洏鍗忚鐨勬敼鍔ㄨ淇濇寔"浠呰В鏋愮敤鎴疯嚜宸辨湁鏉冭闂殑鍒嗕韩鍐呭"杩欎竴杈圭晫锛屼笉瀹炵幇缁曡繃浠樿垂銆佺牬瑙ｆ潈闄愩€佹壒閲忕埇鍙栫瓑鑳藉姏銆?
