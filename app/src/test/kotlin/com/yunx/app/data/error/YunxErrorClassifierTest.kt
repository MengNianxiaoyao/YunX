package com.yunx.app.data.error

import com.yunx.app.data.download.DownloadFailure
import com.yunx.app.data.download.DownloadFailureException
import com.yunx.app.data.download.DownloadFailureKind
import com.yunx.app.data.network.AuthExpiredException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class YunxErrorClassifierTest {
    @Test
    fun classifiesTypedErrorsWithoutMessageMatching() {
        assertEquals(YunxError.AuthExpired, YunxErrorClassifier.classify(AuthExpiredException("expired")))
        assertEquals(YunxError.NetworkUnavailable, YunxErrorClassifier.classify(IOException("offline")))
        assertEquals(
            YunxError.IntegrityCheckFailed,
            YunxErrorClassifier.classify(
                DownloadFailureException(
                    DownloadFailure(DownloadFailureKind.INTEGRITY, "hash mismatch")
                )
            )
        )
    }

    @Test
    fun hidesUnknownDiagnosticMessageFromUser() {
        assertEquals(
            "加载失败",
            YunxErrorClassifier.userMessage(IllegalStateException("sensitive server detail"), "加载失败")
        )
    }

    @Test
    fun neverConvertsCoroutineCancellationToBusinessError() {
        assertThrows(CancellationException::class.java) {
            YunxErrorClassifier.classify(CancellationException("cancelled"))
        }
    }
}
