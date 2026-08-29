package com.yunx.app.data.download

/** 下载失败的用户可见分类，避免所有异常都进入相同的自动重试路径。 */
enum class DownloadFailureKind(val code: String, val retryable: Boolean) {
    NETWORK("network", true),
    LINK_EXPIRED("link_expired", false),
    STORAGE("storage", false),
    INTEGRITY("integrity", false),
    UNSUPPORTED("unsupported", false),
    UNKNOWN("unknown", false)
}

data class DownloadFailure(val kind: DownloadFailureKind, val detail: String) {
    val message: String
        get() = when (kind) {
            DownloadFailureKind.NETWORK -> "网络下载失败：${detail.ifBlank { "请检查网络后重试" }}"
            DownloadFailureKind.LINK_EXPIRED -> "下载链接已失效，请重新获取下载链接"
            DownloadFailureKind.STORAGE -> "文件保存失败：${detail.ifBlank { "请检查存储权限或保存目录" }}"
            DownloadFailureKind.INTEGRITY -> "文件完整性校验失败，请重新下载"
            DownloadFailureKind.UNSUPPORTED -> detail.ifBlank { "当前下载格式暂不支持" }
            DownloadFailureKind.UNKNOWN -> detail.ifBlank { "下载失败，请稍后重试" }
        }
}

object DownloadFailureClassifier {
    fun classify(error: Throwable): DownloadFailure {
        if (error is DownloadFailureException) return error.failure
        val detail = error.message.orEmpty()
        val text = detail.lowercase()
        val kind = when {
            "存储权限" in detail || "保存到下载目录" in detail || "保存失败" in detail ->
                DownloadFailureKind.STORAGE
            "完整性" in detail || "文件大小校验" in detail || "分片文件缺失" in detail ||
                "合并分片" in detail -> DownloadFailureKind.INTEGRITY
            "html" in text || "链接已失效" in detail || "防盗链" in detail ||
                "http 401" in text || "http 403" in text || "http 404" in text ->
                DownloadFailureKind.LINK_EXPIRED
            "暂不支持" in detail || "超过大小限制" in detail || "超过限制" in detail ->
                DownloadFailureKind.UNSUPPORTED
            error is java.io.IOException || "网络" in detail || "timeout" in text ||
                "超时" in detail || "下载失败" in detail -> DownloadFailureKind.NETWORK
            else -> DownloadFailureKind.UNKNOWN
        }
        return DownloadFailure(kind, detail)
    }
}

class DownloadFailureException(val failure: DownloadFailure, cause: Throwable? = null) :
    IllegalStateException(failure.message, cause)
