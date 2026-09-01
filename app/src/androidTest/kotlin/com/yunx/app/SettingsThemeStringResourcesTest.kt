package com.yunx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsThemeStringResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun formatsUpdateMessages() {
        assertEquals(
            "当前版本：1.2.3",
            context.getString(R.string.update_dialog_current_version, "1.2.3")
        )
        assertEquals(
            "已加入下载队列：yunx.apk",
            context.getString(R.string.settings_update_enqueued, "yunx.apk")
        )
        assertEquals(
            "已通过镜像站加入下载队列：yunx.apk",
            context.getString(R.string.settings_update_mirror_enqueued, "yunx.apk")
        )
    }

    @Test
    fun keepsThemeLabelsAvailable() {
        assertEquals("主题与外观", context.getString(R.string.theme_title))
        assertEquals("跟随系统", context.getString(R.string.theme_mode_system))
        assertEquals("经典图标", context.getString(R.string.theme_icon_classic))
        assertEquals("天蓝", context.getString(R.string.theme_color_sky_blue))
    }
}
