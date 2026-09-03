package com.yunx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStringResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun exposesOnboardingStrings() {
        assertEquals("云析", context.getString(R.string.onboarding_app_name))
        assertEquals("网盘分享链接解析与高速下载", context.getString(R.string.onboarding_tagline))
        assertEquals("一键解析分享链接", context.getString(R.string.onboarding_feature_resolve_title))
        assertEquals("高速分片下载", context.getString(R.string.onboarding_feature_download_title))
        assertEquals("多平台支持", context.getString(R.string.onboarding_feature_platforms_title))
        assertEquals("隐私安全", context.getString(R.string.onboarding_feature_privacy_title))
        assertEquals("完全免费", context.getString(R.string.onboarding_free_title))
        assertEquals("无广告、无内购，所有功能永久免费", context.getString(R.string.onboarding_free_description))
        assertEquals("开始使用", context.getString(R.string.onboarding_start))
    }

    @Test
    fun exposesGitHubAndScrollStrings() {
        assertEquals("开源仓库", context.getString(R.string.github_card_title))
        assertEquals("返回顶部", context.getString(R.string.action_scroll_to_top))
    }

    @Test
    fun exposesSettingsRemainderStrings() {
        assertEquals("下载通知详情", context.getString(R.string.settings_notification_detail_title))
        assertEquals("未授予通知权限，下载通知可能不可见（点按申请）", context.getString(R.string.settings_notification_permission_missing))
        assertEquals("显示进度条和下载速度", context.getString(R.string.settings_notification_show_speed))
        assertEquals("仅显示基础通知，不显示进度条和速度", context.getString(R.string.settings_notification_basic_only))
        assertEquals("8 线程", context.getString(R.string.settings_threads_count, 8))
    }
}
