package com.yunx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class AboutSupportStringResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun formatsAboutVersionAndCredit() {
        assertEquals("YunX · v1.2.6 (10)", context.getString(R.string.about_version, "1.2.6", 10L))
        assertEquals(
            "云析 v1.2.6 · Made with ❤ and deepseek",
            context.getString(R.string.about_footer_credit, "1.2.6")
        )
        assertEquals("本项目基于 GNU AGPL-3.0 协议开源", context.getString(R.string.about_license_notice))
    }

    @Test
    fun exposesAboutAndSupportCopy() {
        assertEquals("关于云析", context.getString(R.string.about_title))
        assertEquals("支持平台", context.getString(R.string.about_supported_platforms_title))
        assertEquals("支持开发", context.getString(R.string.support_title))
        assertEquals("微信捐赠码", context.getString(R.string.support_wechat_qr_description))
        assertEquals("保存到相册", context.getString(R.string.support_save_to_gallery))
        assertEquals("已保存到相册", context.getString(R.string.support_saved_to_gallery))
        assertEquals("保存失败", context.getString(R.string.support_save_to_gallery_failed))
        assertEquals("关于", context.getString(R.string.settings_section_about))
        assertEquals("微信扫码捐赠，支持持续维护", context.getString(R.string.settings_support_description))
    }
}
