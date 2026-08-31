package com.yunx.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.yunx.app.data.network.model.ShareInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 夸克/UC 共享 Cookie 工具（P2-5 合并：两家的 QuarkCookieUtil/UCCookieUtil 逐字相同）。
 * 对齐 AList pkg/cookie：__puus 约 3 小时过期，刷新或跨接口续期时合并回写。
 */
object AliCookieUtil {
    private val TRACKED = setOf("__puus", "__pus")

    /** 从响应 Set-Cookie 列表中提取 __puus/__pus 合并回原 Cookie 串。 */
    fun mergeFromSetCookies(original: String, setCookies: List<String>): String {
        var cookie = original
        for (sc in setCookies) {
            val kv = sc.substringBefore(';').trim()
            val eq = kv.indexOf('=')
            if (eq <= 0) continue
            val name = kv.substring(0, eq)
            if (name in TRACKED) cookie = setOrReplace(cookie, name, kv.substring(eq + 1))
        }
        return cookie
    }

    /** 去除 __puus（用于换绑登录态的新鲜下载，AList refreshPuus 同款）。 */
    fun withoutPuus(cookie: String): String =
        cookie.split(";").map { it.trim() }
            .filter { !it.startsWith("__puus=") }
            .joinToString("; ")

    private fun setOrReplace(cookie: String, name: String, value: String): String {
        val parts = cookie.split(";").map { it.trim() }.toMutableList()
        val idx = parts.indexOfFirst { it.startsWith("$name=") }
        val kv = "$name=$value"
        if (idx >= 0) parts[idx] = kv else parts.add(kv)
        return parts.joinToString("; ")
    }
}

/**
 * 夸克/UC 云盘 API 公共基类（P2-5）：两家 API 同源（AList quark_uc 协议），
 * 请求构造/解析/续期/轮询骨架完全一致，仅常量（URL/UA）不同——经抽象属性注入。
 *
 * 收敛范围 = 逐字相同的方法：get/postJson/parseData/mergeCookieFromResponse/createFolder/
 * pollTask/pollShareTask/renameFile/moveFile/getShareInfo/refreshSession。
 * getQuota 两家头不同（UC 多 Origin/Referer/CLOUD_UA），由子类各自实现。
 *
 * 异常统一为 [AliDriveApiException]（P2-5 顺带修复 UCApi 抛 QuarkApiException 的跨平台类型泄漏）；
 * QuarkApiException 继承之，夸克侧既有 catch 全部兼容。
 */
