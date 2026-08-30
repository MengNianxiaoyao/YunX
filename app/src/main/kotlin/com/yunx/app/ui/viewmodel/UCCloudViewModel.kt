package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.UCConstants
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import kotlinx.coroutines.launch

/** UC 云盘浏览 UI 状态（P2-1：统一为 CloudUiState） */

/**
 * UC 网盘云盘浏览 ViewModel（参考夸克 QuarkCloudViewModel；P2-4：共性骨架见 BaseCloudViewModel）：
 * - 目录浏览（根/子目录/面包屑回退）
 * - 文件操作：下载 / 重命名 / 移动 / 创建分享 + 长按多选批量操作
 * 操作成功后自动刷新当前目录，结果通过 cloudMessage（Toast）反馈。
 */
class UCCloudViewModel(
    private val api: UCApi,
    private val cookieProvider: suspend () -> String?,
    private val downloadManager: DownloadManager
) : BaseCloudViewModel() {

    override val platformLoginHint = "请先登录 UC 网盘"
    override val rootDir = "0"

    // 初始加载放在子类 init（构造参数字段已赋值；基类 init 期间调用开放成员会 NPE）
    init {
        loadRoot()
    }

    override suspend fun listFiles(dir: String, cursor: String?): Pair<List<ShareFile>, String?>? {
        val cookie = cookieProvider()
        if (cookie.isNullOrBlank()) return null
        // UC 页码分页：首页 page=1；cursor 为下一页页码（返回 page+1，防止 loadMore 重复取当前页）
        val page = cursor?.toIntOrNull() ?: 1
        val (files, hasMore) = api.listCloudFilesPage(dir, cookie, page)
        return files to if (hasMore) (page + 1).toString() else null
    }

    private suspend fun cookie(): String =
        cookieProvider() ?: throw IllegalStateException(platformLoginHint)

    // ---------- 文件操作 ----------

    /** UC 下载直链的请求头（Cookie + UA + 防盗链 Referer/Origin） */
    private fun downloadHeaders(cookie: String): Map<String, String> = mapOf(
        "Cookie" to cookie,
        "User-Agent" to UCConstants.USER_AGENT,
        "Referer" to UCConstants.DOWNLOAD_REFERER,
        "Origin" to UCConstants.WEB_ORIGIN
    )

    /** 会员视频特殊取链：分享链路原始直链（绕过会员视频限速与转码切片） */
    private suspend fun ucVideoDownloadLinkViaShare(file: ShareFile, cookie: String): DownloadLink? {
        val shareId = api.createShare(
            fidList = listOf(file.fid),
            title = file.fname,
            urlType = 1,       // 1=公开提取
            passcode = "",
            expiredType = 2,   // 2=1 天
            cookie = cookie
        ) ?: return null
        // 分享信息接口返回的是**外层 pwd_id**，share_id 是内部 ID，直接拿 pwd_id 换 token 会 41006（分享不存在）
        val pwdId = api.getShareInfo(shareId, cookie)?.pwdId?.takeIf { it.isNotBlank() } ?: shareId
        // UC 分享链路：token → 文件列表 → video_preview 原始直链（返回即 DownloadLink）
        val token = api.getShareToken(pwdId, null, cookie) ?: return null
        val files = api.getTransferShareFiles(pwdId, token.stoken, "0", cookie) ?: return null
        val target = files.firstOrNull { it.fid == file.fid } ?: files.firstOrNull() ?: return null
        return api.getVideoPreview(
            pwdId = pwdId,
            stoken = token.stoken,
            fid = target.fid,
            fidToken = target.fidToken,
            cookie = cookie
        )?.copy(filename = file.fname)
    }

    /** 取文件直链（视频优先走分享链路原始直链，失败回退 play 接口；普通文件直接取链） */
    private suspend fun ucDownloadLink(fid: String, cookie: String, file: ShareFile): DownloadLink? {
        ucVideoDownloadLinkViaShare(file, cookie)?.let { return it }
        api.getPlayLink(fid, cookie)?.let { play ->
            return DownloadLink(
                fid = fid,
                filename = file.fname,
                downloadUrl = play.url,
                size = file.fsize,
                isHls = play.isHls
            )
        }
        return api.getDownloadLink(fid, cookie)
            ?: api.cloudGetDownloadLink(fid, cookie)
    }

    /**
     * 递归收集文件夹内所有文件（保持目录结构）。
     */
    private suspend fun collectFolderFiles(
        dirFid: String,
        prefix: String,
        cookie: String,
        result: MutableList<Pair<ShareFile, String>>,
        depth: Int
    ) {
        if (depth > 12) return
        val list = runCatching { api.listCloudFiles(dirFid, cookie) ?: emptyList() }
            .getOrDefault(emptyList())
        // 先文件后文件夹（与目录列表展示顺序一致）
        list.filter { !it.isdir }.forEach { result.add(it to "$prefix/${it.fname}") }
        list.filter { it.isdir }.forEach {
            collectFolderFiles(it.fid, "$prefix/${it.fname}", cookie, result, depth + 1)
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
                collectFolderFiles(folder.fid, folder.fname, cookie, tasks, 0)
                if (tasks.isEmpty()) {
                    cloudMessage = "文件夹为空"
                    actionFile = null
                    return@launch
                }
                var okCount = 0
                tasks.forEachIndexed { index, (file, relPath) ->
                    // 用户点击「中断」：跳过剩余项（已入队任务保留下载）
                    if (downloadCancelRequested) return@forEachIndexed
                    folderProgress = "正在加入下载 ${index + 1}/${tasks.size}"
                    runCatching {
                        val link = ucDownloadLink(file.fid, cookie, file) ?: return@runCatching
                        downloadManager.enqueue(
                            url = link.downloadUrl,
                            fileName = relPath, // 相对路径：Download/文件夹A/子目录/文件.mp4
                            size = link.size,
                            platform = DownloadPlatform.UC,
                            headers = downloadHeaders(cookie)
                        )
                        okCount++
                    }
                }
                if (downloadCancelRequested) {
                    cloudMessage = "已中断下载"
                    actionFile = null
                    return@launch
                }
                cloudMessage = "已加入 $okCount 个下载任务"
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
                val link = ucDownloadLink(file.fid, cookie, file)
                    ?: throw IllegalStateException("获取下载链接失败")
                pendingDownload = PendingDownload(
                    url = link.downloadUrl,
                    fileName = link.filename.ifBlank { file.fname },
                    size = link.size,
                    headers = mapOf(
                        "Cookie" to cookie,
                        // UC OSS 直链必须带官方 Referer（否则回调限速 ~100KB/s，加 Origin 对齐网页 UC 行为）
                        "User-Agent" to UCConstants.USER_AGENT,
                        "Referer" to UCConstants.DOWNLOAD_REFERER,
                        "Origin" to UCConstants.WEB_ORIGIN
                    )
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
                    platform = DownloadPlatform.UC,
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
                if (api.renameFile(file.fid, newName, cookie())) {
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
                api.moveFile(file.fid, toDirFid, cookie())
                    ?: throw IllegalStateException("移动失败")
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
                val cookie = cookie()
                val shareId = api.createShare(
                    fidList = listOf(file.fid),
                    title = file.fname,
                    urlType = urlType,
                    passcode = passcode,
                    expiredType = expiredType,
                    cookie = cookie
                ) ?: throw IllegalStateException("创建分享失败")
                val info = api.getShareInfo(shareId, cookie)
                    ?: throw IllegalStateException("获取分享链接失败")
                shareResult = info
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
                api.deleteFile(file.fid, cookie())
                    ?: throw IllegalStateException("删除失败")
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
                        collectFolderFiles(file.fid, file.fname, cookie, tasks, 0)
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
                    runCatching {
                        val link = ucDownloadLink(file.fid, cookie, file) ?: return@runCatching
                        downloadManager.enqueue(
                            url = link.downloadUrl,
                            fileName = if (relPath.contains('/')) relPath else link.filename.ifBlank { relPath },
                            size = link.size,
                            platform = DownloadPlatform.UC,
                            headers = downloadHeaders(cookie)
                        )
                        okCount++
                    }
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
                // 批量下载不自动切页：保持网盘页显示处理中弹窗（单文件下载才切到下载页）
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
                val cookie = cookie()
                val shareId = api.createShare(
                    fidList = files.map { it.fid },
                    title = if (files.size == 1) files[0].fname else "分享 ${files.size} 个文件",
                    urlType = urlType,
                    passcode = passcode,
                    expiredType = expiredType,
                    cookie = cookie
                ) ?: throw IllegalStateException("创建分享失败")
                val info = api.getShareInfo(shareId, cookie)
                    ?: throw IllegalStateException("获取分享链接失败")
                shareResult = info
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
                val cookie = cookie()
                files.forEach { file ->
                    api.moveFile(file.fid, toDirFid, cookie)
                }
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
                val cookie = cookie()
                files.forEach { file ->
                    api.deleteFile(file.fid, cookie)
                }
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
        private val api: UCApi,
        private val cookieProvider: suspend () -> String?,
        private val downloadManager: DownloadManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UCCloudViewModel(api, cookieProvider, downloadManager) as T
    }
}
