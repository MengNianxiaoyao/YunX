package com.yunx.app.data.network.adapters

import com.yunx.app.data.network.CloudCapabilities
import com.yunx.app.data.network.CloudFileSource
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.QuarkConstants
import com.yunx.app.data.network.ShareRequest
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile

/**
 * 夸克 Api → CloudFileSource 适配器（P2-3）。
 * 凭证经 cookieProvider 闭包动态获取；有效期归一（null/1/7/30 天）→ 夸克 expired_type 枚举（1-4）。
 */
class QuarkFileSource(
    private val api: QuarkApi,
    private val cookieProvider: suspend () -> String?
) : CloudFileSource {

    override val capabilities = CloudCapabilities(
        name = "夸克网盘",
        rootDir = "0",
        requiresTransferForShareDownload = true
    )

    private suspend fun cookie(): String =
        cookieProvider() ?: throw IllegalStateException("请先登录夸克网盘")

    override suspend fun list(dir: String, cursor: String?): Pair<List<ShareFile>, String?> {
        // 夸克分页为页码（游标语义化：cursor 为上一页页码字符串，null 为首页）
        val page = cursor?.toIntOrNull() ?: 1
        val (files, hasMore) = api.listCloudFilesPage(dir, cookie(), page)
        return files to if (hasMore) (page + 1).toString() else null
    }

    override suspend fun downloadLink(file: ShareFile): DownloadLink? =
        api.getDownloadLink(file.fid, cookie())

    override fun downloadHeaders(credential: String?): Map<String, String> {
        // 直链与登录态绑定：Cookie 必随请求携带（credential 由调用方在取链后传入）
        val headers = mutableMapOf(
            "User-Agent" to QuarkConstants.API_USER_AGENT,
            "Referer" to QuarkConstants.DOWNLOAD_REFERER
        )
        credential?.let { headers["Cookie"] = it }
        return headers
    }

    override suspend fun rename(file: ShareFile, newName: String): Boolean =
        api.renameFile(file.fid, newName, cookie())

    override suspend fun move(files: List<ShareFile>, toDir: String): Boolean {
        // 夸克单文件 moveFile（批量循环；与 VM 现状一致）
        files.forEach { api.moveFile(it.fid, toDir, cookie()) }
        return true
    }

    override suspend fun delete(files: List<ShareFile>): Boolean {
        // 夸克单文件 deleteFile（批量循环；与 VM 现状一致）
        files.forEach { api.deleteFile(it.fid, cookie()) }
        return true
    }

    override suspend fun createShare(files: List<ShareFile>, request: ShareRequest) =
        api.createShare(
            fidList = files.map { it.fid },
            title = if (files.size == 1) files[0].fname else "批量 ${files.size} 个文件",
            urlType = 1,
            passcode = request.passcode,
            expiredType = expireType(request.expireDays),
            cookie = cookie()
        )?.let { shareId ->
            api.getShareInfo(shareId, cookie())
                ?: throw IllegalStateException("获取分享信息失败")
        } ?: throw IllegalStateException("创建分享失败")

    override suspend fun quota() = api.getQuota(cookie())

    private fun expireType(days: Int?): Int = when (days) {
        null -> 1
        1 -> 2
        7 -> 3
        else -> 4
    }
}
