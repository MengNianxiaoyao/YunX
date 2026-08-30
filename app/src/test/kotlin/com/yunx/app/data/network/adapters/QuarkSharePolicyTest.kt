package com.yunx.app.data.network.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuarkSharePolicyTest {
    @Test
    fun mapsUiExpireTypesToNormalizedDays() {
        assertNull(QuarkSharePolicy.expireDays(1))
        assertEquals(1, QuarkSharePolicy.expireDays(2))
        assertEquals(7, QuarkSharePolicy.expireDays(3))
        assertEquals(30, QuarkSharePolicy.expireDays(4))
    }

    @Test
    fun mapsNormalizedDaysToApiExpireTypes() {
        assertEquals(1, QuarkSharePolicy.expireType(null))
        assertEquals(2, QuarkSharePolicy.expireType(1))
        assertEquals(3, QuarkSharePolicy.expireType(7))
        assertEquals(4, QuarkSharePolicy.expireType(30))
    }
}
