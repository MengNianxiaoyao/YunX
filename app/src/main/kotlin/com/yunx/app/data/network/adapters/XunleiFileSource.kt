package com.yunx.app.data.network.adapters

import com.yunx.app.data.network.CloudCapabilities
import com.yunx.app.data.network.CloudFileSource
import com.yunx.app.data.network.ShareRequest
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.XunleiConstants
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo

/**
 * 迅雷 Api → CloudFileSource 适配器（P2-3）。
 * 凭证为 token/deviceId/captcha 三元组，经三个 provider 闭包动态获取；
 * cacheUserId 与 VM 现状一致在取凭证时刷新。
 */
class XunleiFileSource(
    private val api: XunleiApi,
    private val tokenProvider: suspend () -> String?,
    private val deviceIdProvider: suspend () -> String?,
    private val captchaProvider: suspend () -> String?
) : CloudFileSource {

    override val capabilities = CloudCapabilities(
        name = "迅雷网盘",
        rootDir = "",
        requiresTransferForShareDownload = true
    )

    /** 凭证三元组：token/deviceId 缺失视为未登录 */
    private suspend fun creds(): Triple<String, String, String>? {
        val token = tokenProvider() ?: return null
        val deviceId = deviceIdProvider() ?: return null
        val captcha = captchaProvider() ?: ""
        api.cacheUserId(token)
        return Triple(token, deviceId, captcha)
    }

    private suspend fun requireCreds(): Triple<String, String, String> =
        creds() ?: throw IllegalStateException("请先登录迅雷网盘")

    override suspend fun list(dir: String, cursor: String?): Pair<List<ShareFile>, String?> {
        val c = requireCreds()
        return api.getFilesPage(dir, c.first, c.second, c.third, cursor ?: "")
    }

    override suspend fun downloadLink(file: ShareFile): DownloadLink? {
        val c = requireCreds()
        return api.getFileDetail(file.fid, c.first, c.second, c.third)
    }

    override fun downloadHeaders(credential: String?): Map<String, String> = mapOf(
        "User-Agent" to XunleiConstants.APP_UA
    )

    override suspend fun rename(file: ShareFile, newName: String): Boolean {
        val c = requireCreds()
        return api.renameFile(file.fid, newName, c.first, c.second, c.third)
    }

    override suspend fun move(files: List<ShareFile>, toDir: String): Boolean {
        val c = requireCreds()
        // 迅雷原生批量接口（batchMove）
        return api.moveFile(files.map { it.fid }, toDir, c.first, c.second, c.third) != null
    }

    override suspend fun delete(files: List<ShareFile>): Boolean {
        val c = requireCreds()
        return api.deleteFiles(files.map { it.fid }, c.first, c.second, c.third)
    }

    override suspend fun createShare(files: List<ShareFile>, request: ShareRequest): ShareInfo {
        val c = requireCreds()
        val info = api.createShare(
            files.map { it.fid },
            if (files.size == 1) files[0].fname else "分享 ${files.size} 个文件",
            XunleiSharePolicy.expireDays(request.expireDays),
            c.first, c.second, c.third,
            request.passcode
        ) ?: throw IllegalStateException("创建分享失败")
        return info.copy(expiredType = XunleiSharePolicy.expireType(request.expireDays))
    }

    override suspend fun quota(): QuotaInfo? {
        val c = creds() ?: return null
        return api.getQuota(c.first, c.second, c.third)
    }

}

internal object XunleiSharePolicy {
    fun expireDays(days: Int?): String = when (days) {
        1 -> "1"
        7 -> "7"
        30 -> "30"
        else -> "-1"
    }

    fun expireType(days: Int?): Int = when (days) {
        1 -> 2
        7 -> 3
        30 -> 4
        else -> 1
    }

    fun normalizedDays(expiredType: Int): Int? = when (expiredType) {
        2 -> 1
        3 -> 7
        4 -> 30
        else -> null
    }
}
