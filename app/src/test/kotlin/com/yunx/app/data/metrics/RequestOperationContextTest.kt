package com.yunx.app.data.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RequestOperationContextTest {
    @Test
    fun createsOpaqueValidatedIds() {
        val id = OperationId.create(
            prefix = "resolve",
            uuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")
        )

        assertEquals("resolve-0123456789abcdef0123456789abcdef", id)
        assertTrue(OperationId.isValid(id))
        assertTrue(OperationId.isDownload("download-0123456789abcdef0123456789abcdef"))
        assertFalse(OperationId.isDownload(id))
        assertFalse(OperationId.isValid("resolve-https://secret.example"))
        assertFalse(OperationId.isValid("download-file-name"))
    }

    @Test
    fun formatsFixedAllowlistedRequestContext() {
        val line = RequestContextLog.line(
            context = RequestOperationContext(
                operationId = "resolve-0123456789abcdef0123456789abcdef",
                platform = RequestPlatform.BAIDU,
                stage = RequestStage.DIRECT_LINK,
                retry = 2
            ),
            httpStatus = 403,
            elapsedMillis = 125,
            errorKind = RequestLogErrorKind.HTTP_ERROR
        )

        assertEquals(
            "event=request_context operationId=resolve-0123456789abcdef0123456789abcdef " +
                "platform=baidu stage=direct_link httpStatus=403 retry=2 elapsedMs=125 " +
                "errorKind=http_error",
            line
        )
        listOf("url", "fileName", "cookie", "authorization", "token", "path", "message", "fid", "cursor")
            .forEach { assertFalse(line.contains(it, ignoreCase = true)) }
    }

    @Test
    fun normalizesInvalidValues() {
        val line = RequestContextLog.line(
            context = RequestOperationContext(
                operationId = "secret value",
                platform = RequestPlatform.UNKNOWN,
                stage = RequestStage.DIRECTORY_LIST,
                retry = -1
            ),
            httpStatus = 999,
            elapsedMillis = -1
        )

        assertEquals(
            "event=request_context operationId=invalid platform=unknown stage=directory_list " +
                "httpStatus=none retry=0 elapsedMs=0 errorKind=none",
            line
        )
        assertFalse(line.contains("secret value"))
    }
}
