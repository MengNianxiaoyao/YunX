package com.yunx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStringResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun formatsDownloadProgressAndNotification() {
        assertEquals(
            "已下载 1.0 MB / 2.0 MB · 50%",
            context.getString(R.string.download_progress_detail, "1.0 MB", "2.0 MB", 50)
        )
        assertEquals(
            "下载速度 1.5 MB/s",
            context.getString(R.string.download_notification_speed, "1.5 MB/s")
        )
        assertEquals("50%", context.getString(R.string.download_percent, 50))
    }

    @Test
    fun formatsDownloadCounts() {
        assertEquals(
            "2/5 个文件 · 1.0 GB / 2.0 GB",
            context.resources.getQuantityString(
                R.plurals.download_folder_file_progress,
                5,
                2,
                5,
                "1.0 GB",
                "2.0 GB"
            )
        )
        assertEquals(
            "1.5 MB/s · 剩余 2分3秒 · 4 线程",
            context.resources.getQuantityString(
                R.plurals.download_active_statistics,
                4,
                "1.5 MB/s",
                "2分3秒",
                4
            )
        )
    }
}
