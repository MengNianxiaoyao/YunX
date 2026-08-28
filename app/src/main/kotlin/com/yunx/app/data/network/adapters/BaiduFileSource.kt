package com.yunx.app.data.network.adapters

import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.BaiduConstants
import com.yunx.app.data.network.CloudCapabilities
import com.yunx.app.data.network.CloudFileSource
import com.yunx.app.data.network.ShareRequest
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo

/**
 * 百度 Api → CloudFileSource 适配器（P2-3）。
 * 目录/文件均用绝对路径（dir / fidToken 字段）；分享强制 4 位提取码；
 * locatedownload 直链与账号绑定，Cookie 需随下载头携带。
 */
class BaiduFileSource(
    private val api: BaiduApi,
    private val cookieProvider: suspend () -> String?
) : CloudFileSource {

    override val capabilities = CloudCapabilities(
        name = "百度网盘",
        rootDir = "/",
        shareRequiresPasscode = true,
        sharePasscodeLength = 4
    )

    private suspend fun cookie(): String =
        cookieProvider() ?: throw IllegalStateException("请先登录百度网盘")

    override suspend fun list(dir: String, cursor: String?): Pair<List<ShareFile>, String?> {
        val page = cursor?.toIntOrNull() ?: 1
        val (files, hasMore) = api.listCloudFilesPage(dir, cookie(), page)
        return files to if (hasMore) (page + 1).toString() else null
    }

    override suspend fun downloadLink(file: ShareFile): DownloadLink? {
        val link = api.locateDownload(file.fidToken, cookie())
        return DownloadLink(
            fid = file.fid,
            filename = file.fname,
            downloadUrl = link,
            size = file.fsize
        )
    }

    override fun downloadHeaders(credential: String?): Map<String, String> {
        // locatedownload 直链与 BDUSS 绑定：Cookie 必随请求携带
        val headers = mutableMapOf(
            "User-Agent" to BaiduConstants.UA_NETDISK
        )
        credential?.let { headers["Cookie"] = it }
        return headers
    }

    override suspend fun rename(file: ShareFile, newName: String): Boolean =
        api.renameFile(file.fidToken, newName, cookie())

    override suspend fun move(files: List<ShareFile>, toDir: String): Boolean {
        api.moveFiles(files.map { it.fidToken }, toDir, cookie())
        return true
    }

    override suspend fun delete(files: List<ShareFile>): Boolean {
        api.deleteFiles(files.map { it.fidToken }, cookie())
        return true
    }

    override suspend fun createShare(files: List<ShareFile>, request: ShareRequest): ShareInfo {
        // 百度强制 4 位提取码（UI 按 capabilities.shareRequiresPasscode 保证输入）；
        // ShareRequest 归一的有效期天数即百度原生 period 语义（0=永久）
        val result = api.createShare(
            files.map { it.fid },
            request.expireDays ?: 0,
            request.passcode,
            cookie()
        )
        return ShareInfo(
            shareUrl = result.link,
            passcode = result.pwd,
            pwdId = result.shareId,
            title = if (files.size == 1) files[0].fname else "批量 ${files.size} 个文件",
            expiredType = expireType(request.expireDays ?: 0)
        )
    }

    override suspend fun quota(): QuotaInfo? = api.getQuota(cookie())

    /** 百度 period（0/1/7/30 天）→ ShareInfo.expiredType（1 永久/2 一天/3 七天/4 三十天） */
    private fun expireType(period: Int): Int = when (period) {
        1 -> 2
        7 -> 3
        30 -> 4
        else -> 1
    }
}
