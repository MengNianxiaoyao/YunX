package com.yunx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveStringResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun formatsClipboardAndBatchProgress() {
        assertEquals(
            "检测到 夸克网盘 分享链接",
            context.getString(R.string.resolve_clipboard_detected_link, "夸克网盘")
        )
        assertEquals(
            "正在获取下载链接 2/5",
            context.getString(R.string.resolve_batch_fetching_links, 2, 5)
        )
    }

    @Test
    fun formatsResolveCounts() {
        assertEquals(
            "已选 2 项",
            context.resources.getQuantityString(R.plurals.resolve_selected_count, 2, 2)
        )
        assertEquals(
            "共 5 项",
            context.resources.getQuantityString(R.plurals.resolve_item_count, 5, 5)
        )
    }
}
