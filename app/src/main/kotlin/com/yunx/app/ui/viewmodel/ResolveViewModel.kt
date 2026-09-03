package com.yunx.app.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.R
import com.yunx.app.data.error.YunxErrorClassifier
import com.yunx.app.data.metrics.ResolveMetricErrorKind
import com.yunx.app.data.metrics.ResolveMetricOperation
import com.yunx.app.data.metrics.ResolveMetricSpan
import com.yunx.app.data.metrics.RequestStage
import com.yunx.app.data.db.BookmarkEntity
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.BaiduConstants
import com.yunx.app.data.network.C139Constants
import com.yunx.app.data.network.CloudCapabilities
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.data.network.QuarkConstants
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.UCConstants
import com.yunx.app.data.network.XunleiConstants
import com.yunx.app.data.network.model.CloudCredential
import com.yunx.app.data.network.model.DownloadCleanup
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import com.yunx.app.data.repository.BaiduAccountRepository
import com.yunx.app.data.repository.BaiduResolveRepository
import com.yunx.app.data.repository.BookmarkRepository
import com.yunx.app.data.repository.C139AccountRepository
import com.yunx.app.data.repository.C139ResolveRepository
import com.yunx.app.data.repository.Pan123AccountRepository
import com.yunx.app.data.repository.Pan123ResolveRepository
import com.yunx.app.data.repository.QuarkAccountRepository
import com.yunx.app.data.repository.QuarkResolveRepository
import com.yunx.app.data.repository.ShareResolveRepository
import com.yunx.app.data.repository.UCAccountRepository
import com.yunx.app.data.repository.UCResolveRepository
import com.yunx.app.data.repository.XunleiAccountRepository
import com.yunx.app.data.repository.XunleiResolveRepository
import com.yunx.app.data.task.BatchTaskRunner
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.text.UiText
import com.yunx.app.ui.text.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ResolveBatchStage { COLLECTING, SAVING, FETCHING_LINKS }

data class ResolveBatchProgress(
    val stage: ResolveBatchStage,
    val completed: Int = 0,
    val total: Int = 0
)

sealed interface ResolveUiState {
    data object Idle : ResolveUiState
    data object Loading : ResolveUiState
    data class Detail(
        val session: ShareSession,
        val files: List<ShareFile>,
        val errorBanner: UiText? = null,
        val nextCursor: String? = null,
        val isLoadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false
    ) : ResolveUiState
    data class Error(val message: UiText) : ResolveUiState
}

/** 解析流程所需的平台公共信息；平台协议细节仍由各自 Repository 负责。 */
data class ResolvePlatformContext(
    val platform: SharePlatform,
    val repository: ShareResolveRepository,
    val credentialProvider: suspend () -> CloudCredential?,
    val freshCredentialProvider: suspend (CloudCredential) -> CloudCredential,
    val defaultDirFid: String,
    val displayName: String,
    val capabilities: CloudCapabilities,
    val downloadHeadersProvider: (String) -> Map<String, String>
)

object ResolvePlatformDefaults {
    fun defaultDirFid(platform: SharePlatform): String = when (platform) {
        SharePlatform.QUARK -> QuarkConstants.DEFAULT_PDIR_FID
        SharePlatform.UC -> UCConstants.DEFAULT_PDIR_FID
        SharePlatform.XUNLEI, SharePlatform.C139, SharePlatform.PAN123 -> "0"
        SharePlatform.BAIDU -> ""
    }

    fun displayName(platform: SharePlatform): String = when (platform) {
        SharePlatform.QUARK -> "夸克网盘"
        SharePlatform.UC -> "UC 网盘"
        SharePlatform.XUNLEI -> "迅雷网盘"
        SharePlatform.BAIDU -> "百度网盘"
        SharePlatform.C139 -> "139 网盘"
        SharePlatform.PAN123 -> "123云盘"
    }

    fun capabilities(platform: SharePlatform): CloudCapabilities = CloudCapabilities(
        name = displayName(platform),
        rootDir = when (platform) {
            SharePlatform.QUARK, SharePlatform.UC, SharePlatform.PAN123 -> "0"
            SharePlatform.XUNLEI -> ""
            SharePlatform.BAIDU, SharePlatform.C139 -> "/"
        },
        requiresTransferForShareDownload = platform == SharePlatform.QUARK ||
            platform == SharePlatform.XUNLEI || platform == SharePlatform.BAIDU,
        supportsShareVideoPreview = platform == SharePlatform.UC
    )

    fun downloadHeaders(platform: SharePlatform, credential: String): Map<String, String> = when (platform) {
        SharePlatform.QUARK -> mapOf(
            "Cookie" to credential,
            "User-Agent" to QuarkConstants.API_USER_AGENT,
            "Referer" to QuarkConstants.DOWNLOAD_REFERER
        )
        SharePlatform.UC -> mapOf(
            "Cookie" to credential,
            "User-Agent" to UCConstants.USER_AGENT,
            "Referer" to UCConstants.DOWNLOAD_REFERER,
            "Origin" to UCConstants.WEB_ORIGIN
        )
        SharePlatform.XUNLEI -> mapOf("User-Agent" to XunleiConstants.APP_UA)
        SharePlatform.BAIDU -> mapOf(
            "Cookie" to credential,
            "User-Agent" to BaiduConstants.UA_NETDISK
        )
        SharePlatform.C139 -> mapOf("User-Agent" to C139Constants.PC_UA)
        SharePlatform.PAN123 -> mapOf(
            "User-Agent" to Pan123Constants.WEB_UA,
            "Referer" to Pan123Constants.DOWNLOAD_REFERER
        )
    }
}

