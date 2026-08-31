package com.yunx.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.error.YunxErrorClassifier
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.task.BatchTaskResult
import com.yunx.app.data.task.BatchTaskRunner
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 云盘浏览 ViewModel 基类（P2-4 第一刀）：6 个平台 VM 的共性骨架。
 *
 * 覆盖：状态流（uiState/moveUiState）、目录栈（主/移动目标两套，存自身 fid 语义）、
 * 多选、操作消息、刷新/加载更多框架、批量操作外壳、下载中断标志。
 *
 * 平台差异以四个抽象/开闭点注入（子类实现保持与原 6 份 VM 逐行为一致）：
 * - [listFiles] 首页列表（含凭证检查语义：null 返回 Error）
 * - [moveListFiles] 移动目标列表
 * - [platformLoginHint] 未登录提示文案（「请先登录XX网盘」）
 * - [delayAfterMoveMillis]/[delayAfterDeleteMillis] 操作后延迟刷新
 *
 * 公有 API 与原 6 个 VM 完全一致（Screen 层零改动），文件操作（rename/move/delete/share/download）
 * 因各平台签名与文案差异大，仍留子类——待 P2-4 第二刀经 CloudFileSource 收敛。
 */
abstract class BaseCloudViewModel : ViewModel(), CloudDirBrowser {

    /* ---------- 状态流 ---------- */

    protected val _uiState = MutableStateFlow<CloudUiState>(CloudUiState.Loading)
    override val uiState: StateFlow<CloudUiState> = _uiState.asStateFlow()

    var isLoadingMore by mutableStateOf(false)
        protected set

    /** 当前操作的文件（更多按钮弹出操作菜单） */
    var actionFile by mutableStateOf<ShareFile?>(null)
        protected set

    /** 操作结果消息（Toast） */
    var cloudMessage by mutableStateOf<String?>(null)
        protected set

    /** 操作执行中（防止重复点击） */
    var isOperating by mutableStateOf(false)
        protected set

    /** 文件夹下载/批量下载进度提示（如 "正在加入下载 3/10"）；null 不显示 */
    var folderProgress by mutableStateOf<String?>(null)
        protected set

    /** 下载中断请求（UI 点「中断」置 true，下载循环检查后停止剩余项） */
    protected var downloadCancelRequested = false

    /** 中断当前下载（批量下载/文件夹下载） */
    fun cancelDownload() {
        downloadCancelRequested = true
    }

    protected suspend fun <T> runDownloadBatch(
        tasks: List<T>,
        operation: suspend (T) -> Boolean
    ): BatchTaskResult = BatchTaskRunner.runSequentially(
        items = tasks,
        shouldCancel = { downloadCancelRequested },
        onProgress = { completed, total -> folderProgress = "正在加入下载 $completed/$total" },
        operation = operation
    )

    protected fun downloadBatchMessage(result: BatchTaskResult, cancelledMessage: String): String = when {
        result.cancelled -> "$cancelledMessage（已加入 ${result.succeeded} 项）"
        result.failed > 0 -> "已加入 ${result.succeeded} 个下载任务（${result.failed} 个失败）"
        else -> "已加入 ${result.succeeded} 个下载任务"
    }

    /** 下拉刷新中（不切换 Loading 遮罩，保持列表显示） */
    var refreshing by mutableStateOf(false)
        protected set

    /** 下载入队事件计数（UI 监听后切换到下载页；对齐解析页行为） */
    var downloadTriggered by mutableStateOf(0)
        protected set

    /** 消费下载事件（防止再次进入网盘页重复触发切页） */
    fun consumeDownloadTriggered() {
        downloadTriggered = 0
    }

    /** 分享创建成功后的信息（弹窗展示链接+提取码） */
    var shareResult by mutableStateOf<ShareInfo?>(null)
        protected set

    fun consumeMessage() {
        cloudMessage = null
    }

    fun dismissShareResult() {
        shareResult = null
    }

    fun openActions(file: ShareFile) {
        actionFile = file
    }

    fun dismissActions() {
        actionFile = null
    }

    abstract val downloadLink: DownloadLink?

    abstract fun startDownload()

    abstract fun dismissDownloadDialog()

    /* ---------- 平台注入点 ---------- */

    /** 未登录提示文案 */
    protected abstract val platformLoginHint: String

