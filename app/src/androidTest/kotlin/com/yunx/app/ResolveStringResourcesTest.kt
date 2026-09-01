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

    @Test
    fun formatsResolveFeedback() {
        assertEquals(
            "请先在「网盘」页登录夸克网盘",
            context.getString(R.string.resolve_error_login_platform_in_drive, "夸克网盘")
        )
        assertEquals(
            "已转存 2 项，失败 1 项",
            context.getString(R.string.resolve_batch_save_partial, 2, 1)
        )
        assertEquals(
            "已加入 3 个下载任务",
            context.getString(R.string.resolve_batch_download_success, 3)
        )
        assertEquals(
            "已收藏到「工作」",
            context.getString(R.string.resolve_bookmark_success, "工作")
        )
        assertEquals(
            "UC 网盘接口可能已变化，请更新应用或稍后重试",
            context.getString(R.string.error_protocol_changed, "UC 网盘")
        )
    }
}
