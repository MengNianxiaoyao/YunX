package com.yunx.app.data.network.adapters

import com.yunx.app.data.network.CloudCapabilities
import com.yunx.app.data.network.CloudFileSource
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.data.network.ShareRequest
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 123 Api → CloudFileSource 适配器（P2-3）。
 * 分页游标为平台原生 next 标记；分享有效期需 ISO8601 时间串（永久=2099 固定值），
 * adapter 内做「天数 → 绝对时间」换算。
 */
class Pan123FileSource(
    private val api: Pan123Api,
    private val tokenProvider: suspend () -> String?
) : CloudFileSource {

    override val capabilities = CloudCapabilities(
        name = "123云盘",
        rootDir = "0"
    )

    private suspend fun token(): String =
        tokenProvider() ?: throw IllegalStateException("请先登录 123 云盘")

    override suspend fun list(dir: String, cursor: String?): Pair<List<ShareFile>, String?> {
        val page = Pan123PagingPolicy.decode(cursor)
        val (files, next) = api.listCloudFiles(dir, token(), page.next, page.number)
        return files to next?.let { Pan123PagingPolicy.encode(page.number + 1, it) }
    }

    override suspend fun downloadLink(file: ShareFile): DownloadLink? =
        api.getDownloadLink(file, token())

    override fun downloadHeaders(credential: String?): Map<String, String> = mapOf(
        "User-Agent" to Pan123Constants.WEB_UA,
        "Referer" to Pan123Constants.DOWNLOAD_REFERER
    )

    override suspend fun rename(file: ShareFile, newName: String): Boolean {
        api.renameFile(file.fid, newName, token())
        return true
    }

    override suspend fun move(files: List<ShareFile>, toDir: String): Boolean {
        api.moveFiles(files.map { it.fid }, toDir, token())
        return true
    }

    override suspend fun delete(files: List<ShareFile>): Boolean {
        api.deleteFiles(files, token())
        return true
    }

    override suspend fun createShare(files: List<ShareFile>, request: ShareRequest): ShareInfo =
        api.createShare(
            fileIds = files.map { it.fid },
            shareName = if (files.size == 1) files[0].fname else "分享 ${files.size} 个文件",
            expiration = expiration(request.expireDays),
            sharePwd = request.passcode.takeIf { it.isNotBlank() },
            token = token()
        ).copy(expiredType = Pan123SharePolicy.expireType(request.expireDays))

    override suspend fun quota() = api.getQuota(token())

    /** 天数 → ISO8601 过期时间（+08:00 手动拼接，SimpleDateFormat "XXX" 在低版本 Android 崩溃） */
    private fun expiration(days: Int?): String {
        if (days == null) return Pan123Constants.EXPIRATION_FOREVER
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val offsetMin = TimeZone.getDefault().getOffset(cal.timeInMillis) / 60000
        val sign = if (offsetMin >= 0) "+" else "-"
        val abs = kotlin.math.abs(offsetMin)
        return sdf.format(Date(cal.timeInMillis)) +
            String.format(Locale.US, "%s%02d:%02d", sign, abs / 60, abs % 60)
    }
}

internal object Pan123PagingPolicy {
    data class Page(val number: Int, val next: String)

    fun encode(number: Int, next: String): String = "$number|$next"

    fun decode(cursor: String?): Page {
        if (cursor == null) return Page(1, "0")
        val separator = cursor.indexOf('|')
        if (separator <= 0) return Page(1, cursor)
        return Page(
            number = cursor.substring(0, separator).toIntOrNull()?.coerceAtLeast(1) ?: 1,
            next = cursor.substring(separator + 1)
        )
    }
}

internal object Pan123SharePolicy {
    fun expireType(days: Int?): Int = when (days) {
        null -> 1
        1 -> 2
        7 -> 3
        else -> 4
    }
}
