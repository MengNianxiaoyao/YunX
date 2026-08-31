package com.yunx.app.data.metrics

import com.yunx.app.data.error.YunxError
import com.yunx.app.data.error.YunxErrorClassifier
import com.yunx.app.data.network.SharePlatform

enum class ResolveMetricOperation(val code: String) {
    INITIAL_RESOLVE("initial_resolve"),
    DIRECTORY_LIST("directory_list"),
    DIRECTORY_LOAD_MORE("directory_load_more"),
    DIRECT_LINK("direct_link")
}

enum class ResolveMetricOutcome(val code: String) {
    SUCCESS("success"),
    FAILURE("failure"),
    CANCELLED("cancelled")
}

enum class ResolveMetricErrorKind(val code: String) {
    INVALID_INPUT("invalid_input"),
    AUTH_EXPIRED("auth_expired"),
    PASSCODE_REQUIRED("passcode_required"),
    INVALID_PASSCODE("invalid_passcode"),
    RATE_LIMITED("rate_limited"),
    NETWORK_UNAVAILABLE("network_unavailable"),
    LINK_EXPIRED("link_expired"),
    RANGE_UNSUPPORTED("range_unsupported"),
    STORAGE_DENIED("storage_denied"),
    INTEGRITY_CHECK_FAILED("integrity_check_failed"),
    PROTOCOL_CHANGED("protocol_changed"),
    UNKNOWN("unknown")
}

object ResolveOperationMetric {
    fun elapsedMillis(startedAtNanos: Long, nowNanos: Long): Long =
        ((nowNanos - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)

    fun errorKind(error: Throwable): ResolveMetricErrorKind = when (YunxErrorClassifier.classify(error)) {
        YunxError.AuthExpired -> ResolveMetricErrorKind.AUTH_EXPIRED
        YunxError.PasscodeRequired -> ResolveMetricErrorKind.PASSCODE_REQUIRED
        YunxError.InvalidPasscode -> ResolveMetricErrorKind.INVALID_PASSCODE
        YunxError.RateLimited -> ResolveMetricErrorKind.RATE_LIMITED
        YunxError.NetworkUnavailable -> ResolveMetricErrorKind.NETWORK_UNAVAILABLE
        YunxError.LinkExpired -> ResolveMetricErrorKind.LINK_EXPIRED
        YunxError.RangeUnsupported -> ResolveMetricErrorKind.RANGE_UNSUPPORTED
        YunxError.StorageDenied -> ResolveMetricErrorKind.STORAGE_DENIED
        YunxError.IntegrityCheckFailed -> ResolveMetricErrorKind.INTEGRITY_CHECK_FAILED
        is YunxError.ProtocolChanged -> ResolveMetricErrorKind.PROTOCOL_CHANGED
        is YunxError.Unknown -> ResolveMetricErrorKind.UNKNOWN
    }

    fun line(
        operationId: String,
        platform: SharePlatform?,
        operation: ResolveMetricOperation,
        outcome: ResolveMetricOutcome,
        elapsedMillis: Long,
        errorKind: ResolveMetricErrorKind? = null
    ): String = buildString {
        append("metric=resolve_operation")
        append(" operationId=").append(operationId.takeIf(OperationId::isValid) ?: "invalid")
        append(" platform=").append(platformCode(platform))
        append(" operation=").append(operation.code)
        append(" outcome=").append(outcome.code)
        append(" elapsedMs=").append(elapsedMillis.coerceAtLeast(0L))
        errorKind?.let { append(" errorKind=").append(it.code) }
    }

    private fun platformCode(platform: SharePlatform?): String = when (platform) {
        SharePlatform.QUARK -> "quark"
        SharePlatform.UC -> "uc"
        SharePlatform.XUNLEI -> "xunlei"
        SharePlatform.BAIDU -> "baidu"
        SharePlatform.C139 -> "c139"
        SharePlatform.PAN123 -> "pan123"
        null -> "unknown"
    }
}

class ResolveMetricSpan(
    private val platform: SharePlatform?,
    private val operation: ResolveMetricOperation,
    private val startedAtNanos: Long = System.nanoTime(),
    private val nowNanos: () -> Long = System::nanoTime,
    private val sink: (String) -> Unit,
    val operationId: String = OperationId.resolve()
) {
    private var completed = false

    fun success() = finish(ResolveMetricOutcome.SUCCESS)

    fun failure(error: Throwable) = failure(ResolveOperationMetric.errorKind(error))

    fun failure(errorKind: ResolveMetricErrorKind) =
        finish(ResolveMetricOutcome.FAILURE, errorKind)

    fun cancelled() = finish(ResolveMetricOutcome.CANCELLED)

    suspend fun <T> withRequestStage(stage: RequestStage, block: suspend () -> T): T =
        RequestOperationContextHolder.withContext(
            RequestOperationContext(
                operationId = operationId,
                platform = RequestPlatform.from(platform),
                stage = stage
            ),
            block
        )

    private fun finish(
        outcome: ResolveMetricOutcome,
        errorKind: ResolveMetricErrorKind? = null
    ) {
        if (completed) return
        completed = true
        sink(
            ResolveOperationMetric.line(
                operationId = operationId,
                platform = platform,
                operation = operation,
                outcome = outcome,
                elapsedMillis = ResolveOperationMetric.elapsedMillis(startedAtNanos, nowNanos()),
                errorKind = errorKind
            )
        )
    }
}
