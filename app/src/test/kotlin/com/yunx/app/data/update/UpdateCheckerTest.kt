package com.yunx.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun extractsNetdiskDownloadUrl() {
        assertEquals(
            "https://pan.quark.cn/s/a7287ee935cb",
            UpdateChecker.netdiskDownloadUrl(
                "## 更新内容\n- 修复xx\n[网盘下载](https://pan.quark.cn/s/a7287ee935cb)\n"
            )
        )
    }

    @Test
    fun returnsNullWhenNoNetdiskEntry() {
        assertNull(UpdateChecker.netdiskDownloadUrl("## 更新内容\n- 修复xx\n"))
        assertNull(UpdateChecker.netdiskDownloadUrl(""))
        assertNull(UpdateChecker.netdiskDownloadUrl("[网盘下载]()"))
    }

    @Test
    fun compareVersionsOrdersReleases() {
        assertTrue(UpdateChecker.compareVersions("v1.2.6", "1.2.5") > 0)
        assertTrue(UpdateChecker.compareVersions("1.2.5", "v1.2.6") < 0)
        assertEquals(0, UpdateChecker.compareVersions("v1.2.6", "1.2.6"))
        // 正式版大于预发布版；预发布版之间按后缀字典序
        assertTrue(UpdateChecker.compareVersions("1.2.6", "1.2.6-beta") > 0)
        assertTrue(UpdateChecker.compareVersions("1.2.6-beta", "1.2.6") < 0)
    }
}
