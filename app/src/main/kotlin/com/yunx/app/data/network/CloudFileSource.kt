package com.yunx.app.data.network

import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo

/**
 * 云盘文件源统一接口（P2-3）：6 个平台 Api 的 adapter 目标形态。
 * 接口方法以「引用方需要的语义」定义，各平台签名差异（Cookie/token/creds 三元组、
 * fid/path/fileId、单文件/批量）由 adapter 吸收——P2-4 的 BaseCloudViewModel
 * 只面向本接口与 [CloudCapabilities] 编程。
 *
 * 凭证不作为方法参数（各平台凭证形态不同且需 Provider 动态获取），
 * 由 adapter 构造时闭包持有 provider。
 *
 * @param D 目录标识类型擦除为 String（夸克/UC/迅雷 fid、百度路径、139/123 id）
 */
interface CloudFileSource {
    /** 平台能力描述（UI 数据化差异，见 [CloudCapabilities]） */
    val capabilities: CloudCapabilities

    /** 分页列目录：返回 (本页文件, 下页游标)；cursor=null 表示首页 */
    suspend fun list(dir: String, cursor: String?): Pair<List<ShareFile>, String?>

    /** 取单文件下载直链（含文件名/大小；UC 视频的 HLS 特殊取链由 adapter 内部处理） */
    suspend fun downloadLink(file: ShareFile): DownloadLink?

    /**
     * 下载直链所需请求头。夸克/UC/百度的直链与登录态绑定，调用方需传入
     * 当时的凭证（cookie/token）；无凭证依赖平台（139/123/迅雷）忽略之。
     */
    fun downloadHeaders(credential: String?): Map<String, String>

    /** 重命名；成功返回 true */
    suspend fun rename(file: ShareFile, newName: String): Boolean

    /** 移动（单/多文件）；成功返回 true */
    suspend fun move(files: List<ShareFile>, toDir: String): Boolean

    /** 删除（单/多文件）；成功返回 true（139 异步任务由 adapter 内部轮询） */
    suspend fun delete(files: List<ShareFile>): Boolean

    /** 创建分享；各平台有效期/提取码语义经 [ShareRequest] 归一 */
    suspend fun createShare(files: List<ShareFile>, request: ShareRequest): ShareInfo

    /** 空间配额；不可得返回 null */
    suspend fun quota(): QuotaInfo?
}

/**
 * 创建分享统一请求（P2-3）：
 * 各平台有效期档位不同（夸克/UC/迅雷 1-4 枚举、百度 0/1/7/30 天、139 null/1/7/30、123 null/1/7/30 + ISO8601），
 * 归一为「天数；null=永久」；提取码可空（无码平台忽略）。
 * adapter 负责把归一值映射回平台原生参数（枚举/天数/ISO8601 时间串）。
 */
data class ShareRequest(
    /** 有效期天数；null = 永久 */
    val expireDays: Int?,
    /** 提取码；空串 = 无码 */
    val passcode: String = ""
)

/**
 * 平台能力数据类（P2-3）：真实平台差异的数据化描述，UI 据此渲染而非分支。
 *
 * @param name 平台名（标题/文案用）
 * @param rootDir 根目录标识（夸克/UC/123 "0"、迅雷 ""、百度/139 "/"）
 * @param shareRequiresPasscode 是否强制提取码（百度固定 4 位）
 * @param sharePasscodeLength 强制提取码长度（百度 4）；不强制为 null
 * @param shareSupportsPasscode 是否支持自定义提取码（139/迅雷系统生成，不支持）
 * @param folderDownloadNeedsFullList 移动端目录下载是否需完整展开
 *   （各平台 collectFolderFiles 行为一致，此字段为 P2-4 预留，当前恒 true）
 */
data class CloudCapabilities(
    val name: String,
    val rootDir: String,
    val shareRequiresPasscode: Boolean = false,
    val sharePasscodeLength: Int? = null,
    val shareSupportsPasscode: Boolean = true
)
