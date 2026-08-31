package com.yunx.app.data.metrics

import com.yunx.app.data.network.AuthExpiredException
import com.yunx.app.data.network.InvalidPasscodeException
import com.yunx.app.data.network.ProtocolChangedException
import com.yunx.app.data.network.RateLimitedException
import com.yunx.app.data.network.SharePlatform
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ResolveOperationMetricTest {
    @Test
    fun formatsOnlyAllowlistedFields() {
        val line = ResolveOperationMetric.line(
            operationId = "resolve-0123456789abcdef0123456789abcdef",
            platform = SharePlatform.QUARK,
            operation = ResolveMetricOperation.DIRECT_LINK,
            outcome = ResolveMetricOutcome.FAILURE,
            elapsedMillis = 125,
            errorKind = ResolveMetricErrorKind.NETWORK_UNAVAILABLE
        )

        assertEquals(
            "metric=resolve_operation operationId=resolve-0123456789abcdef0123456789abcdef " +
                "platform=quark operation=direct_link outcome=failure " +
                "elapsedMs=125 errorKind=network_unavailable",
            line
        )
        listOf("url", "fileName", "cookie", "token", "cursor", "fid").forEach {
            assertFalse(line.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun mapsErrorsWithoutExposingDiagnostics() {
        assertEquals(
            ResolveMetricErrorKind.AUTH_EXPIRED,
            ResolveOperationMetric.errorKind(AuthExpiredException("secret"))
        )
        assertEquals(
            ResolveMetricErrorKind.INVALID_PASSCODE,
            ResolveOperationMetric.errorKind(InvalidPasscodeException())
        )
        assertEquals(
            ResolveMetricErrorKind.RATE_LIMITED,
            ResolveOperationMetric.errorKind(RateLimitedException())
        )
        assertEquals(
            ResolveMetricErrorKind.NETWORK_UNAVAILABLE,
            ResolveOperationMetric.errorKind(IOException("https://secret.example"))
        )
        assertEquals(
            ResolveMetricErrorKind.PROTOCOL_CHANGED,
            ResolveOperationMetric.errorKind(ProtocolChangedException("secret platform detail"))
        )
        assertEquals(
            ResolveMetricErrorKind.UNKNOWN,
            ResolveOperationMetric.errorKind(IllegalStateException("secret server response"))
        )
    }

    @Test
    fun spanEmitsOnlyOneTerminalEvent() {
        val lines = mutableListOf<String>()
        val span = ResolveMetricSpan(
            platform = SharePlatform.UC,
            operation = ResolveMetricOperation.INITIAL_RESOLVE,
            startedAtNanos = 1_000_000L,
            nowNanos = { 6_000_000L },
            sink = lines::add,
            operationId = "resolve-0123456789abcdef0123456789abcdef"
        )

        span.success()
        span.failure(ResolveMetricErrorKind.UNKNOWN)
        span.cancelled()

        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("outcome=success"))
        assertTrue(lines.single().contains("elapsedMs=5"))
    }

    @Test
    fun unknownPlatformAndNegativeElapsedAreNormalized() {
        assertEquals(
            "metric=resolve_operation operationId=resolve-0123456789abcdef0123456789abcdef " +
                "platform=unknown operation=initial_resolve " +
                "outcome=failure elapsedMs=0 errorKind=invalid_input",
            ResolveOperationMetric.line(
                operationId = "resolve-0123456789abcdef0123456789abcdef",
                platform = null,
                operation = ResolveMetricOperation.INITIAL_RESOLVE,
                outcome = ResolveMetricOutcome.FAILURE,
                elapsedMillis = -1,
                errorKind = ResolveMetricErrorKind.INVALID_INPUT
            )
        )
    }

    @Test
    fun cancellationIsNeverClassifiedAsFailure() {
        assertThrows(CancellationException::class.java) {
            ResolveOperationMetric.errorKind(CancellationException("cancelled"))
        }
    }
}
