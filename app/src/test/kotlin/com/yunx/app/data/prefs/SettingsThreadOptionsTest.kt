package com.yunx.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsThreadOptionsTest {
    @Test
    fun baiduMaximumIsEightThreads() {
        assertEquals(8, SettingsRepository.BAIDU_MAX_DOWNLOAD_THREADS)
        assertTrue(SettingsRepository.BAIDU_MAX_DOWNLOAD_THREADS < SettingsRepository.MAX_DOWNLOAD_THREADS)
    }
}
