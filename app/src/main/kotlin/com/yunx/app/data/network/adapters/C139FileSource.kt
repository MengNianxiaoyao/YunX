package com.yunx.app.data.network.adapters

import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.C139Constants
import com.yunx.app.data.network.CloudCapabilities
import com.yunx.app.data.network.CloudFileSource
import com.yunx.app.data.network.ShareRequest
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo
import kotlinx.coroutines.delay

/**
 * 139 Api → CloudFileSource 适配器（P2-3）。
 * 移动/删除为异步任务（taskId），adapter 内部轮询至完成（与 VM 现状 pollTask 一致）；
 * 分享无提取码（系统自动生成，capabilities 已声明）。
 */
class C139FileSource(
    private val api: C139Api,
    private val cookieProvider: suspend () -> String?
) : CloudFileSource {

    override val capabilities = CloudCapabilities(
        name = "139网盘",
        rootDir = "/",
        shareSupportsPasscode = false
    )

    private suspend fun cookie(): String =
        cookieProvider() ?: throw IllegalStateException("请先登录 139 网盘")

    override suspend fun list(dir: String, cursor: String?): Pair<List<ShareFile>, String?> =
        api.listCloudFiles(dir, cookie(), cursor)

    override suspend fun downloadLink(file: ShareFile): DownloadLink? =
        api.getDownloadUrl(file.fid, cookie())

    override fun downloadHeaders(credential: String?): Map<String, String> = mapOf(
        "User-Agent" to C139Constants.PC_UA,
        "Referer" to "https://yun.139.com/"
    )

    override suspend fun rename(file: ShareFile, newName: String): Boolean =
        api.renameFile(file.fid, newName, cookie())

    override suspend fun move(files: List<ShareFile>, toDir: String): Boolean {
        val cookie = cookie()
        val taskId = api.moveFiles(files.map { it.fid }, toDir, cookie) ?: return false
        pollTask(taskId, cookie)
        return true
    }

    override suspend fun delete(files: List<ShareFile>): Boolean {
        val cookie = cookie()
        val taskId = api.deleteFiles(files.map { it.fid }, cookie) ?: return false
        pollTask(taskId, cookie)
        return true
    }

    override suspend fun createShare(files: List<ShareFile>, request: ShareRequest): ShareInfo {
        // 139 文件(coIDLst)/目录(caIDLst)分列；提取码由系统生成（请求不支持自定义）
        val coLst = files.filter { !it.isdir }.map { it.fid }
        val caLst = files.filter { it.isdir }.map { it.fid }
        return api.createShare(
            coLst, caLst,
            request.expireDays,
            if (files.size == 1) files[0].fname else "分享 ${files.size} 个文件",
            cookie()
        )
    }

    override suspend fun quota(): QuotaInfo? = api.getQuota(cookie())

    /** 异步任务轮询（与原 VM pollTask 一致：500ms 首查 + 800ms×30 上限） */
    private suspend fun pollTask(taskId: String, cookie: String) {
        delay(500)
        repeat(30) {
            val status = api.getTask(taskId, cookie)
            if (status.status == "Succeed" || status.progress >= 100) return
            val errorCode = status.results.firstOrNull {
                it.second.isNotBlank() && it.second != "0000"
            }?.second
            if (errorCode != null) {
                throw IllegalStateException("操作失败（$errorCode）")
            }
            delay(800)
        }
        throw IllegalStateException("操作超时")
    }
}
