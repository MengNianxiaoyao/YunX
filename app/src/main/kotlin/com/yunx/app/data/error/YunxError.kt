package com.yunx.app.data.error

import com.yunx.app.data.download.DownloadFailureException
import com.yunx.app.data.download.DownloadFailureKind
import com.yunx.app.data.network.AuthExpiredException
import com.yunx.app.data.network.InvalidPasscodeException
import com.yunx.app.data.network.LinkExpiredException
import com.yunx.app.data.network.PasscodeRequiredException
import com.yunx.app.data.network.ProtocolChangedException
import com.yunx.app.data.network.RateLimitedException
import kotlinx.coroutines.CancellationException
import java.io.IOException

sealed interface YunxError {
    data object AuthExpired : YunxError
    data object PasscodeRequired : YunxError
    data object InvalidPasscode : YunxError
    data object RateLimited : YunxError
    data object NetworkUnavailable : YunxError
    data object LinkExpired : YunxError
    data object RangeUnsupported : YunxError
    data object StorageDenied : YunxError
    data object IntegrityCheckFailed : YunxError
    data class ProtocolChanged(val platform: String) : YunxError
    data class Unknown(val diagnosticMessage: String?) : YunxError
}

object YunxErrorClassifier {
    fun classify(error: Throwable): YunxError {
        if (error is CancellationException) throw error
        return when (error) {
            is AuthExpiredException -> YunxError.AuthExpired
            is PasscodeRequiredException -> YunxError.PasscodeRequired
            is InvalidPasscodeException -> YunxError.InvalidPasscode
            is RateLimitedException -> YunxError.RateLimited
            is LinkExpiredException -> YunxError.LinkExpired
            is ProtocolChangedException -> YunxError.ProtocolChanged(error.platform)
            is IOException -> YunxError.NetworkUnavailable
            is DownloadFailureException -> when (error.failure.kind) {
                DownloadFailureKind.NETWORK -> YunxError.NetworkUnavailable
                DownloadFailureKind.LINK_EXPIRED -> YunxError.LinkExpired
                DownloadFailureKind.STORAGE -> YunxError.StorageDenied
                DownloadFailureKind.INTEGRITY -> YunxError.IntegrityCheckFailed
                DownloadFailureKind.UNSUPPORTED -> YunxError.RangeUnsupported
                DownloadFailureKind.UNKNOWN -> YunxError.Unknown(error.failure.detail)
            }
            else -> YunxError.Unknown(error.message)
        }
    }

    fun userMessage(error: Throwable, fallback: String): String {
        return when (val classified = classify(error)) {
            YunxError.AuthExpired -> "登录已过期，请重新登录"
            YunxError.PasscodeRequired -> "该分享需要提取码"
            YunxError.InvalidPasscode -> "提取码错误，请重新输入"
            YunxError.RateLimited -> "请求过于频繁，请稍后重试"
            YunxError.NetworkUnavailable -> "网络连接失败，请检查网络后重试"
            YunxError.LinkExpired -> "分享或下载链接已失效，请重新获取"
            YunxError.RangeUnsupported -> "当前下载地址不支持断点续传"
            YunxError.StorageDenied -> "无法写入存储，请检查存储权限和可用空间"
            YunxError.IntegrityCheckFailed -> "文件完整性校验失败，请重新下载"
            is YunxError.ProtocolChanged -> "${classified.platform}接口可能已变化，请更新应用或稍后重试"
            is YunxError.Unknown -> fallback
        }
    }
}
