package com.yunx.app.data.repository

import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

/**
 * 分享解析仓库公共接口：平台差异由实现封装，解析页统一执行会话、列表、转存和取链流程。
 */
interface ShareResolveRepository {
    suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession>
    suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>>
    suspend fun listFilesPage(
        session: ShareSession,
        dirFid: String,
        cookie: String,
        cursor: String?
    ): Result<ShareFilePage> = listFiles(session, dirFid, cookie).map { ShareFilePage(it, null) }
    suspend fun ensureTempDir(cookie: String): Result<String>
    /** 将分享文件转存到指定个人网盘目录，成功值为转存后的文件标识。 */
    suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String>
    suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink>

    /**
     * 获取分享文件下载直链（平台差异在此收敛）：
     * - 夸克：转存到临时目录 → 用转存后新 fid 取直链；
     * - UC：直接用分享 fid + stoken + fid_token 取直链（无需转存）。
     */
    suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink>

    /**
     * 下载完成后清理临时转存目录（夸克实现删除 tr_* 子目录；其它平台默认空实现）。
     * @param dirFid DownloadLink.cleanupDirFid 带回的临时目录 fid
     */
    suspend fun cleanupTempDir(dirFid: String, cookie: String) {}
}

data class ShareFilePage(
    val files: List<ShareFile>,
    val nextCursor: String?
)

internal object SharePagingPolicy {
    fun pageNumber(cursor: String?): Int = cursor?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    fun nextPageCursor(currentPage: Int, itemCount: Int, pageSize: Int, maxPages: Int): String? =
        if (itemCount == pageSize && currentPage < maxPages) (currentPage + 1).toString() else null
}

internal object ShareTokenPagingPolicy {
    data class Cursor(val page: Int, val token: String)

    fun decode(cursor: String?): Cursor {
        if (cursor.isNullOrBlank()) return Cursor(1, "")
        val separator = cursor.indexOf(':')
        if (separator <= 0) return Cursor(1, cursor)
        return Cursor(
            page = cursor.substring(0, separator).toIntOrNull()?.coerceAtLeast(1) ?: 1,
            token = cursor.substring(separator + 1)
        )
    }

    fun nextCursor(currentPage: Int, currentToken: String, nextToken: String, maxPages: Int): String? =
        nextToken.takeIf { it.isNotBlank() && it != currentToken && currentPage < maxPages }
            ?.let { "${currentPage + 1}:$it" }
}

internal object ShareRangePagingPolicy {
    fun begin(cursor: String?): Int = cursor?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    fun nextCursor(
        begin: Int,
        pageSize: Int,
        categoryCounts: List<Int>,
        maxBegin: Int
    ): String? {
        val next = begin + pageSize
        return next.takeIf { categoryCounts.any { it >= pageSize } && it <= maxBegin }?.toString()
    }
}
