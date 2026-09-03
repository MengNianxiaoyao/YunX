package com.yunx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkStringResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun exposesBookmarkStrings() {
        assertEquals("收藏网盘链接", context.getString(R.string.bookmark_title))
        assertEquals("添加收藏", context.getString(R.string.bookmark_add))
        assertEquals("链接已复制", context.getString(R.string.bookmark_link_copied))
        assertEquals("全部", context.getString(R.string.bookmark_filter_all))
        assertEquals("还没有收藏任何网盘链接", context.getString(R.string.bookmark_empty_title))
        assertEquals("网盘链接", context.getString(R.string.bookmark_link_label))
        assertEquals("粘贴分享链接", context.getString(R.string.bookmark_link_placeholder))
        assertEquals("标题（可选）", context.getString(R.string.bookmark_title_optional))
        assertEquals("提取码（可选）", context.getString(R.string.bookmark_pwd_optional))
        assertEquals("分类", context.getString(R.string.bookmark_category))
        assertEquals("收藏", context.getString(R.string.bookmark_confirm))
        assertEquals("添加至收藏", context.getString(R.string.bookmark_add_to_title))
        assertEquals("自定义", context.getString(R.string.bookmark_custom))
        assertEquals("自定义分类", context.getString(R.string.bookmark_custom_category))
        assertEquals("修改分类", context.getString(R.string.bookmark_edit_category))
        assertEquals("解析", context.getString(R.string.bookmark_resolve))
        assertEquals("复制链接", context.getString(R.string.bookmark_copy_link))
        assertEquals("网盘", context.getString(R.string.bookmark_platform_unknown))
    }
}
