package com.yunx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudBrowserStringResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun formatsBrowserCountsAndDeleteConfirmation() {
        assertEquals(
            "已选 2 项",
            context.resources.getQuantityString(R.plurals.cloud_selected_count, 2, 2)
        )
        assertEquals(
            "共 5 项",
            context.resources.getQuantityString(R.plurals.cloud_item_count, 5, 5)
        )
        assertEquals(
            "确定要删除选中的 2 项吗？删除后进入回收站。",
            context.getString(R.string.cloud_delete_confirmation, "选中的 2 项")
        )
    }

    @Test
    fun exposesSharedBrowserActions() {
        assertEquals("加载更多", context.getString(R.string.cloud_load_more))
        assertEquals("处理中", context.getString(R.string.cloud_processing_title))
        assertEquals("文件", context.getString(R.string.cloud_file_type_file))
        assertEquals("删除", context.getString(R.string.cloud_action_delete))
    }
}
