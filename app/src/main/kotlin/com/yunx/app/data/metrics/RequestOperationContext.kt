package com.yunx.app.data.metrics

import android.util.Log
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.SharePlatform
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext as coroutineWithContext
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.UUID

enum class RequestPlatform(val code: String) {
    QUARK("quark"),
    UC("uc"),
    XUNLEI("xunlei"),
    BAIDU("baidu"),
    C139("c139"),
    PAN123("pan123"),
    GENERIC("generic"),
    UNKNOWN("unknown");

    companion object {
        fun from(platform: SharePlatform?): RequestPlatform = when (platform) {
            SharePlatform.QUARK -> QUARK
            SharePlatform.UC -> UC
            SharePlatform.XUNLEI -> XUNLEI
            SharePlatform.BAIDU -> BAIDU
            SharePlatform.C139 -> C139
            SharePlatform.PAN123 -> PAN123
            null -> UNKNOWN
        }

        fun from(platform: String): RequestPlatform = when (platform) {
            DownloadPlatform.QUARK -> QUARK
            DownloadPlatform.UC -> UC
            DownloadPlatform.XUNLEI -> XUNLEI
            DownloadPlatform.BAIDU -> BAIDU
            DownloadPlatform.C139 -> C139
            DownloadPlatform.PAN123 -> PAN123
            else -> GENERIC
        }
    }
}

enum class RequestStage(val code: String) {
    RESOLVE_SESSION("resolve_session"),
    RESOLVE_ROOT_LIST("resolve_root_list"),
    DIRECTORY_LIST("directory_list"),
    DIRECTORY_LOAD_MORE("directory_load_more"),
    DIRECT_LINK("direct_link"),
    DOWNLOAD("download")
}

enum class RequestLogErrorKind(val code: String) {
    NONE("none"),
    HTTP_ERROR("http_error"),
    NETWORK_UNAVAILABLE("network_unavailable")
}

data class RequestOperationContext(
    val operationId: String,
    val platform: RequestPlatform,
    val stage: RequestStage,
    val retry: Int = 0,
    val logSuccessfulRequests: Boolean = true
)

object OperationId {
    private val validPattern = Regex("^(resolve|download)-[a-f0-9]{32}$")

    fun resolve(): String = create("resolve")
    fun download(): String = create("download")
    fun isValid(value: String): Boolean = validPattern.matches(value)

    internal fun create(prefix: String, uuid: UUID = UUID.randomUUID()): String =
        "$prefix-${uuid.toString().replace("-", "")}".takeIf(::isValid)
            ?: error("Invalid operation ID prefix")
}

object RequestOperationContextHolder {
    private val local = ThreadLocal<RequestOperationContext?>()

    fun current(): RequestOperationContext? = local.get()

    suspend fun <T> withContext(context: RequestOperationContext, block: suspend () -> T): T =
        coroutineWithContext(local.asContextElement(context)) { block() }
}

object RequestContextLog {
    fun line(
        context: RequestOperationContext,
        httpStatus: Int?,
        elapsedMillis: Long,
        errorKind: RequestLogErrorKind = RequestLogErrorKind.NONE
    ): String = buildString {
        append("event=request_context")
        append(" operationId=").append(context.operationId.takeIf(OperationId::isValid) ?: "invalid")
        append(" platform=").append(context.platform.code)
        append(" stage=").append(context.stage.code)
        append(" httpStatus=").append(httpStatus?.takeIf { it in 100..599 } ?: "none")
        append(" retry=").append(context.retry.coerceAtLeast(0))
        append(" elapsedMs=").append(elapsedMillis.coerceAtLeast(0L))
        append(" errorKind=").append(errorKind.code)
    }
}

class RequestContextInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val context = RequestOperationContextHolder.current() ?: return chain.proceed(chain.request())
        val startedAt = System.nanoTime()
        return try {
            val response = chain.proceed(chain.request())
            if (context.logSuccessfulRequests.not() && response.isSuccessful) return response
            Log.i(
                TAG,
                RequestContextLog.line(
                    context = context,
                    httpStatus = response.code,
                    elapsedMillis = elapsedMillis(startedAt),
                    errorKind = if (response.isSuccessful) {
                        RequestLogErrorKind.NONE
                    } else {
                        RequestLogErrorKind.HTTP_ERROR
                    }
                )
            )
            response
        } catch (error: IOException) {
            Log.i(
                TAG,
                RequestContextLog.line(
                    context = context,
                    httpStatus = null,
                    elapsedMillis = elapsedMillis(startedAt),
                    errorKind = RequestLogErrorKind.NETWORK_UNAVAILABLE
                )
            )
            throw error
        }
    }

    private fun elapsedMillis(startedAt: Long): Long =
        ((System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000L)

    private companion object {
        const val TAG = "RequestContext"
    }
}
