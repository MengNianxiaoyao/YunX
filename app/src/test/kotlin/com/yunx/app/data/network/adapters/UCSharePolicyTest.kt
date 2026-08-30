package com.yunx.app.data.network.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UCSharePolicyTest {
    @Test
    fun mapsUiExpireTypesToNormalizedDays() {
        assertNull(UCSharePolicy.expireDays(1))
        assertEquals(1, UCSharePolicy.expireDays(2))
        assertEquals(7, UCSharePolicy.expireDays(3))
        assertEquals(30, UCSharePolicy.expireDays(4))
    }

    @Test
    fun mapsNormalizedDaysToApiExpireTypes() {
        assertEquals(1, UCSharePolicy.expireType(null))
        assertEquals(2, UCSharePolicy.expireType(1))
        assertEquals(3, UCSharePolicy.expireType(7))
        assertEquals(4, UCSharePolicy.expireType(30))
    }
}
