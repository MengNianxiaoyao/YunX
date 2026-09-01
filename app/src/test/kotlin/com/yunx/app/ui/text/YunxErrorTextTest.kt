package com.yunx.app.ui.text

import com.yunx.app.R
import com.yunx.app.data.error.YunxError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YunxErrorTextTest {
    @Test
    fun mapsTypedErrorsToResources() {
        assertEquals(
            UiText.Resource(R.string.error_auth_expired),
            YunxError.AuthExpired.toUiText(UiText.Resource(R.string.resolve_error_resolve_failed))
        )
        assertEquals(
            UiText.Resource(R.string.error_invalid_passcode),
            YunxError.InvalidPasscode.toUiText(UiText.Resource(R.string.resolve_error_resolve_failed))
        )
        assertEquals(
            UiText.Resource(R.string.error_protocol_changed, listOf("UC 网盘")),
            YunxError.ProtocolChanged("UC 网盘")
                .toUiText(UiText.Resource(R.string.resolve_error_resolve_failed))
        )
    }

    @Test
    fun unknownErrorUsesFallbackWithoutDiagnostic() {
        val fallback = UiText.Resource(R.string.resolve_error_save_failed)
        val text = YunxError.Unknown("sensitive server detail").toUiText(fallback)

        assertEquals(fallback, text)
        assertFalse(text.toString().contains("sensitive server detail"))
    }
}
