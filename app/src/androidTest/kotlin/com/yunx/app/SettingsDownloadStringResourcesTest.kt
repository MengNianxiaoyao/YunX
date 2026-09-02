package com.yunx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDownloadStringResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun formatsDownloadSettingsValues() {
        assertEquals(
            "按网盘设置分片并发数（默认 16，最高 32）",
            context.getString(R.string.settings_download_threads_description, 16, 32)
        )
        assertEquals(
            "最多同时下载 3 个任务，其余任务排队",
            context.getString(R.string.settings_download_concurrency_description, 3)
        )
        assertEquals(
            "网络失败后自动重试 2 次，并保留已下载内容",
            context.getString(R.string.settings_download_retry_enabled_description, 2)
        )
        assertEquals("8 线程", context.getString(R.string.settings_threads_value, 8))
        assertEquals("固定为 8 线程", context.getString(R.string.settings_threads_fixed, 8))
    }

    @Test
    fun exposesBatteryOptimizationStates() {
        assertEquals(
            "WakeLock 已开启；已允许忽略电池优化",
            context.getString(R.string.settings_download_keep_locked_allowed)
        )
        assertEquals(
            "无法打开电池优化设置",
            context.getString(R.string.settings_battery_optimization_open_failed)
        )
    }
}