/**
 * 解析页 ViewModel：分享解析状态机 + 目录导航 + 下载直链。
 * 支持夸克 / UC / 迅雷，按链接自动路由到对应平台仓库与凭证。
 */
class ResolveViewModel(
    private val accountRepository: QuarkAccountRepository,
    private val resolveRepository: QuarkResolveRepository,
    private val ucAccountRepository: UCAccountRepository,
    private val ucResolveRepository: UCResolveRepository,
    private val xunleiAccountRepository: XunleiAccountRepository,
    private val xunleiResolveRepository: XunleiResolveRepository,
    private val baiduAccountRepository: BaiduAccountRepository,
    private val baiduResolveRepository: BaiduResolveRepository,
    private val c139AccountRepository: C139AccountRepository,
    private val c139ResolveRepository: C139ResolveRepository,
    private val pan123AccountRepository: Pan123AccountRepository,
    private val pan123ResolveRepository: Pan123ResolveRepository,
    private val downloadManager: DownloadManager,
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    var uiState by mutableStateOf<ResolveUiState>(ResolveUiState.Idle)
        private set

    var downloadLink by mutableStateOf<DownloadLink?>(null)
        private set

    var downloadError by mutableStateOf<UiText?>(null)
    private set

    /** 获取下载直链中（UI 显示加载弹窗） */
    var isFetchingDownloadLink by mutableStateOf(false)
        private set

    /** 待转存文件（转存弹窗）；null 表示未在转存流程 */
    var saveTarget by mutableStateOf<ShareFile?>(null)
        private set

    /** 转存中（UI 显示加载） */
    var isSaving by mutableStateOf(false)
        private set

    /** 转存结果消息（Toast） */
    var saveMessage by mutableStateOf<UiText?>(null)
        private set

    /** 当前分享是否支持转存（夸克 / UC / 迅雷 / 百度 / 139 / 123） */
    val canSave: Boolean
        get() = currentContext().capabilities.supportsShareSave

    /** 当前分享是否为迅雷（UI 选择迅雷版转存目录选择器） */
    val isSaveXunlei: Boolean
        get() = currentPlatform == SharePlatform.XUNLEI

    /** 当前分享是否为百度（UI 选择百度版转存目录选择器） */
    val isSaveBaidu: Boolean
        get() = currentPlatform == SharePlatform.BAIDU

    /** 当前分享是否为百度（限速提示判断用） */
    val isBaidu: Boolean
        get() = currentPlatform == SharePlatform.BAIDU

    /** 当前分享是否为 139（UI 选择 139 版转存目录选择器） */
    val isSaveC139: Boolean
        get() = currentPlatform == SharePlatform.C139

    /** 当前分享是否为 UC（UI 选择 UC 版转存目录选择器） */
    val isSaveUC: Boolean
        get() = currentPlatform == SharePlatform.UC

    /** 当前分享是否为 123（UI 选择 123 版转存目录选择器） */
    val isSavePan123: Boolean
        get() = currentPlatform == SharePlatform.PAN123

    /** 请求转存：记录目标文件并打开目录选择弹窗 */
    fun requestSave(file: ShareFile) {
        saveTarget = file
        saveMessage = null
    }

    fun dismissSave() {
        saveTarget = null
        isSaving = false
    }

    fun consumeSaveMessage() {
        saveMessage = null
    }

    /** 通过当前平台 Repository 转存到指定目录，成功后自动关闭弹窗。 */
    fun saveToCloud(toDirFid: String) {
        val file = saveTarget ?: return
        val s = session ?: return
        viewModelScope.launch {
            isSaving = true
            try {
                val credential = currentTypedCredential()
                if (credential == null || !credential.isUsable()) {
                    saveMessage = uiText(R.string.resolve_error_login_platform, platformName())
                    return@launch
                }
                currentRepo().transferFile(s, file, toDirFid, credential)
                    .onSuccess {
                        saveMessage = uiText(R.string.resolve_save_success, platformName())
                        saveTarget = null
                    }
                    .onFailure { error ->
                        saveMessage = errorText(error, R.string.resolve_error_save_failed)
                    }
            } finally {
                isSaving = false
            }
        }
    }

    /** 下载已入队事件：触发后由 UI 切换到下载页 */
    var downloadStarted by mutableStateOf(false)
        private set

    // ---------- 长按多选（解析页文件列表） ----------

    /** 多选模式（长按进入） */
    var multiSelectMode by mutableStateOf(false)
        private set

    private val _selected = mutableStateListOf<ShareFile>()
    val selected: List<ShareFile> get() = _selected

    /** 批量处理中（UI 显示加载弹窗） */
    var isBatchWorking by mutableStateOf(false)
        private set

    /** 批量下载进度（如 "2/5"）；null 表示未显示进度 */
    var batchProgress by mutableStateOf<ResolveBatchProgress?>(null)
        private set

    /** 批量处理中断请求（UI 点「中断」后置 true，批量循环中检查并跳出） */
    private var batchCancelRequested = false

    /** 中断当前批量处理（批量下载/批量转存） */
    fun cancelBatch() {
        batchCancelRequested = true
    }

    fun enterMultiSelect(file: ShareFile) {
        multiSelectMode = true
        _selected.clear()
        _selected.add(file)
    }

    fun toggleSelect(file: ShareFile) {
        if (_selected.contains(file)) _selected.remove(file) else _selected.add(file)
    }

    fun toggleSelectAll(files: List<ShareFile>) {
        if (_selected.size == files.size) _selected.clear()
        else {
            _selected.clear()
            _selected.addAll(files)
        }
    }

    fun exitMultiSelect() {
        multiSelectMode = false
        _selected.clear()
    }

    /** 批量转存到当前平台能力声明的个人网盘根目录。 */
    fun batchSaveToCloud() {
        val files = _selected.toList()
        val s = session ?: return
        viewModelScope.launch {
            isBatchWorking = true
            batchProgress = ResolveBatchProgress(ResolveBatchStage.SAVING, total = files.size)
            batchCancelRequested = false
            try {
                val credential = currentTypedCredential()
                if (credential == null || !credential.isUsable()) {
                    downloadError = uiText(R.string.resolve_error_login_platform, platformName())
                    return@launch
                }
                val context = currentContext()
                val result = BatchTaskRunner.runSequentially(
                    items = files,
                    shouldCancel = { batchCancelRequested },
                    onProgress = { completed, total ->
                        batchProgress = ResolveBatchProgress(ResolveBatchStage.SAVING, completed, total)
                    }
                ) { file ->
                    val transferResult = context.repository.transferFile(
                        s, file, context.capabilities.rootDir, credential
                    )
                    transferResult.exceptionOrNull()?.let { error ->
                        if (error is CancellationException) throw error
                    }
                    transferResult.isSuccess
                }
                downloadError = when {
                    result.cancelled -> uiText(R.string.resolve_batch_save_cancelled, result.succeeded)
                    result.failed > 0 -> uiText(
                        R.string.resolve_batch_save_partial,
                        result.succeeded,
                        result.failed
                    )
                    result.succeeded > 0 -> uiText(
                        R.string.resolve_batch_save_success,
                        result.succeeded,
                        context.displayName
                    )
                    else -> uiText(R.string.resolve_error_save_failed)
                }
                exitMultiSelect()
            } finally {
                isBatchWorking = false
                batchProgress = null
                batchCancelRequested = false
            }
        }
    }

    /** 批量下载：逐个取直链入队（选中文件夹时递归下载整个文件夹并保持目录结构，全部获取完再统一切到下载页） */
    fun batchDownload() {
        val files = _selected.toList()
        val s = session ?: return
        viewModelScope.launch {
            isBatchWorking = true
            batchProgress = ResolveBatchProgress(ResolveBatchStage.COLLECTING)
            batchCancelRequested = false
            try {
                val credential = currentTypedCredential()
                if (credential == null || !credential.isUsable()) {
                    downloadError = uiText(R.string.resolve_error_login_generic)
                    return@launch
                }
                val context = currentContext()
                // 夸克/UC 共用 __puus：取链与下载必须用同一份已刷新 Cookie（直链签名绑定取链时刻的 __puus）
                val quarkCred = freshCredential(context, credential)
                // 展开选中项：文件直接加入，文件夹递归收集（相对路径 = 文件夹名/子/...）
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                for (file in files) {
                    if (file.isdir) {
                        collectShareFolder(context.repository, s, file.fid, file.fname, quarkCred, tasks, 0)
                    } else {
                        tasks.add(file to "")
                    }
                }
                if (tasks.isEmpty()) {
                    downloadError = uiText(R.string.resolve_error_selected_folder_empty)
                    exitMultiSelect()
                    return@launch
                }
                val result = BatchTaskRunner.runSequentially(
                    items = tasks,
                    shouldCancel = { batchCancelRequested },
                    onProgress = { completed, total ->
                        batchProgress = ResolveBatchProgress(
                            ResolveBatchStage.FETCHING_LINKS,
                            completed,
                            total
                        )
                    }
                ) { task ->
                    val (file, relPath) = task
                    val span = metricSpan(context.platform, ResolveMetricOperation.DIRECT_LINK)
                    val link = try {
                        val result = span.withRequestStage(RequestStage.DIRECT_LINK) {
                            context.repository.getShareDownloadLink(s, file, quarkCred)
                        }
                        val error = result.exceptionOrNull()
                        if (error != null) {
                            if (error is CancellationException) throw error
                            span.failure(error)
                            return@runSequentially false
                        }
                        result.getOrThrow().also { span.success() }
                    } catch (error: CancellationException) {
                        span.cancelled()
                        throw error
                    } catch (error: Exception) {
                        span.failure(error)
                        return@runSequentially false
                    }
                    // 文件夹内文件用相对路径（保持目录结构）；根目录文件用取链返回的文件名
                    enqueueDownload(link, quarkCred, if (relPath.isBlank()) link.filename else relPath)
                    true
                }
                downloadError = when {
                    result.cancelled -> uiText(R.string.resolve_batch_download_cancelled, result.succeeded)
                    result.failed > 0 -> uiText(
                        R.string.resolve_batch_download_partial,
                        result.succeeded,
                        result.failed
                    )
                    result.succeeded > 0 -> uiText(
                        R.string.resolve_batch_download_success,
                        result.succeeded
                    )
                    else -> uiText(R.string.resolve_error_download_link_failed)
                }
                // 所有已成功入队的任务均保留；批量结束或中断后统一切到下载页。
                if (result.succeeded > 0) downloadStarted = true
                exitMultiSelect()
            } finally {
                isBatchWorking = false
                batchProgress = null
                batchCancelRequested = false
            }
        }
    }

    /**
     * 递归收集分享文件夹内所有文件（保持目录结构）。
     * @param dirFid 分享内目录 fid
     * @param prefix 相对路径前缀（如 "文件夹A/子目录"）
     * @param result 输出：文件 + 相对路径（"文件夹A/子目录/文件.mp4"）
     */
    private suspend fun collectShareFolder(
        repository: ShareResolveRepository,
        s: ShareSession,
        dirFid: String,
        prefix: String,
        credential: CloudCredential,
        result: MutableList<Pair<ShareFile, String>>,
        depth: Int
    ) {
        if (depth > 12) return
        val listResult = repository.listFiles(s, dirFid, credential)
        listResult.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
        }
        val files = listResult.getOrNull().orEmpty()
        files.filter { !it.isdir }.forEach { result.add(it to "$prefix/${it.fname}") }
        files.filter { it.isdir }.forEach {
            collectShareFolder(
                repository, s, it.fid, "$prefix/${it.fname}", credential, result, depth + 1
            )
        }
    }

    fun consumeDownloadStarted() {
        downloadStarted = false
    }

    fun consumeDownloadError() {
        downloadError = null
    }

    private var session: ShareSession? = null
    private var currentDirFid = QuarkConstants.DEFAULT_PDIR_FID
    private class DirStack {
        private val values = ArrayDeque<String>()

        val size: Int get() = values.size
        val isEmpty: Boolean get() = values.isEmpty()

        fun push(fid: String) = values.addLast(fid)
        fun pop(): String? = if (values.isEmpty()) null else values.removeLast()
        fun clear() = values.clear()
        fun lastOrNull(): String? = values.lastOrNull()
        fun snapshot(): List<String> = values.toList()
        fun restore(snapshot: List<String>) {
            values.clear()
            values.addAll(snapshot)
        }
    }

    private val dirStack = DirStack()

    /** 当前解析的原始分享链接与提取码（收藏当前分享用） */
    private var currentLink: String? = null
    private var currentPwd: String? = null

    /** 当前目录路径名栈（用于面包屑显示），如 [辅助工具, 专用模组] */
    var pathNames by mutableStateOf<List<String>>(emptyList())
        private set

    /** 当前解析平台（QUARK / UC / XUNLEI），由链接自动检测 */
    private var currentPlatform: SharePlatform = SharePlatform.QUARK

    private fun currentContext(): ResolvePlatformContext = when (currentPlatform) {
        SharePlatform.QUARK -> ResolvePlatformContext(
            currentPlatform, resolveRepository,
            { accountRepository.getAccount()?.cookie?.let(CloudCredential::Cookie) },
            { fallback -> accountRepository.getFreshCookie()?.let(CloudCredential::Cookie) ?: fallback },
            ResolvePlatformDefaults.defaultDirFid(currentPlatform),
            ResolvePlatformDefaults.displayName(currentPlatform),
            ResolvePlatformDefaults.capabilities(currentPlatform),
            { ResolvePlatformDefaults.downloadHeaders(SharePlatform.QUARK, it) }
        )
        SharePlatform.UC -> ResolvePlatformContext(
            currentPlatform, ucResolveRepository,
            { ucAccountRepository.getAccount()?.cookie?.let(CloudCredential::Cookie) },
            { fallback -> ucAccountRepository.getFreshCookie()?.let(CloudCredential::Cookie) ?: fallback },
            ResolvePlatformDefaults.defaultDirFid(currentPlatform),
            ResolvePlatformDefaults.displayName(currentPlatform),
            ResolvePlatformDefaults.capabilities(currentPlatform),
            { ResolvePlatformDefaults.downloadHeaders(SharePlatform.UC, it) }
        )
        SharePlatform.XUNLEI -> ResolvePlatformContext(
            currentPlatform, xunleiResolveRepository,
            { xunleiAccountRepository.getAccount()?.accessToken?.let(CloudCredential::AccessToken) },
            { it }, ResolvePlatformDefaults.defaultDirFid(currentPlatform),
            ResolvePlatformDefaults.displayName(currentPlatform),
            ResolvePlatformDefaults.capabilities(currentPlatform),
            { ResolvePlatformDefaults.downloadHeaders(SharePlatform.XUNLEI, it) }
        )
        SharePlatform.BAIDU -> ResolvePlatformContext(
            currentPlatform, baiduResolveRepository,
            { baiduAccountRepository.getAccount()?.cookie?.let(CloudCredential::Cookie) },
            { it }, ResolvePlatformDefaults.defaultDirFid(currentPlatform),
            ResolvePlatformDefaults.displayName(currentPlatform),
            ResolvePlatformDefaults.capabilities(currentPlatform),
            { ResolvePlatformDefaults.downloadHeaders(SharePlatform.BAIDU, it) }
        )
        SharePlatform.C139 -> ResolvePlatformContext(
            currentPlatform, c139ResolveRepository,
            { c139AccountRepository.getAccount()?.cookie?.let(CloudCredential::Cookie) },
            { it }, ResolvePlatformDefaults.defaultDirFid(currentPlatform),
            ResolvePlatformDefaults.displayName(currentPlatform),
            ResolvePlatformDefaults.capabilities(currentPlatform),
            { ResolvePlatformDefaults.downloadHeaders(SharePlatform.C139, it) }
        )
        SharePlatform.PAN123 -> ResolvePlatformContext(
            currentPlatform, pan123ResolveRepository,
            { pan123AccountRepository.getAccount()?.accessToken?.let(CloudCredential::AccessToken) },
            { it }, ResolvePlatformDefaults.defaultDirFid(currentPlatform),
            ResolvePlatformDefaults.displayName(currentPlatform),
            ResolvePlatformDefaults.capabilities(currentPlatform),
            { ResolvePlatformDefaults.downloadHeaders(SharePlatform.PAN123, it) }
        )
    }

    /** 当前平台凭证（夸克/UC/百度/139 用 cookie，迅雷/123 用 access_token） */
    private suspend fun currentTypedCredential(): CloudCredential? = currentContext().credentialProvider()

    private suspend fun freshCredential(
        context: ResolvePlatformContext,
        fallback: CloudCredential
    ): CloudCredential = context.freshCredentialProvider(fallback)

    private fun currentRepo(): ShareResolveRepository = currentContext().repository

    private fun currentDefaultDirFid(): String = currentContext().defaultDirFid

    private fun platformName(): String = currentContext().displayName

    private fun uiText(resId: Int, vararg args: Any): UiText =
        UiText.Resource(resId, args.toList())

    private fun errorText(error: Throwable, fallbackResId: Int): UiText =
        YunxErrorClassifier.classify(error).toUiText(uiText(fallbackResId))

    private fun metricSpan(
        platform: SharePlatform?,
        operation: ResolveMetricOperation,
        startedAtNanos: Long = System.nanoTime()
    ): ResolveMetricSpan = ResolveMetricSpan(
        platform = platform,
        operation = operation,
        startedAtNanos = startedAtNanos,
        sink = { Log.i(TAG, it) }
    )

    /** 开始解析：链接 → token →（密码）→ 根目录列表 */
    fun startResolve(link: String, pwd: String?) {
        currentLink = link
        currentPwd = pwd
        viewModelScope.launch {
            val startedAtNanos = System.nanoTime()
            uiState = ResolveUiState.Loading
            val parsed = ShareLinkParser.parse(link)
            if (parsed == null) {
                metricSpan(null, ResolveMetricOperation.INITIAL_RESOLVE, startedAtNanos)
                    .failure(ResolveMetricErrorKind.INVALID_INPUT)
                uiState = ResolveUiState.Error(uiText(R.string.resolve_error_invalid_link))
                return@launch
            }
            currentPlatform = parsed.platform
            val span = metricSpan(parsed.platform, ResolveMetricOperation.INITIAL_RESOLVE, startedAtNanos)
            try {
                val credential = currentTypedCredential()
                if (credential == null || !credential.isUsable()) {
                    span.failure(ResolveMetricErrorKind.AUTH_EXPIRED)
                    uiState = ResolveUiState.Error(
                        uiText(R.string.resolve_error_login_platform_in_drive, platformName())
                    )
                    return@launch
                }
                val repo = currentRepo()
                val sessionResult = span.withRequestStage(RequestStage.RESOLVE_SESSION) {
                    repo.createSession(link, pwd, credential)
                }
                val error = sessionResult.exceptionOrNull()
                if (error != null) {
                    if (error is CancellationException) throw error
                    span.failure(error)
                    uiState = ResolveUiState.Error(
                        errorText(error, R.string.resolve_error_resolve_failed)
                    )
                    return@launch
                }
                val resolvedSession = sessionResult.getOrThrow()
                session = resolvedSession
                currentDirFid = currentDefaultDirFid()
                dirStack.clear()
                pathNames = emptyList()
                exitMultiSelect()
                loadFiles(
                    resolvedSession,
                    currentDirFid,
                    credential,
                    repo,
                    previousDetail = null,
                    span = span,
                    requestStage = RequestStage.RESOLVE_ROOT_LIST
                )
            } catch (error: CancellationException) {
                span.cancelled()
                throw error
            } catch (error: Exception) {
                span.failure(error)
                uiState = ResolveUiState.Error(errorText(error, R.string.resolve_error_resolve_failed))
            }
        }
    }

    /** 进入文件夹 */
    fun openFolder(file: ShareFile) {
        val s = session ?: return
        val previous = uiState as? ResolveUiState.Detail ?: return
        dirStack.push(file.fid)
        pathNames = pathNames + file.fname
        currentDirFid = file.fid
        viewModelScope.launch {
            val span = metricSpan(currentPlatform, ResolveMetricOperation.DIRECTORY_LIST)
            uiState = ResolveUiState.Loading
            try {
                val credential = currentTypedCredential()
                if (credential == null || !credential.isUsable()) {
                    span.failure(ResolveMetricErrorKind.AUTH_EXPIRED)
                    rollbackTo(previous, uiText(R.string.resolve_error_login_invalid))
                    return@launch
                }
                if (!loadFiles(s, file.fid, credential, currentRepo(), previous, span)) {
                    rollbackTo(
                        previous,
                        (uiState as? ResolveUiState.Detail)?.errorBanner
                            ?: uiText(R.string.resolve_error_file_list_failed)
                    )
                }
            } catch (error: CancellationException) {
                span.cancelled()
                throw error
            } catch (error: Exception) {
                span.failure(error)
                rollbackTo(previous, errorText(error, R.string.resolve_error_file_list_failed))
            }
        }
    }

    /** 返回上级目录 */
    fun goBack() {
        val s = session ?: return
        if (dirStack.isEmpty) return
        val previous = uiState as? ResolveUiState.Detail ?: return
        val childFid = currentDirFid
        val childName = pathNames.lastOrNull()
        dirStack.pop()
        currentDirFid = dirStack.lastOrNull() ?: currentDefaultDirFid()
        pathNames = pathNames.dropLast(1)
        viewModelScope.launch {
            val span = metricSpan(currentPlatform, ResolveMetricOperation.DIRECTORY_LIST)
            uiState = ResolveUiState.Loading
            var failureMessage: UiText = uiText(R.string.resolve_error_file_list_failed)
            try {
                val credential = currentTypedCredential()
                if (credential == null || !credential.isUsable()) {
                    span.failure(ResolveMetricErrorKind.AUTH_EXPIRED)
                    failureMessage = uiText(R.string.resolve_error_login_invalid)
                } else if (loadFiles(s, currentDirFid, credential, currentRepo(), previous, span)) {
                    return@launch
                } else {
                    failureMessage = (uiState as? ResolveUiState.Detail)?.errorBanner ?: failureMessage
                }
            } catch (error: CancellationException) {
                span.cancelled()
                throw error
            } catch (error: Exception) {
                span.failure(error)
                failureMessage = errorText(error, R.string.resolve_error_file_list_failed)
            }
            dirStack.push(childFid)
            currentDirFid = childFid
            if (childName != null) pathNames = pathNames + childName
            uiState = previous.copy(errorBanner = failureMessage)
        }
    }

    /** 返回：在子目录则返回上一级，在根目录则返回输入页 */
    fun navigateBack() {
        if (dirStack.isEmpty) {
            backToInput()
        } else {
            goBack()
        }
    }

    /** 返回输入页 */
    fun backToInput() {
        session = null
        downloadLink = null
        currentLink = null
        currentPwd = null
        pathNames = emptyList()
        dirStack.clear()
        currentDirFid = currentDefaultDirFid()
        multiSelectMode = false
        _selected.clear()
        uiState = ResolveUiState.Idle
    }

    /** 将当前分享链接收藏到指定分类（标题可自定义，为空时回退分享标题） */
    fun addCurrentToBookmark(title: String, category: String) {
        val link = currentLink?.takeIf { it.isNotBlank() }
        if (link == null) {
            SnackbarController.show(uiText(R.string.resolve_bookmark_missing_link))
            return
        }
        val cat = category.ifBlank { BookmarkEntity.DEFAULT_CATEGORY }
        val resolvedTitle = title.ifBlank { session?.title.orEmpty() }
        viewModelScope.launch {
            bookmarkRepository.insert(
                BookmarkEntity(
                    link = link,
                    title = resolvedTitle,
                    platform = currentPlatform.name,
                    pwd = currentPwd.orEmpty(),
                    category = cat
                )
            )
            SnackbarController.show(uiText(R.string.resolve_bookmark_success, cat))
        }
    }

    /**
     * 面包屑导航：点击第 level 级（0=分享根目录）回退到该目录并刷新列表。
     * 当前所在层（level == pathNames.size）无需操作。
     */
    fun navigateToLevel(level: Int) {
        val s = session ?: return
        if (level < 0 || level > pathNames.size) return
        if (level == pathNames.size) return
        val previous = uiState as? ResolveUiState.Detail ?: return
        val stackSnapshot = dirStack.snapshot()
        val previousDirFid = currentDirFid
        val previousPathNames = pathNames
        // 弹出目录栈直到对应层级；level=0 时回到分享根目录
        while (dirStack.size > level) dirStack.pop()
        currentDirFid = dirStack.lastOrNull() ?: currentDefaultDirFid()
        pathNames = pathNames.take(level)
        viewModelScope.launch {
            val span = metricSpan(currentPlatform, ResolveMetricOperation.DIRECTORY_LIST)
            var failureMessage: UiText = uiText(R.string.resolve_error_file_list_failed)
            try {
                val credential = currentTypedCredential()
                if (credential == null || !credential.isUsable()) {
                    span.failure(ResolveMetricErrorKind.AUTH_EXPIRED)
                    failureMessage = uiText(R.string.resolve_error_login_invalid)
                } else if (loadFiles(s, currentDirFid, credential, currentRepo(), previous, span)) {
                    return@launch
                } else {
                    failureMessage = (uiState as? ResolveUiState.Detail)?.errorBanner ?: failureMessage
                }
            } catch (error: CancellationException) {
                span.cancelled()
                throw error
            } catch (error: Exception) {
                span.failure(error)
                failureMessage = errorText(error, R.string.resolve_error_file_list_failed)
            }
            dirStack.restore(stackSnapshot)
            currentDirFid = previousDirFid
            pathNames = previousPathNames
            uiState = previous.copy(errorBanner = failureMessage)
        }
    }

    private fun rollbackTo(previous: ResolveUiState.Detail, message: UiText) {
        dirStack.pop()
        currentDirFid = dirStack.lastOrNull() ?: currentDefaultDirFid()
        pathNames = pathNames.dropLast(1)
        uiState = previous.copy(errorBanner = message)
    }

    /** 获取文件下载直链（各平台实现不同：夸克转存后取 / UC 直接取 / 迅雷转存后取详情直链） */
    fun fetchDownloadLink(file: ShareFile) {
        viewModelScope.launch {
            val span = metricSpan(session?.let { currentPlatform }, ResolveMetricOperation.DIRECT_LINK)
            downloadLink = null
            downloadError = null
            isFetchingDownloadLink = true
            try {
                val s = session
                if (s == null) {
                    span.failure(ResolveMetricErrorKind.INVALID_INPUT)
                    downloadError = uiText(R.string.resolve_error_resolve_first)
                    return@launch
                }
                val credential = currentTypedCredential()
                if (credential == null || !credential.isUsable()) {
                    span.failure(ResolveMetricErrorKind.AUTH_EXPIRED)
                    downloadError = uiText(R.string.resolve_error_login_invalid)
                    return@launch
                }
                val context = currentContext()
                // 夸克/UC 共用 __puus：取链前确保新鲜（直链签名绑定取链时刻的 Cookie）
                val quarkCred = freshCredential(context, credential)
                val result = span.withRequestStage(RequestStage.DIRECT_LINK) {
                    context.repository.getShareDownloadLink(s, file, quarkCred)
                }
                val error = result.exceptionOrNull()
                if (error != null) {
                    if (error is CancellationException) throw error
                    span.failure(error)
                    downloadError = errorText(error, R.string.resolve_error_download_link_failed)
                } else {
                    downloadLink = result.getOrThrow()
                    span.success()
                }
            } catch (error: CancellationException) {
                span.cancelled()
                throw error
            } catch (error: Exception) {
                span.failure(error)
                downloadError = errorText(error, R.string.resolve_error_download_link_failed)
            } finally {
                isFetchingDownloadLink = false
            }
        }
    }

    fun dismissDownloadDialog() {
        val link = downloadLink
        downloadLink = null
        // 弹窗关闭且未开始下载时，清理当前平台取链产生的临时转存。
        if (link?.cleanupDirFid != null) {
            val context = currentContext()
            viewModelScope.launch {
                val credential = context.credentialProvider() ?: return@launch
                link.cleanupDirFid?.let { dirFid ->
                    context.repository.cleanupTempDir(dirFid, credential)
                }
            }
        }
    }

    /**
     * 将直链原样加入下载队列，并携带当前平台要求的凭证和请求头。
     * 不触发切页 —— 与 startDownload 的区别：批量下载全部入队后才统一切到下载页。
     */
    private suspend fun enqueueDownload(
        link: DownloadLink,
        credential: CloudCredential,
        fileName: String = link.filename
    ) {
        val context = currentContext()
        // 【关键修复】夸克/UC 共用 __puus：取链与下载必须用同一份已刷新 Cookie（AlistGo/alist#830 类缺陷）
        // getFreshCookie 有 90 分钟间隔保护，与取链处调用幂等，得到的是同一份。
        val effectiveCredential = freshCredential(context, credential)
        val headers = context.downloadHeadersProvider(effectiveCredential.value)
        downloadManager.enqueue(
            url = link.downloadUrl,
            fileName = fileName,
            headers = headers,
            size = link.size,
            platform = context.platform.name.lowercase(),
            cleanup = link.cleanupDirFid?.let { dirFid ->
                DownloadCleanup(
                    platform = context.platform.name,
                    resourceId = dirFid,
                    credential = effectiveCredential.value
                )
            }
        ) {
            // 下载完成：兼容无持久化清理信息的旧调用；当前夸克清理信息已随任务持久化
            val dirFid = link.cleanupDirFid
            if (dirFid != null) {
                val cleanupCredential = context.credentialProvider()
                if (cleanupCredential != null && cleanupCredential.isUsable()) {
                    context.repository.cleanupTempDir(dirFid, cleanupCredential)
                }
            }
        }
    }

    /** 将直链加入下载队列（单文件下载：入队后立即切换到下载页） */
    fun startDownload(link: DownloadLink) {
        viewModelScope.launch {
            // 开始下载：先关闭弹窗（临时转存由下载完成 onComplete 清理，不在此时删）
            downloadLink = null
            val credential = currentTypedCredential()
            if (credential == null || !credential.isUsable()) {
                downloadError = uiText(R.string.resolve_error_login_generic)
                return@launch
            }
            enqueueDownload(link, credential)
            downloadStarted = true
        }
    }

    private suspend fun loadFiles(
        s: ShareSession,
        dirFid: String,
        credential: CloudCredential,
        repo: ShareResolveRepository,
        previousDetail: ResolveUiState.Detail?,
        span: ResolveMetricSpan,
        requestStage: RequestStage = RequestStage.DIRECTORY_LIST
    ): Boolean {
        return try {
            val result = span.withRequestStage(requestStage) {
                repo.listFilesPage(s, dirFid, credential, cursor = null)
            }
            val error = result.exceptionOrNull()
            if (error != null) {
                if (error is CancellationException) throw error
                span.failure(error)
                val message = errorText(error, R.string.resolve_error_file_list_failed)
                uiState = if (previousDetail != null) {
                    previousDetail.copy(errorBanner = message)
                } else {
                    ResolveUiState.Error(message)
                }
                false
            } else {
                val page = result.getOrThrow()
                uiState = ResolveUiState.Detail(s, page.files, nextCursor = page.nextCursor)
                span.success()
                true
            }
        } catch (error: CancellationException) {
            span.cancelled()
            throw error
        } catch (error: Exception) {
            span.failure(error)
            val message = errorText(error, R.string.resolve_error_file_list_failed)
            uiState = previousDetail?.copy(errorBanner = message) ?: ResolveUiState.Error(message)
            false
        }
    }

    fun loadMoreFiles() {
        val current = uiState as? ResolveUiState.Detail ?: return
        val cursor = current.nextCursor ?: return
        if (current.isLoadingMore) return
        val s = session ?: return
        val requestedDir = currentDirFid
        val context = currentContext()
        val loadingState = current.copy(isLoadingMore = true, loadMoreFailed = false, errorBanner = null)
        uiState = loadingState
        viewModelScope.launch {
            val span = metricSpan(context.platform, ResolveMetricOperation.DIRECTORY_LOAD_MORE)
            try {
                val credential = context.credentialProvider()
                if (credential == null || !credential.isUsable()) {
                    span.failure(ResolveMetricErrorKind.AUTH_EXPIRED)
                    if (uiState == loadingState) {
                        uiState = current.copy(
                            errorBanner = uiText(R.string.resolve_error_login_invalid),
                            loadMoreFailed = true
                        )
                    }
                    return@launch
                }
                val result = span.withRequestStage(RequestStage.DIRECTORY_LOAD_MORE) {
                    context.repository.listFilesPage(s, requestedDir, credential, cursor)
                }
                val error = result.exceptionOrNull()
                if (error != null) {
                    if (error is CancellationException) throw error
                    span.failure(error)
                    if (uiState == loadingState && currentDirFid == requestedDir) {
                        uiState = current.copy(
                            errorBanner = errorText(error, R.string.resolve_error_load_more_failed),
                            loadMoreFailed = true
                        )
                    }
                } else {
                    val page = result.getOrThrow()
                    span.success()
                    if (uiState == loadingState && currentDirFid == requestedDir) {
                        val seen = current.files.asSequence().map { it.fid }.toMutableSet()
                        val fresh = page.files.filter { seen.add(it.fid) }
                        uiState = current.copy(
                            files = current.files + fresh,
                            nextCursor = page.nextCursor?.takeUnless { it == cursor || fresh.isEmpty() },
                            errorBanner = null,
                            loadMoreFailed = false
                        )
                    }
                }
            } catch (error: CancellationException) {
                span.cancelled()
                throw error
            } catch (error: Exception) {
                span.failure(error)
                if (uiState == loadingState && currentDirFid == requestedDir) {
                    uiState = current.copy(
                        errorBanner = errorText(error, R.string.resolve_error_load_more_failed),
                        loadMoreFailed = true
                    )
                }
            }
        }
    }

    class Factory(
        private val accountRepository: QuarkAccountRepository,
        private val resolveRepository: QuarkResolveRepository,
        private val ucAccountRepository: UCAccountRepository,
        private val ucResolveRepository: UCResolveRepository,
        private val xunleiAccountRepository: XunleiAccountRepository,
        private val xunleiResolveRepository: XunleiResolveRepository,
        private val baiduAccountRepository: BaiduAccountRepository,
        private val baiduResolveRepository: BaiduResolveRepository,
        private val c139AccountRepository: C139AccountRepository,
        private val c139ResolveRepository: C139ResolveRepository,
        private val pan123AccountRepository: Pan123AccountRepository,
        private val pan123ResolveRepository: Pan123ResolveRepository,
        private val downloadManager: DownloadManager,
        private val bookmarkRepository: BookmarkRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ResolveViewModel::class.java))
            return ResolveViewModel(
                accountRepository, resolveRepository,
                ucAccountRepository, ucResolveRepository,
                xunleiAccountRepository, xunleiResolveRepository,
                baiduAccountRepository, baiduResolveRepository,
                c139AccountRepository, c139ResolveRepository,
                pan123AccountRepository, pan123ResolveRepository,
                downloadManager,
                bookmarkRepository
            ) as T
        }
    }

    private companion object {
        const val TAG = "ResolveMetrics"
    }
}
