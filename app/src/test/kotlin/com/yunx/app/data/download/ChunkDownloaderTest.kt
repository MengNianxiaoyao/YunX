package com.yunx.app.data.download

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ChunkDownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        tempDir = Files.createTempDirectory("yunx-chunk-test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun downloadsValid206Range() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Range", "bytes 0-9/10")
                .setBody("abcdefghij")
        )
        val file = tempDir.resolve("part")

        val result = downloader().downloadChunk(
            taskId = 1L,
            url = server.url("/file").toString(),
            start = 0L,
            end = 9L,
            partFile = file,
            headers = emptyMap(),
            onBytes = {}
        )

        assertEquals(ChunkResult.OK, result)
        assertTrue(file.readBytes().contentEquals("abcdefghij".toByteArray()))
        assertEquals("bytes=0-9", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun returnsRangeIgnoredWhenServerReturns200() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setBody("whole file")
        )

        val result = downloader().downloadChunk(
            taskId = 2L,
            url = server.url("/file").toString(),
            start = 0L,
            end = 9L,
            partFile = tempDir.resolve("part"),
            headers = emptyMap(),
            onBytes = {}
        )

        assertEquals(ChunkResult.RANGE_IGNORED, result)
    }

    @Test
    fun returnsFailedForHtmlResponse() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "text/html")
                .setHeader("Content-Range", "bytes 0-9/10")
                .setBody("<html>expired</html>")
        )

        val result = downloader().downloadChunk(
            taskId = 3L,
            url = server.url("/file").toString(),
            start = 0L,
            end = 9L,
            partFile = tempDir.resolve("part"),
            headers = emptyMap(),
            onBytes = {}
        )

        assertEquals(ChunkResult.FAILED, result)
    }

    @Test
    fun returnsFailedForMismatchedContentRange() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Range", "bytes 1-10/11")
                .setBody("abcdefghij")
        )

        val result = downloader().downloadChunk(
            taskId = 4L,
            url = server.url("/file").toString(),
            start = 0L,
            end = 9L,
            partFile = tempDir.resolve("part"),
            headers = emptyMap(),
            onBytes = {}
        )

        assertEquals(ChunkResult.FAILED, result)
    }

    @Test
    fun returnsFailedWhenResponseBodyIsShort() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Range", "bytes 0-9/10")
                .setBody("short")
        )

        val result = downloader().downloadChunk(
            taskId = 5L,
            url = server.url("/file").toString(),
            start = 0L,
            end = 9L,
            partFile = tempDir.resolve("part"),
            headers = emptyMap(),
            onBytes = {}
        )

        assertEquals(ChunkResult.FAILED, result)
    }

    private fun downloader() = ChunkDownloader { OkHttpClient() }
}
