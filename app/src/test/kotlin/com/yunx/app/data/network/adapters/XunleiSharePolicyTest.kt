package com.yunx.app.data.network.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XunleiSharePolicyTest {
    @Test
    fun mapsDisplayTypeToNormalizedDays() {
        assertNull(XunleiSharePolicy.normalizedDays(1))
        assertEquals(1, XunleiSharePolicy.normalizedDays(2))
        assertEquals(7, XunleiSharePolicy.normalizedDays(3))
        assertEquals(30, XunleiSharePolicy.normalizedDays(4))
    }

    @Test
    fun mapsNormalizedDaysToApiAndDisplayValues() {
        assertEquals("-1", XunleiSharePolicy.expireDays(null))
        assertEquals("1", XunleiSharePolicy.expireDays(1))
        assertEquals("7", XunleiSharePolicy.expireDays(7))
        assertEquals("30", XunleiSharePolicy.expireDays(30))
        assertEquals(1, XunleiSharePolicy.expireType(null))
        assertEquals(4, XunleiSharePolicy.expireType(30))
    }
}
