package com.yunx.app.data.error

import com.yunx.app.data.download.DownloadFailure
import com.yunx.app.data.download.DownloadFailureException
import com.yunx.app.data.download.DownloadFailureKind
import com.yunx.app.data.network.AuthExpiredException
import com.yunx.app.data.network.InvalidPasscodeException
import com.yunx.app.data.network.PasscodeRequiredException
import com.yunx.app.data.network.RateLimitedException
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
        assertEquals(YunxError.PasscodeRequired, YunxErrorClassifier.classify(PasscodeRequiredException()))
        assertEquals(YunxError.InvalidPasscode, YunxErrorClassifier.classify(InvalidPasscodeException()))
        assertEquals(YunxError.RateLimited, YunxErrorClassifier.classify(RateLimitedException()))
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
    fun mapsPlatformErrorsToActionableMessages() {
        assertEquals("该分享需要提取码", YunxErrorClassifier.userMessage(PasscodeRequiredException(), "失败"))
        assertEquals("提取码错误，请重新输入", YunxErrorClassifier.userMessage(InvalidPasscodeException(), "失败"))
        assertEquals("请求过于频繁，请稍后重试", YunxErrorClassifier.userMessage(RateLimitedException(), "失败"))
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
