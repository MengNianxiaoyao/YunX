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
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import kotlinx.coroutines.launch

/** 百度网盘云盘浏览 UI 状态（P2-1：统一为 CloudUiState；dir 为绝对路径，根="/"） */

/**
 * 百度网盘云盘浏览 ViewModel（参考夸克/UC/迅雷云盘；P2-4：共性骨架见 BaseCloudViewModel）：
 * - 目录浏览（根/子目录/面包屑回退）+ 下拉刷新
 * - 文件操作：下载 / 重命名 / 移动 / 创建分享 / 删除 + 长按多选批量
 * 认证：Cookie（BDUSS），目录用绝对路径，文件标识 fs_id + path。
 */
class BaiduCloudViewModel(
    private val source: CloudFileSource,
    private val cookieProvider: suspend () -> String?,
    private val downloadManager: DownloadManager
) : BaseCloudViewModel() {

    override val platformLoginHint = "请先登录${source.capabilities.name}"
    override val rootDir = source.capabilities.rootDir

    // 初始加载放在子类 init（构造参数字段已赋值；基类 init 期间调用开放成员会 NPE）
    init {
        loadRoot()
    }

    private suspend fun cookie(): String =
        cookieProvider() ?: throw IllegalStateException(platformLoginHint)

    override suspend fun listFiles(dir: String, cursor: String?): Pair<List<ShareFile>, String?>? {
        return source.list(dir, cursor)
    }

    // ---------- 单文件操作 ----------

    private suspend fun collectFolderFiles(
        dirPath: String,
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
            val page = runCatching { source.list(dirPath, cursor) }.getOrNull() ?: break
            list += page.first
            cursor = page.second
            pageCount++
        } while (cursor != null && seenCursors.add(cursor) && pageCount < 100)
        list.filter { !it.isdir }.forEach { result.add(it to "$prefix/${it.fname}") }
        list.filter { it.isdir }.forEach {
            collectFolderFiles(it.fidToken, "$prefix/${it.fname}", result, depth + 1)
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
                collectFolderFiles(folder.fidToken, folder.fname, tasks, 0)
                if (tasks.isEmpty()) {
                    cloudMessage = "文件夹为空"
                    actionFile = null
                    return@launch
                }
                var okCount = 0
                var failCount = 0
                tasks.forEachIndexed { index, (file, relPath) ->
                    // 用户点击「中断」：跳过剩余项（已入队任务保留下载）
                    if (downloadCancelRequested) return@forEachIndexed
                    folderProgress = "正在加入下载 ${index + 1}/${tasks.size}"
                    val enqueued = runCatching {
                        val link = source.downloadLink(file)
                            ?: throw IllegalStateException("获取下载链接失败")
                        downloadManager.enqueue(
                            url = link.downloadUrl,
                            fileName = relPath, // 相对路径：Download/文件夹A/子目录/文件.mp4
                            size = file.fsize,
                            platform = DownloadPlatform.BAIDU,
                            headers = source.downloadHeaders(cookie)
                        )
                    }.isSuccess
                    if (enqueued) okCount++ else failCount++
                }
                if (downloadCancelRequested) {
                    cloudMessage = "已中断下载"
                    actionFile = null
                    return@launch
                }
                cloudMessage = if (failCount > 0) {
                    "已加入 $okCount 个下载任务（$failCount 个失败）"
                } else {
                    "已加入 $okCount 个下载任务"
                }
                actionFile = null
            } catch (e: Exception) {
                cloudMessage = e.message ?: "下载文件夹失败"
            } finally {
                isOperating = false
                folderProgress = null
                downloadCancelRequested = false
            }
        }
    }

    /** 下载：locatedownload 取直链（需 Cookie + netdisk UA）→ 内置下载队列 */
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
                    fileName = file.fname,
                    size = file.fsize,
                    headers = source.downloadHeaders(cookie)
                )
                downloadLink = link // 弹下载确认弹窗（长按直链可复制）
            } catch (e: Exception) {
                cloudMessage = e.message ?: "下载失败"
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
                    platform = DownloadPlatform.BAIDU,
                    headers = pd.headers
                )
                cloudMessage = "已加入下载：${pd.fileName}"
                actionFile = null
                downloadTriggered++
            } catch (e: Exception) {
                cloudMessage = e.message ?: "下载失败"
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

    /** 移动 */
    fun moveFile(toDirPath: String) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                if (!source.move(listOf(file), toDirPath)) throw IllegalStateException("移动失败")
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

    /** 创建分享（百度必须带 4 位提取码） */
    fun shareFile(period: Int, pwd: String) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                shareResult = source.createShare(listOf(file), ShareRequest(period.takeIf { it > 0 }, pwd))
                // 不清空 actionFile：弹窗存活才能显示分享结果
            } catch (e: Exception) {
                cloudMessage = e.message ?: "分享失败"
            } finally {
                isOperating = false
            }
        }
    }

    /** 删除 */
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

    // ---------- 批量操作 ----------

    /** 批量下载（不切页；选中文件夹时递归下载整个文件夹并保持目录结构） */
    fun downloadSelected() {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            folderProgress = "正在收集文件…"
            downloadCancelRequested = false
            try {
                val cookie = cookie()
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                for (file in files) {
                    if (file.isdir) {
                        collectFolderFiles(file.fidToken, file.fname, tasks, 0)
                    } else {
                        tasks.add(file to file.fname)
                    }
                }
                if (tasks.isEmpty()) {
                    cloudMessage = "所选文件夹为空"
                    exitMultiSelect()
                    return@launch
                }
                var okCount = 0
                var failCount = 0
                tasks.forEachIndexed { index, (file, relPath) ->
                    // 用户点击「中断」：跳过剩余项（已入队任务保留下载）
                    if (downloadCancelRequested) return@forEachIndexed
                    folderProgress = "正在加入下载 ${index + 1}/${tasks.size}"
                    val enqueued = runCatching {
                        val link = source.downloadLink(file)
                            ?: throw IllegalStateException("获取下载链接失败")
                        downloadManager.enqueue(
                            url = link.downloadUrl,
                            fileName = if (relPath.contains('/')) relPath else file.fname,
                            size = file.fsize,
                            platform = DownloadPlatform.BAIDU,
                            headers = source.downloadHeaders(cookie)
                        )
                    }.isSuccess
                    if (enqueued) okCount++ else failCount++
                }
                if (downloadCancelRequested) {
                    cloudMessage = "已中断批量下载"
                    exitMultiSelect()
                    return@launch
                }
                cloudMessage = if (failCount > 0) {
                    "已加入 $okCount 个下载任务（$failCount 个失败）"
                } else {
                    "已加入 $okCount 个下载任务"
                }
                exitMultiSelect()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "批量下载失败"
            } finally {
                isOperating = false
                folderProgress = null
                downloadCancelRequested = false
            }
        }
    }

    /** 批量分享 */
    fun shareSelected(period: Int, pwd: String) {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                shareResult = source.createShare(files, ShareRequest(period.takeIf { it > 0 }, pwd))
                exitMultiSelect()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "分享失败"
            } finally {
                isOperating = false
            }
        }
    }

    /** 批量移动 */
    fun moveSelected(toDirPath: String) {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                if (!source.move(files, toDirPath)) throw IllegalStateException("移动失败")
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

    /** 批量删除 */
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
        private val downloadManager: DownloadManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BaiduCloudViewModel(source, cookieProvider, downloadManager) as T
    }
}