    /**
     * 列目录（首页与加载更多统一入口）：返回 (本页文件, 下页游标)；游标 null = 无更多。
     * cursor=null 为首页。凭证检查失败返回 null → Error 态（load）/静默（refresh/loadMore）。
     */
    protected abstract suspend fun listFiles(dir: String, cursor: String?): Pair<List<ShareFile>, String?>?

    /** 移动/删除后延迟刷新毫秒数（夸克/迅雷/UC/百度/139：1500/1200；123：立即） */
    protected open val delayAfterMoveMillis: Long = 1500L
    protected open val delayAfterDeleteMillis: Long = 1200L

    /* ---------- 目录栈（主列表，存自身 fid 语义） ---------- */

    /** 目录 fid 栈（不含根目录） */
    protected val dirStack = ArrayDeque<String>()
    /** 目录名栈（与 dirStack 一一对应） */
    protected val nameStack = ArrayDeque<String>()

    // ⚠️ 注意：初始加载（loadRoot）**不在基类 init 中调用**——
    // 父类构造期间子类字段（cookieProvider 等）尚未赋值，而 loadRoot → listFiles 是子类覆写的
    // 开放成员，此时执行会读到未初始化字段直接 NPE（构造期开放成员调用陷阱）。
    // 各子类在字段声明之后自行 `init { loadRoot() }`。

    override fun loadRoot() {
        dirStack.clear()
        nameStack.clear()
        load(rootDir, emptyList())
    }

    /** 根目录标识：夸克/UC/123 "0"、迅雷 ""、百度/139 "/"（子类按平台覆写） */
    protected open val rootDir: String = "0"

    /** 进入文件夹 */
    override fun openFolder(file: ShareFile) {
        dirStack.addLast(file.fid)
        nameStack.addLast(file.fname)
        load(file.fid, nameStack.toList())
    }

    /** 返回上一级（根目录时重新加载根） */
    override fun back() {
        if (nameStack.isEmpty()) {
            loadRoot()
            return
        }
        dirStack.removeLast()
        nameStack.removeLast()
        load(dirStack.lastOrNull() ?: rootDir, nameStack.toList())
    }

    /** 面包屑回退到第 level 层（0=根目录） */
    override fun navigateToLevel(level: Int) {
        while (nameStack.size > level) {
            dirStack.removeLast()
            nameStack.removeLast()
        }
        load(dirStack.lastOrNull() ?: rootDir, nameStack.toList())
    }

    /* ---------- 移动目标目录浏览（独立状态，避免影响主列表） ---------- */

    private val _moveUiState = MutableStateFlow<CloudUiState>(CloudUiState.Loading)
    val moveUiState: StateFlow<CloudUiState> = _moveUiState.asStateFlow()
    private val moveDirStack = ArrayDeque<String>()
    private val moveNameStack = ArrayDeque<String>()

    /** 打开移动目标浏览（回到根目录） */
    fun openMoveRoot() {
        moveDirStack.clear()
        moveNameStack.clear()
        moveLoad(rootDir, emptyList())
    }

    /** 移动目标：进入文件夹 */
    fun openMoveFolder(file: ShareFile) {
        moveDirStack.addLast(moveDirKeyOf(file))
        moveNameStack.addLast(file.fname)
        moveLoad(moveDirKeyOf(file), moveNameStack.toList())
    }

    /** 移动目标：返回上一级 */
    fun moveBack() {
        if (moveNameStack.isEmpty()) return
        moveDirStack.removeLast()
        moveNameStack.removeLast()
        moveLoad(moveDirStack.lastOrNull() ?: rootDir, moveNameStack.toList())
    }

    /** 移动目标：面包屑回退 */
    fun moveNavigateToLevel(level: Int) {
        while (moveNameStack.size > level) {
            moveDirStack.removeLast()
            moveNameStack.removeLast()
        }
        moveLoad(moveDirStack.lastOrNull() ?: rootDir, moveNameStack.toList())
    }

    /** 移动目标目录的入栈标识（夸克/UC/迅雷/139/123 为 fid；百度为 fidToken 绝对路径） */
    protected open fun moveDirKeyOf(file: ShareFile): String = file.fid

