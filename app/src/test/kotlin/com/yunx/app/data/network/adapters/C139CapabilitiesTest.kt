package com.yunx.app.data.network.adapters

import com.yunx.app.data.network.C139Api
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class C139CapabilitiesTest {
    @Test
    fun declaresSystemGeneratedPasscodeAndRootDirectory() {
        val capabilities = C139FileSource(C139Api()) { null }.capabilities

        assertEquals("139网盘", capabilities.name)
        assertEquals("/", capabilities.rootDir)
        assertFalse(capabilities.shareSupportsPasscode)
        assertTrue(capabilities.supportsShareSave)
        assertTrue(capabilities.supportsFolderDownload)
    }
}
