package com.yunx.app.data.metrics

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RequestContextInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun logsSuccessfulRequestWithContext() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val lines = mutableListOf<String>()
        execute(lines)

        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("operationId=resolve-0123456789abcdef0123456789abcdef"))
        assertTrue(lines.single().contains("platform=quark stage=direct_link"))
        assertTrue(lines.single().contains("httpStatus=200"))
        assertTrue(lines.single().contains("errorKind=none"))
    }

    @Test
    fun logsHttpFailureWithoutResponseBodyOrRequestSecrets() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("secret-response-body"))
        val lines = mutableListOf<String>()
        execute(lines)

        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("httpStatus=403"))
        assertTrue(lines.single().contains("errorKind=http_error"))
        assertFalse(lines.single().contains("secret-response-body"))
        assertFalse(lines.single().contains("secret-query"))
        assertFalse(lines.single().contains("secret-cookie"))
    }

    @Test
    fun propagatesNetworkFailureAndLogsStableError() = runBlocking {
        val url = server.url("/offline")
        server.shutdown()
        val lines = mutableListOf<String>()
        try {
            execute(lines, url)
            throw AssertionError("Expected network failure")
        } catch (_: IOException) {
            assertEquals(1, lines.size)
            assertTrue(lines.single().contains("httpStatus=none"))
            assertTrue(lines.single().contains("errorKind=network_unavailable"))
        }
    }

    @Test
    fun skipsSuccessfulRequestWithoutContextButStillLogsHttpFailureWhenDisabled() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        val noContextLines = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor(RequestContextInterceptor(noContextLines::add))
            .build()
        client.newCall(Request.Builder().url(server.url("/plain")).build()).execute().close()
        assertTrue(noContextLines.isEmpty())

        server.enqueue(MockResponse().setResponseCode(500))
        val errorLines = mutableListOf<String>()
        val context = RequestOperationContext(
            operationId = "download-0123456789abcdef0123456789abcdef",
            platform = RequestPlatform.UC,
            stage = RequestStage.DOWNLOAD,
            logSuccessfulRequests = false
        )
        RequestOperationContextHolder.withContext(context) {
            OkHttpClient.Builder()
                .addInterceptor(RequestContextInterceptor(errorLines::add))
                .build()
                .newCall(Request.Builder().url(server.url("/download")).build())
                .execute()
                .close()
        }
        assertEquals(1, errorLines.size)
        assertTrue(errorLines.single().contains("httpStatus=500"))
    }

    private suspend fun execute(
        lines: MutableList<String>,
        url: okhttp3.HttpUrl = server.url("/files/secret-query?token=secret-query")
    ) {
        val context = RequestOperationContext(
            operationId = "resolve-0123456789abcdef0123456789abcdef",
            platform = RequestPlatform.QUARK,
            stage = RequestStage.DIRECT_LINK
        )
        RequestOperationContextHolder.withContext(context) {
            OkHttpClient.Builder()
                .addInterceptor(RequestContextInterceptor(lines::add))
                .build()
                .newCall(
                    Request.Builder()
                        .url(url)
                        .header("Cookie", "secret-cookie")
                        .build()
                )
                .execute()
                .close()
        }
    }
}
