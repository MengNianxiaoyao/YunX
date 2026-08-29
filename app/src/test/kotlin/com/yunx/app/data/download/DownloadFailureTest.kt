package com.yunx.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DownloadFailureTest {
    @Test
    fun classifiesNetworkFailureAsRetryable() {
        val failure = DownloadFailureClassifier.classify(IOException("timeout"))
        assertEquals(DownloadFailureKind.NETWORK, failure.kind)
        assertTrue(failure.kind.retryable)
    }

    @Test
    fun doesNotRetryStorageFailure() {
        val failure = DownloadFailureClassifier.classify(
            IllegalStateException("未授予存储权限，无法保存到下载目录")
        )
        assertEquals(DownloadFailureKind.STORAGE, failure.kind)
        assertFalse(failure.kind.retryable)
    }

    @Test
    fun classifiesExpiredHtmlLink() {
        val failure = DownloadFailureClassifier.classify(
            IllegalStateException("下载失败：链接已失效或需要 Referer（返回 HTML 页）")
        )
        assertEquals(DownloadFailureKind.LINK_EXPIRED, failure.kind)
    }

    @Test
    fun wrapsFailureWithStableUserMessage() {
        val failure = DownloadFailure(DownloadFailureKind.INTEGRITY, "文件大小校验失败")
        assertEquals("文件完整性校验失败，请重新下载", DownloadFailureException(failure).message)
    }
}
