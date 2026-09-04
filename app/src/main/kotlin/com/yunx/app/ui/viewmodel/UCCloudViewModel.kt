package com.yunx.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.CloudFileSource
import com.yunx.app.data.network.ShareRequest
import com.yunx.app.data.network.adapters.UCSharePolicy
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** UC 云盘浏览 UI 状态（P2-1：统一为 CloudUiState） */

/**
 * UC 网盘云盘浏览 ViewModel（参考夸克 QuarkCloudViewModel；P2-4：共性骨架见 BaseCloudViewModel）：
 * - 目录浏览（根/子目录/面包屑回退）
 * - 文件操作：下载 / 重命名 / 移动 / 创建分享 + 长按多选批量操作
 * 操作成功后自动刷新当前目录，结果通过 cloudMessage（Toast）反馈。
 */
class UCCloudViewModel(
    private val source: CloudFileSource,
    private val cookieProvider: suspend () -> String?,
    private val downloadManager: DownloadManager,
    private val loginState: Flow<Boolean>
) : BaseCloudViewModel() {

    override val platformLoginHint = "请先登录${source.capabilities.name}"
    override val rootDir = source.capabilities.rootDir

    // 初始加载放在子类 init（构造参数字段已赋值；基类 init 期间调用开放成员会 NPE）
    init {
        loadRoot()
        // 启动期未登录时上面的 loadRoot 会残留「请先登录…」错误态；登录态从无到有后自动重载根目录，
        // 进网盘列表无需再手动点「重试」。drop(1) 跳过 VM 创建时的登录态快照（init 已加载，避免冷启动重复）；
        // distinctUntilChanged 过滤登录页 Cookie/Token 刷新等重复 upsert。
        viewModelScope.launch {
            loginState
                .drop(1)
                .distinctUntilChanged()
                .collect { loggedIn -> if (loggedIn) loadRoot() }
        }
    }

    override suspend fun listFiles(dir: String, cursor: String?): Pair<List<ShareFile>, String?>? {
        return source.list(dir, cursor)
    }

    private suspend fun cookie(): String =
        cookieProvider() ?: throw IllegalStateException(platformLoginHint)

    // ---------- 文件操作 ----------

    /**
     * 递归收集文件夹内所有文件（保持目录结构）。
     */
    private suspend fun collectFolderFiles(
        dirFid: String,
        prefix: String,
        result: MutableList<Pair<ShareFile, String>>,
        depth: Int
    ) {
        if (depth > 12) return
        val list = mutableListOf<ShareFile>()
        var cursor: String? = null
        val seenCursors = mutableSetOf<String>()
        var pageCount = 0
        do {
            val page = runCatching { source.list(dirFid, cursor) }.getOrNull() ?: break
            list += page.first
            cursor = page.second
            pageCount++
        } while (cursor != null && seenCursors.add(cursor) && pageCount < 100)
        // 先文件后文件夹（与目录列表展示顺序一致）
        list.filter { !it.isdir }.forEach { result.add(it to "$prefix/${it.fname}") }
        list.filter { it.isdir }.forEach {
            collectFolderFiles(it.fid, "$prefix/${it.fname}", result, depth + 1)
        }
    }

    /** 下载整个文件夹（操作菜单）：递归收集所有文件，保持目录结构保存到 Download */
    fun downloadFolder() {
        val folder = actionFile ?: return
        if (!folder.isdir) return
        viewModelScope.launch {
            isOperating = true
            folderProgress = "正在收集文件…"
            downloadCancelRequested = false
            try {
                val cookie = cookie()
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                collectFolderFiles(folder.fid, folder.fname, tasks, 0)
                if (tasks.isEmpty()) {
                    cloudMessage = "文件夹为空"
                    actionFile = null
                    return@launch
                }
                val result = runDownloadBatch(tasks) { (file, relPath) ->
                    val link = source.downloadLink(file)
                        ?: throw IllegalStateException("获取下载链接失败")
                    downloadManager.enqueue(
                        url = link.downloadUrl,
                        fileName = relPath, // 相对路径：Download/文件夹A/子目录/文件.mp4
                        size = link.size,
                        platform = DownloadPlatform.UC,
                        headers = source.downloadHeaders(cookie)
                    )
                    true
                }
                cloudMessage = downloadBatchMessage(result, "已中断下载")
                actionFile = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                cloudMessage = e.message ?: "下载文件夹失败"
            } finally {
                isOperating = false
                folderProgress = null
                downloadCancelRequested = false
            }
        }
    }

    /** 下载文件：取直链（带 Cookie+UA）→ 加入内置下载队列 */
    /** 待确认的下载直链（单文件下载弹窗展示用，长按链接可复制） */
    override var downloadLink by mutableStateOf<DownloadLink?>(null)
        private set

    /** 与 downloadLink 配套的入队参数（弹窗确认后直接入队） */
    private var pendingDownload: PendingDownload? = null

    /** 下载文件：取直链 → 弹出下载确认弹窗（对齐解析页行为，确认后入队） */
    fun downloadFile() {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                val cookie = cookie()
                val link = source.downloadLink(file)
                    ?: throw IllegalStateException("获取下载链接失败")
                pendingDownload = PendingDownload(
                    url = link.downloadUrl,
                    fileName = link.filename.ifBlank { file.fname },
                    size = link.size,
                    headers = source.downloadHeaders(cookie)
                )
                downloadLink = link // 弹下载确认弹窗（长按直链可复制）
            } catch (e: Exception) {
                cloudMessage = userMessage(e, "下载失败")
            } finally {
                isOperating = false
            }
        }
    }

    /** 下载弹窗确认：用已生成的直链入队 */
    override fun startDownload() {
        val pd = pendingDownload ?: return
        downloadLink = null
        pendingDownload = null
        viewModelScope.launch {
            isOperating = true
            try {
                downloadManager.enqueue(
                    url = pd.url,
                    fileName = pd.fileName,
                    size = pd.size,
                    platform = DownloadPlatform.UC,
                    headers = pd.headers
                )
                cloudMessage = "已加入下载：${pd.fileName}"
                actionFile = null
                downloadTriggered++
            } catch (e: Exception) {
                cloudMessage = userMessage(e, "下载失败")
            } finally {
                isOperating = false
            }
        }
    }

    /** 关闭下载弹窗（放弃下载） */
    override fun dismissDownloadDialog() {
        downloadLink = null
        pendingDownload = null
    }

    /** 重命名 */
    fun renameFile(newName: String) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                if (source.rename(file, newName)) {
                    cloudMessage = "已重命名"
                    actionFile = null
                    reloadCurrent()
                } else {
                    cloudMessage = "重命名失败"
                }
            } catch (e: Exception) {
                cloudMessage = e.message ?: "重命名失败"
            } finally {
                isOperating = false
            }
        }
    }

    /** 移动文件到指定目录 */
    fun moveFile(toDirFid: String) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                if (!source.move(listOf(file), toDirFid)) throw IllegalStateException("移动失败")
                actionFile = null
                delayThenReload(delayAfterMoveMillis)
                cloudMessage = "已移动到目标目录"
            } catch (e: Exception) {
                cloudMessage = e.message ?: "移动失败"
            } finally {
                isOperating = false
            }
        }
    }

    /** 创建分享并查询链接 */
    fun shareFile(urlType: Int, passcode: String, expiredType: Int) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                shareResult = source.createShare(
                    listOf(file),
                    ShareRequest(UCSharePolicy.expireDays(expiredType), if (urlType == 2) passcode else "")
                )
                // 注意：不置空 actionFile —— FileActionSheet 依赖它存活，
                // 才能在其内部弹出 ShareResultDialog（置空会导致弹窗销毁、分享结果延迟显示）
            } catch (e: Exception) {
                cloudMessage = e.message ?: "分享失败"
            } finally {
                isOperating = false
            }
        }
    }

    /** 删除文件（二次确认由 UI 层负责） */
    fun deleteFile() {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                if (!source.delete(listOf(file))) throw IllegalStateException("删除失败")
                actionFile = null
                delayThenReload(delayAfterDeleteMillis)
                cloudMessage = "已删除「${file.fname}」"
            } catch (e: Exception) {
                cloudMessage = e.message ?: "删除失败"
            } finally {
                isOperating = false
            }
        }
    }

    // ---------- 批量操作（多选） ----------

    /** 批量下载：逐个取直链加入下载队列（选中文件夹时递归下载整个文件夹并保持目录结构） */
    fun downloadSelected() {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            folderProgress = "正在收集文件…"
            downloadCancelRequested = false
            try {
                val cookie = cookie()
                // 展开选中项：文件直接加入，文件夹递归收集
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                for (file in files) {
                    if (file.isdir) {
                        collectFolderFiles(file.fid, file.fname, tasks, 0)
                    } else {
                        tasks.add(file to file.fname)
                    }
                }
                if (tasks.isEmpty()) {
                    cloudMessage = "所选文件夹为空"
                    exitMultiSelect()
                    return@launch
                }
                val result = runDownloadBatch(tasks) { (file, relPath) ->
                    val link = source.downloadLink(file)
                        ?: throw IllegalStateException("获取下载链接失败")
                    downloadManager.enqueue(
                        url = link.downloadUrl,
                        fileName = if (relPath.contains('/')) relPath else link.filename.ifBlank { relPath },
                        size = link.size,
                        platform = DownloadPlatform.UC,
                        headers = source.downloadHeaders(cookie)
                    )
                    true
                }
                cloudMessage = downloadBatchMessage(result, "已中断批量下载")
                exitMultiSelect()
                // 批量下载不自动切页：保持网盘页显示处理中弹窗（单文件下载才切到下载页）
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                cloudMessage = e.message ?: "批量下载失败"
            } finally {
                isOperating = false
                folderProgress = null
                downloadCancelRequested = false
            }
        }
    }

    /** 批量分享选中文件 */
    fun shareSelected(urlType: Int, passcode: String, expiredType: Int) {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                shareResult = source.createShare(
                    files,
                    ShareRequest(UCSharePolicy.expireDays(expiredType), if (urlType == 2) passcode else "")
                )
                exitMultiSelect()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "分享失败"
            } finally {
                isOperating = false
            }
        }
    }

    /** 批量移动到指定目录 */
    fun moveSelected(toDirFid: String) {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                if (!source.move(files, toDirFid)) throw IllegalStateException("移动失败")
                exitMultiSelect()
                delayThenReload(delayAfterMoveMillis)
                cloudMessage = "已移动 ${files.size} 项"
            } catch (e: Exception) {
                cloudMessage = e.message ?: "移动失败"
            } finally {
                isOperating = false
            }
        }
    }

    /** 批量删除（二次确认由 UI 层负责） */
    fun deleteSelected() {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                if (!source.delete(files)) throw IllegalStateException("删除失败")
                exitMultiSelect()
                delayThenReload(delayAfterDeleteMillis)
                cloudMessage = "已删除 ${files.size} 项"
            } catch (e: Exception) {
                cloudMessage = e.message ?: "删除失败"
            } finally {
                isOperating = false
            }
        }
    }

    class Factory(
        private val source: CloudFileSource,
        private val cookieProvider: suspend () -> String?,
        private val downloadManager: DownloadManager,
        private val loginState: Flow<Boolean>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UCCloudViewModel(source, cookieProvider, downloadManager, loginState) as T
    }
}
