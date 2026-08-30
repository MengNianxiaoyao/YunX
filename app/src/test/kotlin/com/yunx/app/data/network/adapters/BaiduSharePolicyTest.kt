package com.yunx.app.data.network.adapters

import com.yunx.app.data.network.BaiduApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaiduSharePolicyTest {
    @Test
    fun mapsNativePeriodToDisplayType() {
        assertEquals(1, BaiduSharePolicy.expireType(0))
        assertEquals(2, BaiduSharePolicy.expireType(1))
        assertEquals(3, BaiduSharePolicy.expireType(7))
        assertEquals(4, BaiduSharePolicy.expireType(30))
    }

    @Test
    fun declaresRequiredFourCharacterPasscode() {
        val capabilities = BaiduFileSource(BaiduApi()) { null }.capabilities

        assertTrue(capabilities.shareRequiresPasscode)
        assertEquals(4, capabilities.sharePasscodeLength)
        assertEquals("/", capabilities.rootDir)
    }
}