abstract class AliCookieDriveApi(
    private val clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    /** 每次请求获取全局 API 客户端。 */
    protected val client get() = clientProvider()

    /**
     * Cookie 回写钩子（账号续期，由 XxxAccountRepository 注入并落库）：
     * 每次响应的 Set-Cookie 合并进 Cookie 后回调，确保 __puus/__pus 始终新鲜。
     */
    var cookieSink: ((String) -> Unit)? = null

    protected val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ---------- 平台差异注入点 ----------

    /** 任务查询 URL（含平台 pr/fr 参数的完整地址） */
    protected abstract val taskUrl: String

    /** 文件夹创建 POST 地址（file） */
    protected abstract val fileUrl: String

    /** 重命名 POST 地址 */
    protected abstract val renameUrl: String

    /** 移动 POST 地址 */
    protected abstract val moveUrl: String

    /** 会话刷新地址（config；__puus 续期） */
    protected abstract val configUrl: String

    /** 分享信息查询地址（POST body={share_id}） */
    protected abstract val shareInfoUrl: String

    /** API User-Agent（get/postJson 统一携带） */
    protected abstract val apiUserAgent: String

    /** 会话刷新/直链请求的 Referer（夸克 DOWNLOAD_REFERER / UC DOWNLOAD_REFERER） */
    protected abstract val referer: String

    /** 业务异常构造（夸克子类返回 QuarkApiException 以兼容既有 catch） */
    protected open fun apiError(message: String, code: Int = 0): Exception =
        AliDriveApiException(message, code)

    // ---------- 请求构造与解析（逐字相同） ----------

    protected fun get(url: String, cookie: String): Request =
        Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", apiUserAgent)
            .get()
            .build()

    protected fun postJson(url: String, cookie: String, body: String): Request =
        Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", apiUserAgent)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

    /**
     * 统一响应解析：status==200 校验 + data 解包 + Cookie 回写。
     * 透传服务端 message（如「取件码错误」「会话已失效」），供 UI 直接提示。
     */
    protected fun <T> parseData(request: Request, parser: (JSONObject) -> T): T {
        val response = client.newCall(request).execute()
        val body = response.use {
            PlatformHttpErrors.throwIfRateLimited(it.code)
            mergeCookieFromResponse(request, it)
            it.body?.string() ?: throw apiError("请求失败（响应为空）")
        }
        val json = runCatching { JSONObject(body) }.getOrElse {
            throw apiError("响应解析失败")
        }
        if (json.optInt("status") != 200) {
            throw apiError(json.optString("message").ifBlank { "请求失败" }, json.optInt("code"))
        }
        return parser(json.optJSONObject("data") ?: throw apiError("响应缺少 data"))
    }

    /** 每次响应的 Set-Cookie 合并回请求 Cookie 并回调 cookieSink（续期落库）。 */
    protected fun mergeCookieFromResponse(request: Request, response: okhttp3.Response) {
        val original = request.header("Cookie") ?: return
        val setCookies = response.headers("Set-Cookie")
        if (setCookies.isEmpty()) return
        val merged = AliCookieUtil.mergeFromSetCookies(original, setCookies)
        if (merged != original) cookieSink?.invoke(merged)
    }

    // ---------- 公共业务方法（逐字相同，仅 URL 常量不同） ----------

    /** 创建文件夹，返回新目录 fid。 */
    suspend fun createFolder(name: String, parentFid: String, cookie: String): String? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("pdir_fid", parentFid)
                .put("file_name", name)
                .put("dir_path", "")
                .put("dir_init_lock", false)
                .toString()
            val request = postJson(fileUrl, cookie, body)
            parseData(request) { data -> data.optString("fid") }
        }

    /** 转存任务轮询：返回转存后的首个 fid（完成判定：finished_at>0 或 status/task_status==2）。 */
    suspend fun pollTask(taskId: String, cookie: String): String? = withContext(Dispatchers.IO) {
        val url = "$taskUrl&task_id=${URLEncoder.encode(taskId, "UTF-8")}&retry_index=0"
        for (i in 0 until 10) {
            val savedFid = runCatching {
                client.newCall(get(url, cookie)).execute().use { response ->
                    val json = JSONObject(response.body?.string() ?: "{}")
                    if (json.optInt("status") != 200) return@use null
                    val data = json.optJSONObject("data") ?: return@use null
                    // 完成：finished_at > 0 或 status/task_status == 2
                    val finished = data.optLong("finished_at") > 0 ||
                        data.optInt("status") == 2 ||
                        data.optInt("task_status") == 2
                    if (!finished) return@use null
                    data.optJSONObject("save_as")
                        ?.optJSONArray("save_as_top_fids")
                        ?.optString(0)
                        ?.takeIf { it.isNotBlank() }
                }
            }.getOrNull()
            if (savedFid != null) return@withContext savedFid
            delay(1000)
        }
        null
    }

    /** 重命名（fid + file_name 单对象 body）。 */
    suspend fun renameFile(fid: String, newName: String, cookie: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("fid", fid)
                .put("file_name", newName)
                .toString()
            val request = postJson(renameUrl, cookie, body)
            runCatching {
                client.newCall(request).execute().use { response ->
                    JSONObject(response.body?.string() ?: "{}").optInt("status") == 200
                }
            }.getOrDefault(false)
        }

    /** 移动（action_type=1 + to_pdir_fid + filelist，返回任务 id）。 */
    suspend fun moveFile(fid: String, toPdirFid: String, cookie: String): String? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("action_type", 1)
                .put("to_pdir_fid", toPdirFid)
                .put("filelist", JSONArray().put(fid))
                .put("exclude_fids", JSONArray())
                .toString()
            val request = postJson(moveUrl, cookie, body)
            parseData(request) { data -> data.optString("task_id").takeIf { it.isNotBlank() } }
        }

    /** __puus 续期：用去除 __puus 的 Cookie 换取新鲜会话（AList refreshPuus 同款）。 */
    suspend fun refreshSession(cookie: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(configUrl)
            .header("Cookie", AliCookieUtil.withoutPuus(cookie))
            .header("User-Agent", apiUserAgent)
            .header("Referer", referer)
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                val merged = AliCookieUtil.mergeFromSetCookies(cookie, resp.headers("Set-Cookie"))
                if (merged != cookie) cookieSink?.invoke(merged)
                merged
            }
        }.getOrNull()
    }

    /** 分享任务轮询（完成判定 finished_at>0 或 status==2；返回 share_id）。 */
    protected suspend fun pollShareTask(taskId: String, cookie: String): String? =
        withContext(Dispatchers.IO) {
            val url = "$taskUrl&task_id=${URLEncoder.encode(taskId, "UTF-8")}&retry_index=0"
            for (i in 0 until 15) {
                val shareId = runCatching {
                    client.newCall(get(url, cookie)).execute().use { resp ->
                        val json = JSONObject(resp.body?.string() ?: "{}")
                        if (json.optInt("status") != 200) return@use null
                        val data = json.optJSONObject("data") ?: return@use null
                        val finished = data.optLong("finished_at") > 0 || data.optInt("status") == 2
                        if (!finished) return@use null
                        data.optString("share_id").takeIf { it.isNotBlank() }
                    }
                }.getOrNull()
                if (shareId != null) return@withContext shareId
                delay(1000)
            }
            null
        }

    /** 查询分享信息（POST body={share_id}：链接/提取码/标题/有效期档位）。 */
    suspend fun getShareInfo(shareId: String, cookie: String): ShareInfo? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("share_id", shareId).toString()
        val request = postJson(shareInfoUrl, cookie, body)
        parseData(request) { data ->
            ShareInfo(
                shareUrl = data.optString("share_url"),
                passcode = data.optString("passcode"),
                pwdId = data.optString("pwd_id"),
                title = data.optString("title"),
                expiredType = data.optInt("expired_type")
            )
        }
    }
}

/** 夸克/UC 共享业务异常（P2-5；修复 UCApi 抛 QuarkApiException 的跨平台类型泄漏） */
open class AliDriveApiException(message: String, val code: Int = 0) : Exception(message)
