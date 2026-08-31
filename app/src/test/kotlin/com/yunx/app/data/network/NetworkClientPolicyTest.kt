package com.yunx.app.data.network

import com.yunx.app.data.prefs.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientPolicyTest {
    @Test
    fun downloadRequestLimitCoversAtLeastOneFullTask() {
        assertTrue(
            NetworkClientPolicy.MAX_DOWNLOAD_REQUESTS >= SettingsRepository.MAX_DOWNLOAD_THREADS
        )
    }

    @Test
    fun clientLimitsRemainBounded() {
        assertEquals(64, NetworkClientPolicy.MAX_API_REQUESTS)
        assertEquals(8, NetworkClientPolicy.MAX_API_REQUESTS_PER_HOST)
        assertEquals(64, NetworkClientPolicy.MAX_DOWNLOAD_REQUESTS)
        assertEquals(16, NetworkClientPolicy.MAX_DOWNLOAD_IDLE_CONNECTIONS)
    }
}
