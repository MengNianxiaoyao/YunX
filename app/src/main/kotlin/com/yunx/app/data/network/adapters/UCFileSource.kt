package com.yunx.app.data.network.adapters

import com.yunx.app.data.network.CloudCapabilities
import com.yunx.app.data.network.CloudFileSource
import com.yunx.app.data.network.ShareRequest
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.UCConstants
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile

/**
 * UC Api → CloudFileSource 适配器（P2-3）。
 * 注意：取链含会员视频特殊回退（直链 → cloudGetDownloadLink → 分享链路原始直链），
 * 该逻辑现仍在 UCCloudViewModel（ucDownloadLink），P2-4 抽 BaseCloudViewModel 时迁入本 adapter。
 */
class UCFileSource(
    private val api: UCApi,
    private val cookieProvider: suspend () -> String?
) : CloudFileSource {

    override val capabilities = CloudCapabilities(
        name = "UC网盘",
        rootDir = "0",
        supportsShareVideoPreview = true
    )

    private suspend fun cookie(): String =
        cookieProvider() ?: throw IllegalStateException("请先登录 UC 网盘")

    override suspend fun list(dir: String, cursor: String?): Pair<List<ShareFile>, String?> {
        val page = cursor?.toIntOrNull() ?: 1
        val (files, hasMore) = api.listCloudFilesPage(dir, cookie(), page)
        return files to if (hasMore) (page + 1).toString() else null
    }

    override suspend fun downloadLink(file: ShareFile): DownloadLink? =
        api.getDownloadLink(file.fid, cookie())
            ?: api.cloudGetDownloadLink(file.fid, cookie())

    override fun downloadHeaders(credential: String?): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to UCConstants.USER_AGENT,
            "Referer" to UCConstants.DOWNLOAD_REFERER,
            "Origin" to UCConstants.WEB_ORIGIN
        )
        credential?.let { headers["Cookie"] = it }
        return headers
    }

    override suspend fun rename(file: ShareFile, newName: String): Boolean =
        api.renameFile(file.fid, newName, cookie())

    override suspend fun move(files: List<ShareFile>, toDir: String): Boolean {
        files.forEach { api.moveFile(it.fid, toDir, cookie()) }
        return true
    }

    override suspend fun delete(files: List<ShareFile>): Boolean {
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
