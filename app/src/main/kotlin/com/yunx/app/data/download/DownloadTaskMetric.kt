package com.yunx.app.data.download

enum class DownloadMetricOutcome(val code: String) {
    SUCCESS("success"),
    FAILURE("failure"),
    CANCELLED("cancelled")
}

object DownloadTaskMetric {
    fun elapsedMillis(startedAtNanos: Long, nowNanos: Long): Long =
        ((nowNanos - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)

    fun retry(
        taskId: Long,
        platform: String,
        retry: Int,
        maxRetries: Int,
        failureKind: DownloadFailureKind,
        elapsedMillis: Long
    ): String = buildString {
        append("metric=download_retry")
        append(" taskId=").append(taskId)
        append(" platform=").append(safePlatform(platform))
        append(" retry=").append(retry.coerceAtLeast(0))
        append(" maxRetries=").append(maxRetries.coerceAtLeast(0))
        append(" failureKind=").append(failureKind.code)
        append(" elapsedMs=").append(elapsedMillis.coerceAtLeast(0L))
    }

    fun terminal(
        taskId: Long,
        platform: String,
        outcome: DownloadMetricOutcome,
        retries: Int,
        elapsedMillis: Long,
        failureKind: DownloadFailureKind? = null
    ): String = buildString {
        append("metric=download_task")
        append(" taskId=").append(taskId)
        append(" platform=").append(safePlatform(platform))
        append(" outcome=").append(outcome.code)
        append(" retries=").append(retries.coerceAtLeast(0))
        append(" elapsedMs=").append(elapsedMillis.coerceAtLeast(0L))
        failureKind?.let { append(" failureKind=").append(it.code) }
    }

    private fun safePlatform(platform: String): String = when (platform) {
        DownloadPlatform.QUARK,
        DownloadPlatform.UC,
        DownloadPlatform.XUNLEI,
        DownloadPlatform.BAIDU,
        DownloadPlatform.C139,
        DownloadPlatform.PAN123 -> platform
        else -> DownloadPlatform.GENERIC
    }
}
