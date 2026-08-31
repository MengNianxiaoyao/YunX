package com.yunx.app.data.network

import org.junit.Assert.assertThrows
import org.junit.Test

class C139ShareErrorsTest {
    @Test
    fun mapsDocumentedPasscodeAndProtocolCodes() {
        assertThrows(InvalidPasscodeException::class.java) {
            C139ShareErrors.throwIfKnown("9188")
        }
        assertThrows(ProtocolChangedException::class.java) {
            C139ShareErrors.throwIfKnown("9530")
        }
    }

    @Test
    fun leavesUnknownCodesToCaller() {
        C139ShareErrors.throwIfKnown("9999")
        C139ShareErrors.throwIfKnown("")
    }
}
