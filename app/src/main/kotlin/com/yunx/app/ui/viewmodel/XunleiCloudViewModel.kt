package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.XunleiConstants
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import kotlinx.coroutines.launch

/** 迅雷云盘浏览 UI 状态（P2-1：统一为 CloudUiState） */

/**
 * 迅雷云盘浏览 ViewModel（参考夸克/UC QuarkCloudViewModel；P2-4：共性骨架见 BaseCloudViewModel）：
 * - 目录浏览（根/子目录/面包屑回退）+ 下拉刷新
 * - 文件操作：下载 / 重命名 / 移动 / 创建分享 / 删除 + 长按多选
 * 认证：token（Bearer）+ 设备指纹 + captcha。
 */
class XunleiCloudViewModel(
    private val api: XunleiApi,
    private val tokenProvider: suspend () -> String?,
    private val deviceIdProvider: suspend () -> String?,
    private val captchaProvider: suspend () -> String?,
    private val downloadManager: DownloadManager
) : BaseCloudViewModel() {

    override val platformLoginHint = "请先登录迅雷网盘"
    override val rootDir = ""

    // 初始加载放在子类 init（构造参数字段已赋值；基类 init 期间调用开放成员会 NPE）
    init {
        loadRoot()
    }

    /** 凭证三元组：token/deviceId 缺失视为未登录 */
    private suspend fun creds(): Triple<String, String, String>? {
        val token = tokenProvider() ?: return null
        val deviceId = deviceIdProvider() ?: return null
        val captcha = captchaProvider() ?: ""
        api.cacheUserId(token)
        return Triple(token, deviceId, captcha)
    }

    override suspend fun listFiles(dir: String, cursor: String?): Pair<List<ShareFile>, String?>? {
        val c = creds() ?: return null
        // 迅雷 pageToken 游标：cursor 直接透传（首页空串）
        return api.getFilesPage(dir, c.first, c.second, c.third, cursor ?: "")
    }

    private suspend fun requireCreds(): Triple<String, String, String> =
        creds() ?: throw IllegalStateException(platformLoginHint)

    // ---------- 文件操作 ----------

    /** 迅雷下载直链的请求头（签名 URL；UA 必须用官方 app UA，浏览器 UA 会触发 CDN 降级 200 整文件） */
    private fun downloadHeaders(): Map<String, String> = mapOf(
        "User-Agent" to XunleiConstants.APP_UA
    )

    /**
     * 递归收集文件夹内所有文件（保持目录结构）。
     */
    private suspend fun collectFolderFiles(
        dirFid: String,
        prefix: String,
        c: Triple<String, String, String>,
        result: MutableList<Pair<ShareFile, String>>,
        depth: Int
    ) {
        if (depth > 12) return
        val list = runCatching { api.getFiles(dirFid, c.first, c.second, c.third) ?: emptyList() }
            .getOrDefault(emptyList())
        list.filter { !it.isdir }.forEach { result.add(it to "$prefix/${it.fname}") }
        list.filter { it.isdir }.forEach {
            collectFolderFiles(it.fid, "$prefix/${it.fname}", c, result, depth + 1)
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
                val c = requireCreds()
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                collectFolderFiles(folder.fid, folder.fname, c, tasks, 0)
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
                        val link = api.getFileDetail(file.fid, c.first, c.second, c.third)
                            ?: return@runCatching
                        downloadManager.enqueue(
                            url = link.downloadUrl,
                            fileName = relPath, // 相对路径：Download/文件夹A/子目录/文件.mp4
                            size = link.size,
                            platform = DownloadPlatform.XUNLEI,
                            headers = downloadHeaders()
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

    /** 下载：文件详情取直链（签名 URL，无需 Cookie）→ 内置下载队列 */
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
                val c = requireCreds()
                val link = api.getFileDetail(file.fid, c.first, c.second, c.third)
                    ?: throw IllegalStateException("获取下载链接失败")
                pendingDownload = PendingDownload(
                    url = link.downloadUrl,
                    fileName = link.filename.ifBlank { file.fname },
                    size = link.size,
                    headers = mapOf("User-Agent" to XunleiConstants.APP_UA)
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
                    platform = DownloadPlatform.XUNLEI,
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
                val c = requireCreds()
                if (api.renameFile(file.fid, newName, c.first, c.second, c.third)) {
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
                val c = requireCreds()
                api.moveFile(listOf(file.fid), toDirFid, c.first, c.second, c.third)
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

    /** 删除文件（二次确认由 UI 层负责） */
    fun deleteFile() {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                val c = requireCreds()
                api.deleteFiles(listOf(file.fid), c.first, c.second, c.third)
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

    /** 创建分享（迅雷必带提取码；可自动生成，可自定义 4 位）
     *  @param expiredType 1=永久 2=1天 3=7天 4=30天（内部映射为 API 的 "-1"/"1"/"7"/"30"）
     *  @param passCode 自定义提取码（4 位字母数字），空则由服务端自动生成
     */
    fun shareFile(expiredType: Int, passCode: String = "") {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                val c = requireCreds()
                val info = api.createShare(
                    listOf(file.fid), file.fname, expireDays(expiredType),
                    c.first, c.second, c.third, passCode
                ) ?: throw IllegalStateException("创建分享失败")
                shareResult = info.copy(expiredType = expiredType)
                // 不清空 actionFile（保持弹窗内展示分享结果）
            } catch (e: Exception) {
                cloudMessage = e.message ?: "分享失败"
            } finally {
                isOperating = false
            }
        }
    }

    // ---------- 批量操作（多选） ----------

    /** 批量下载（保持网盘页显示处理中弹窗；选中文件夹时递归下载整个文件夹并保持目录结构） */
    fun downloadSelected() {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            folderProgress = "正在收集文件…"
            downloadCancelRequested = false
            try {
                val c = requireCreds()
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                for (file in files) {
                    if (file.isdir) {
                        collectFolderFiles(file.fid, file.fname, c, tasks, 0)
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
                        val link = api.getFileDetail(file.fid, c.first, c.second, c.third)
                            ?: return@runCatching
                        downloadManager.enqueue(
                            url = link.downloadUrl,
                            fileName = if (relPath.contains('/')) relPath else link.filename.ifBlank { relPath },
                            size = link.size,
                            platform = DownloadPlatform.XUNLEI,
                            headers = downloadHeaders()
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
    fun shareSelected(expiredType: Int, passCode: String = "") {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                val c = requireCreds()
                val info = api.createShare(
                    files.map { it.fid },
                    if (files.size == 1) files[0].fname else "分享 ${files.size} 个文件",
                    expireDays(expiredType),
                    c.first, c.second, c.third, passCode
                ) ?: throw IllegalStateException("创建分享失败")
                shareResult = info.copy(expiredType = expiredType)
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
                val c = requireCreds()
                api.moveFile(files.map { it.fid }, toDirFid, c.first, c.second, c.third)
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
                val c = requireCreds()
                api.deleteFiles(files.map { it.fid }, c.first, c.second, c.third)
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

    /** expiredType（1=永久 2=1天 3=7天 4=30天）→ API expiration_days */
    private fun expireDays(type: Int): String = when (type) {
        2 -> "1"
        3 -> "7"
        4 -> "30"
        else -> "-1"
    }

    class Factory(
        private val api: XunleiApi,
        private val tokenProvider: suspend () -> String?,
        private val deviceIdProvider: suspend () -> String?,
        private val captchaProvider: suspend () -> String?,
        private val downloadManager: DownloadManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            XunleiCloudViewModel(api, tokenProvider, deviceIdProvider, captchaProvider, downloadManager) as T
    }
}