    private fun moveLoad(dir: String, pathNames: List<String>) {
        _moveUiState.value = CloudUiState.Loading
        viewModelScope.launch {
            try {
                val files = moveListFiles(dir)
                if (files == null) {
                    _moveUiState.value = CloudUiState.Error(platformLoginHint)
                    return@launch
                }
                _moveUiState.value = CloudUiState.Loaded(files.first, pathNames, dir)
            } catch (e: Exception) {
                _moveUiState.value = CloudUiState.Error(userMessage(e, "加载失败"))
            }
        }
    }

    /** 移动目标目录列表：只取首页（移动弹窗无分页）；默认与主列表共用 [listFiles] */
    protected open suspend fun moveListFiles(dir: String): Pair<List<ShareFile>, String?>? =
        listFiles(dir, null)?.let { it.first to null }

    /* ---------- 多选 ---------- */

    /** 多选模式（长按进入） */
    var multiSelectMode by mutableStateOf(false)
        private set

    protected val _selected = mutableStateListOf<ShareFile>()
    val selected: List<ShareFile> get() = _selected

    /** 长按进入多选并选中该文件 */
    fun enterMultiSelect(file: ShareFile) {
        multiSelectMode = true
        _selected.clear()
        _selected.add(file)
    }

    /** 切换选中状态 */
    fun toggleSelect(file: ShareFile) {
        if (_selected.contains(file)) _selected.remove(file) else _selected.add(file)
    }

    /** 全选/取消全选当前目录 */
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

    /* ---------- 刷新 / 加载更多 ---------- */

    /** 下拉刷新当前目录（不切 Loading 遮罩，完成后更新列表） */
    fun refresh() {
        val current = uiState.value
        if (current !is CloudUiState.Loaded) {
            loadRoot()
            return
        }
        refreshing = true
        viewModelScope.launch {
            try {
                val files = listFiles(current.dir, null)
                if (files != null) {
                    _uiState.value = CloudUiState.Loaded(
                        files.first, current.pathNames, current.dir,
                        files.second != null, files.second
                    )
                }
            } catch (e: Exception) {
                cloudMessage = userMessage(e, "刷新失败")
            } finally {
                refreshing = false
            }
        }
    }

    /** 加载更多（页码/游标经 listFiles 的 cursor 透传；123 页码特殊故 open 供覆写） */
    open fun loadMore() {
        val current = uiState.value as? CloudUiState.Loaded ?: return
        if (!current.hasMore || isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val files = listFiles(current.dir, current.cursor)
                if (uiState.value != current) return@launch
                if (files != null) {
                    // 防御性去重：平台分页边界异常（如最后一页恰好满页/游标回绕）可能返回重复项，
                    // LazyColumn 以 fid 为 key，重复项会直接崩溃
                    val seen = current.files.asSequence().map { it.fid }.toMutableSet()
                    val fresh = files.first.filter { seen.add(it.fid) }
                    _uiState.value = current.copy(
                        files = current.files + fresh,
                        hasMore = files.second != null,
                        cursor = files.second
                    )
                }
            } catch (e: Exception) {
                cloudMessage = userMessage(e, "加载更多失败")
            } finally {
                isLoadingMore = false
            }
        }
    }

    /* ---------- 内部 ---------- */

    protected fun reloadCurrent() {
        val current = uiState.value
        if (current is CloudUiState.Loaded) {
            load(current.dir, current.pathNames)
        } else {
            loadRoot()
        }
    }

    private fun load(dir: String, pathNames: List<String>) {
        _uiState.value = CloudUiState.Loading
        viewModelScope.launch {
            try {
                val files = listFiles(dir, null)
                if (files == null) {
                    _uiState.value = CloudUiState.Error(platformLoginHint)
                    return@launch
                }
                _uiState.value = CloudUiState.Loaded(
                    files.first, pathNames, dir,
                    files.second != null, files.second
                )
            } catch (e: Exception) {
                // 对齐原版各 VM 的 load：异常（含未登录时 cookie()/token() 抛出的提示）转 Error 态，
                // 未登录/网络失败展示提示而非崩溃
                _uiState.value = CloudUiState.Error(userMessage(e, "加载失败"))
            }
        }
    }

    /** 供子类文件操作统一「延迟后刷新」语义 */
    protected suspend fun delayThenReload(millis: Long) {
        if (millis > 0) delay(millis)
        reloadCurrent()
    }

    protected fun userMessage(error: Throwable, fallback: String): String =
        YunxErrorClassifier.userMessage(error, fallback)
}
