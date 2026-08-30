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
 * 会员视频的分享预览与播放流回退全部封装在本适配器，调用方只消费 DownloadLink。
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

    override suspend fun downloadLink(file: ShareFile): DownloadLink? {
        val cookie = cookie()
        videoDownloadLinkViaShare(file, cookie)?.let { return it }
        api.getPlayLink(file.fid, cookie)?.let { play ->
            return DownloadLink(
                fid = file.fid,
                filename = file.fname,
                downloadUrl = play.url,
                size = file.fsize,
                isHls = play.isHls
            )
        }
        return api.getDownloadLink(file.fid, cookie)
            ?: api.cloudGetDownloadLink(file.fid, cookie)
    }

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
        val cookie = cookie()
        var succeeded = true
        files.forEach { if (api.moveFile(it.fid, toDir, cookie) == null) succeeded = false }
        return succeeded
    }

    override suspend fun delete(files: List<ShareFile>): Boolean {
        val cookie = cookie()
        var succeeded = true
        files.forEach { if (api.deleteFile(it.fid, cookie) == null) succeeded = false }
        return succeeded
    }

    override suspend fun createShare(files: List<ShareFile>, request: ShareRequest) = cookie().let { cookie ->
        api.createShare(
            fidList = files.map { it.fid },
            title = if (files.size == 1) files[0].fname else "分享 ${files.size} 个文件",
            urlType = if (request.passcode.isBlank()) 1 else 2,
            passcode = request.passcode,
            expiredType = UCSharePolicy.expireType(request.expireDays),
            cookie = cookie
        )?.let { shareId ->
            api.getShareInfo(shareId, cookie)
                ?: throw IllegalStateException("获取分享信息失败")
        } ?: throw IllegalStateException("创建分享失败")
    }

    override suspend fun quota() = api.getQuota(cookie())

    private suspend fun videoDownloadLinkViaShare(file: ShareFile, cookie: String): DownloadLink? {
        val shareId = api.createShare(
            fidList = listOf(file.fid),
            title = file.fname,
            urlType = 1,
            passcode = "",
            expiredType = 2,
            cookie = cookie
        ) ?: return null
        val pwdId = api.getShareInfo(shareId, cookie)?.pwdId?.takeIf { it.isNotBlank() } ?: shareId
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
}

internal object UCSharePolicy {
    fun expireType(days: Int?): Int = when (days) {
        null -> 1
        1 -> 2
        7 -> 3
        else -> 4
    }

    fun expireDays(expiredType: Int): Int? = when (expiredType) {
        1 -> null
        2 -> 1
        3 -> 7
        else -> 30
    }
}
