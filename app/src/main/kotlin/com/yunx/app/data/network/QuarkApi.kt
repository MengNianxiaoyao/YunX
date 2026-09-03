package com.yunx.app.data.network

import com.yunx.app.data.network.model.CloudCredential
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 夸克 Cookie 工具已并入 [AliCookieUtil]（P2-5：与 UC 同源协议共用）。
 */

/**
 * 夸克 API 封装（OkHttp：轮询鉴权 + Cookie 续期 + 分享直链提取）。
 * P2-5：公共骨架（请求构造/parseData/轮询/重命名/移动/续期/分享信息查询）继承 [AliCookieDriveApi]。
 */
class QuarkApi(
    clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) : AliCookieDriveApi(clientProvider) {

    // ---------- 平台注入点 ----------

    override val taskUrl: String get() = QuarkConstants.TASK_URL
    override val fileUrl: String get() = QuarkConstants.FILE_URL
    override val renameUrl: String get() = QuarkConstants.RENAME_URL
    override val moveUrl: String get() = QuarkConstants.MOVE_URL
    override val configUrl: String get() = QuarkConstants.CONFIG_URL
    override val shareInfoUrl: String get() = QuarkConstants.SHARE_INFO_URL
    override val apiUserAgent: String get() = QuarkConstants.API_USER_AGENT
    override val referer: String get() = QuarkConstants.DOWNLOAD_REFERER

    /** 夸克侧异常保持 QuarkApiException（既有 catch 兼容） */
    override fun apiError(message: String, code: Int): Exception =
        QuarkApiException(message, code)

    // ---------- 账号 ----------

    suspend fun fetchNickname(cookie: CloudCredential.Cookie): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(QuarkConstants.ACCOUNT_INFO_URL)
            .header("Cookie", cookie.value)
            .header("User-Agent", QuarkConstants.API_USER_AGENT)
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                // 该接口无 status 字段，成功标志为 success:true / code:"OK"
                if (json.optBoolean("success", false)) {
                    json.optJSONObject("data")
                        ?.optString("nickname")
                        ?.takeIf { it.isNotBlank() }
                } else null
            }
        }.getOrNull()
    }

    // ---------- 分享解析 ----------

    /** 4.1 获取分享 Token（请求体携带 pwd_id/passcode） */
    suspend fun getShareToken(shareId: String, pwd: String?, cookie: CloudCredential.Cookie): ShareToken? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("pwd_id", shareId)
            .put("passcode", pwd ?: "")
            .put("support_visit_limit_private_share", true)
            .toString()
        val request = postJson(QuarkConstants.SHARE_TOKEN_URL, cookie, body)
        parseData(request) { data ->
            ShareToken(
                stoken = data.optString("stoken"),
                title = data.optString("title"),
                firstFid = data.optString("first_fid")
            )
        }
    }

    /** 4.3 验证分享提取码 */
    suspend fun verifySharePassword(shareId: String, passcode: String, cookie: CloudCredential.Cookie): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("share_id", shareId)
                .put("passcode", passcode)
                .toString()
            val request = postJson(QuarkConstants.SHARE_PASSWORD_URL, cookie, body)
            runCatching {
                client.newCall(request).execute().use { response ->
                    val json = JSONObject(response.body?.string() ?: "{}")
                    json.optInt("status") == 200
                }
            }.getOrDefault(false)
        }

    /** 4.2 获取分享文件列表（sharepage/detail）
 *  官方字段：file_name / size / dir(boolean) / share_fid_token，
 *  与 kkdo.md 文档中的 fname/fsize/isdir/fid_token 不同，以抓包为准。
 */
    suspend fun getShareFiles(
        shareId: String,
        stoken: String,
        pdirFid: String,
        cookie: CloudCredential.Cookie,
        page: Int = 1,
        size: Int = 100
    ): List<ShareFile>? = withContext(Dispatchers.IO) {
        // 参数名必须为 pwd_id（值=分享链接短码），并追加 ver=2 / _page / _size 等固定参数
        val url = buildString {
            append(QuarkConstants.SHARE_DETAIL_URL)
            append("&pwd_id=").append(shareId)
            append("&stoken=").append(URLEncoder.encode(stoken, "UTF-8"))
            append("&pdir_fid=").append(pdirFid)
            append("&ver=2")
            append("&force=0")
            append("&_page=").append(page)
            append("&_size=").append(size)
            append("&_fetch_banner=0")
            append("&_fetch_share=0")
            append("&fetch_relate_conversation=0")
            append("&_fetch_total=1")
            append("&_sort=file_type:asc,file_name:asc")
        }
        // 该接口需携带 Origin / Referer，否则可能返回 400
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie.value)
            .header("User-Agent", QuarkConstants.API_USER_AGENT)
            .header("Origin", "https://pan.quark.cn")
            .header("Referer", "https://pan.quark.cn/")
            .get()
            .build()
        parseData(request) { data ->
            val array = data.optJSONArray("list") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        ShareFile(
                            fid = item.optString("fid"),
                            fname = item.optString("file_name"),
                            fsize = item.optLong("size"),
                            isdir = item.optBoolean("dir", false),
                            pdirFid = item.optString("pdir_fid"),
                            fidToken = item.optString("share_fid_token"),
                            modifyTime = item.optString("updated_at")
                        )
                    )
                }
            }
        }
    }

    // ---------- 个人网盘 / 转存 ----------

    /** 7.1 个人网盘文件列表（用于查找/确认临时目录）
     *  注意：个人网盘列表字段为 file_name / size / dir(boolean)，
     *  与分享列表的 fname / fsize / isdir(int) 不同，需做兼容映射。
     */
    suspend fun getFileList(
        pdirFid: String,
        cookie: CloudCredential.Cookie,
        page: Int = 1,
        size: Int = 100
    ): List<ShareFile>? = withContext(Dispatchers.IO) {
    val url = "${QuarkConstants.FILE_URL}&pdir_fid=$pdirFid&page=$page&size=$size"
    val request = get(url, cookie)
    parseData(request) { data ->
        val array = data.optJSONArray("list") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    ShareFile(
                        fid = item.optString("fid"),
                        fname = item.optString("file_name").ifEmpty { item.optString("fname") },
                        fsize = if (item.has("size")) item.optLong("size") else item.optLong("fsize"),
                        isdir = item.optBoolean("dir", false) || item.optInt("isdir") == 1,
                        pdirFid = item.optString("pdir_fid"),
                        fidToken = item.optString("fid_token"),
                        modifyTime = item.optString("modify_time")
                    )
                )
            }
        }
    }
}

    /** 云盘文件列表（网盘页浏览；抓包 /1/clouddrive/file/sort，pdir_fid=0 根目录）
     *  响应 data.list[]，字段：fid / file_name / size / dir(boolean) / pdir_fid / updated_at。
     */
    suspend fun listCloudFiles(
        pdirFid: String,
        cookie: CloudCredential.Cookie,
        page: Int = 1,
        size: Int = 50
    ): List<ShareFile>? = withContext(Dispatchers.IO) {
        val url = buildString {
            append(QuarkConstants.CLOUD_FILE_SORT_URL)
            append("&uc_param_str=")
            append("&pdir_fid=").append(pdirFid)
            append("&_page=").append(page)
            append("&_size=").append(size)
            append("&_fetch_total=1")
            append("&_fetch_sub_dirs=0")
            append("&_sort=file_type:asc,updated_at:desc")
            append("&fetch_all_file=1")
            append("&fetch_risk_file_name=1")
        }
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie.value)
            .header("User-Agent", QuarkConstants.API_USER_AGENT)
            .header("Origin", "https://pan.quark.cn")
            .header("Referer", "https://pan.quark.cn/")
            .get()
            .build()
        parseData(request) { data ->
            val array = data.optJSONArray("list") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        ShareFile(
                            fid = item.optString("fid"),
                            fname = item.optString("file_name").ifEmpty { item.optString("fname") },
                            fsize = item.optLong("size"),
                            isdir = item.optBoolean("dir", false),
                            pdirFid = item.optString("pdir_fid"),
                            fidToken = "",
                            modifyTime = item.optString("updated_at")
                        )
                    )
                }
            }
        }
    }

    suspend fun listCloudFilesPage(pdirFid: String, cookie: CloudCredential.Cookie, page: Int): Pair<List<ShareFile>, Boolean> =
        listCloudFiles(pdirFid, cookie, page).orEmpty().let { it to (it.size >= 50) }

    // createFolder 由 AliCookieDriveApi 提供（P2-5：逐字相同的公共实现）

    /** 5. 转存分享文件到个人网盘目录，返回异步任务 id（可能为空）
     *  注意：pwd_id 必须为分享链接短码（非空），并携带 pdir_fid/scene，
     *  否则接口返回 400 Bad Parameter: [pwd_id为空]。
     */
    suspend fun saveShareFile(
        shareId: String,
        stoken: String,
        pdirFid: String,
        fid: String,
        fidToken: String,
        toPdirFid: String,
        cookie: CloudCredential.Cookie
    ): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("pwd_id", shareId)
            .put("stoken", stoken)
            .put("pdir_fid", pdirFid)
            .put("to_pdir_fid", toPdirFid)
            .put("fid_list", JSONArray().put(fid))
            .put("fid_token_list", JSONArray().put(fidToken))
            .put("scene", "link")
            .toString()
        val request = postJson(QuarkConstants.SAVE_URL, cookie, body)
        parseData(request) { data -> data.optString("task_id").takeIf { it.isNotBlank() } }
    }

    // pollTask 由 AliCookieDriveApi 提供（P2-5：逐字相同的公共实现）

    // ---------- 网盘空间详情 ----------

    /** 网盘空间详情（/1/clouddrive/member：total_capacity / use_capacity） */
    suspend fun getQuota(cookie: CloudCredential.Cookie): QuotaInfo? = withContext(Dispatchers.IO) {
        val url = "https://drive-pc.quark.cn/1/clouddrive/member?pr=ucpro&fr=pc&fetch_subscribe=true&_ch=home"
        runCatching {
            val response = client.newCall(get(url, cookie)).execute()
            val body = response.use { it.body?.string() ?: return@runCatching null }
            val data = JSONObject(body).optJSONObject("data") ?: return@runCatching null
            QuotaInfo(
                used = data.optLong("use_capacity"),
                total = data.optLong("total_capacity")
            )
        }.getOrNull()
    }

    // ---------- 下载直链 ----------

    // refreshSession 由 AliCookieDriveApi 提供（P2-5：逐字相同的公共实现，
    // AList quark_uc refreshPuus 同款：剥离 __puus 请求 /config 换取新鲜会话）

    /** 6.1 获取下载直链 */
    suspend fun getDownloadLink(fid: String, cookie: CloudCredential.Cookie): DownloadLink? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("fids", JSONArray().put(fid)).toString()
        val request = postJson(QuarkConstants.DOWNLOAD_URL, cookie, body)
        val response = client.newCall(request).execute()
        val bodyStr = response.use {
            PlatformHttpErrors.throwIfRateLimited(it.code)
            mergeCookieFromResponse(request, it)
            it.body?.string() ?: throw QuarkApiException("获取下载链接失败：响应为空")
        }
        val json = runCatching { JSONObject(bodyStr) }.getOrElse {
            throw QuarkApiException("响应解析失败")
        }
        if (json.optInt("status") != 200 && json.optInt("code") != 0) {
            // 失败响应无 status 字段（默认0），用 code 识别（如 21001 file not found）
            throw QuarkApiException(
                json.optString("message").ifBlank { "获取下载链接失败" },
                json.optInt("code")
            )
        }
        val array = json.optJSONArray("data") ?: throw ProtocolChangedException("夸克网盘")
        if (array.length() == 0) throw QuarkApiException("未返回下载链接")
        val item = array.optJSONObject(0) ?: throw QuarkApiException("未返回下载链接")
        DownloadLink(
            fid = item.optString("fid"),
            filename = item.optString("file_name").ifEmpty { item.optString("filename") },
            downloadUrl = item.optString("download_url"),
            size = item.optLong("size")
        )
    }

    /** 6.2 删除文件（取链成功后清理临时转存；对齐抓包：action_type=2 + filelist + exclude_fids）
     *  返回异步 task_id（删除为异步任务，无需轮询；失败返回 null）。
     */
    suspend fun deleteFile(fid: String, cookie: CloudCredential.Cookie): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("action_type", 2)
            .put("filelist", JSONArray().put(fid))
            .put("exclude_fids", JSONArray())
            .toString()
        val request = postJson(QuarkConstants.DELETE_URL, cookie, body)
        parseData(request) { data -> data.optString("task_id").takeIf { it.isNotBlank() } }
    }

    // ---------- 云盘文件管理 ----------

    // renameFile / moveFile 由 AliCookieDriveApi 提供（P2-5：逐字相同的公共实现）

    /**
     * 创建分享（云盘功能抓包：POST /1/clouddrive/share）。
     * 注意：分享创建是**异步任务**——响应只有 data.task_id，必须轮询 /1/clouddrive/task 直到完成拿到 share_id。
     * @param urlType 1=链接无提取码 2=链接+提取码
     * @param expiredType 1=永久 2=一天 3=七天 4=三十天
     * @return 分享 share_id
     */
    suspend fun createShare(
        fidList: List<String>,
        title: String,
        urlType: Int,
        passcode: String,
        expiredType: Int,
        cookie: CloudCredential.Cookie
    ): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("fid_list", JSONArray().apply { fidList.forEach { put(it) } })
            .put("title", title.ifBlank { "分享文件" })
            .put("url_type", urlType)
            .apply { if (passcode.isNotBlank()) put("passcode", passcode) }
            .put("expired_type", expiredType)
            .put("support_error_code", JSONArray().put("41060"))
            .toString()
        val request = postJson(QuarkConstants.SHARE_CREATE_URL, cookie, body)
        // 1) 创建分享 → task_id（异步，须轮询等待完成）
        val taskId = parseData(request) { data ->
            data.optString("task_id").takeIf { it.isNotBlank() }
        } ?: return@withContext null
        // 2) 轮询 task 直到完成，取 share_id（官方响应 status=2 + share_id）
        pollShareTask(taskId, cookie)
    }

    // pollShareTask / getShareInfo / get / postJson / parseData / mergeCookieFromResponse
    // 由 AliCookieDriveApi 提供（P2-5：逐字相同的公共实现）
}
