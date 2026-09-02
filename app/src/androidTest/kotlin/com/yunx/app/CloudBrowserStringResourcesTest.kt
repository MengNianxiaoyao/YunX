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

    @Test
    fun formatsAccountStatusAndLogoutConfirmation() {
        assertEquals(
            "夸克网盘 · 已登录",
            context.getString(R.string.cloud_account_status_logged_in, "夸克网盘")
        )
        assertEquals(
            "确定要退出当前夸克账号吗？退出后将清除本地 Cookie。",
            context.getString(R.string.cloud_account_logout_confirm_quark)
        )
        assertEquals(
            "确定要退出当前 123 账号吗？退出后将清除本地凭证。",
            context.getString(R.string.cloud_account_logout_confirm_pan123)
        )
    }

    @Test
    fun exposesAccountActionStrings() {
        assertEquals("登录信息", context.getString(R.string.cloud_account_login_info))
        assertEquals("登录时间", context.getString(R.string.cloud_account_login_time))
        assertEquals("设备号", context.getString(R.string.cloud_account_device_id))
        assertEquals("展开全部", context.getString(R.string.cloud_account_credential_expand))
        assertEquals("收起", context.getString(R.string.cloud_account_credential_collapse))
        assertEquals("退出登录", context.getString(R.string.cloud_account_logout))
    }

    @Test
    fun exposesActionSheetStrings() {
        assertEquals("文件夹", context.getString(R.string.cloud_action_file_type_folder))
        assertEquals("重命名", context.getString(R.string.cloud_action_rename))
        assertEquals("移动到", context.getString(R.string.cloud_action_move_to))
        assertEquals("新文件名", context.getString(R.string.cloud_action_new_filename))
        assertEquals(
            "当前目录没有子文件夹，可直接移动到此处",
            context.getString(R.string.cloud_action_move_empty)
        )
        assertEquals(
            "移动到此处（根目录）",
            context.getString(R.string.cloud_action_move_to_here, "根目录")
        )
    }

    @Test
    fun exposesSaveSheetStrings() {
        assertEquals(
            "转存到夸克网盘",
            context.getString(R.string.cloud_save_to_platform, "夸克网盘")
        )
        assertEquals("重试", context.getString(R.string.cloud_save_retry))
        assertEquals(
            "当前目录没有子文件夹，可直接转存到此目录",
            context.getString(R.string.cloud_save_empty_dirs)
        )
        assertEquals(
            "转存到此目录（根目录）",
            context.getString(R.string.cloud_save_to_this_dir, "根目录")
        )
    }

    @Test
    fun exposesDriveScreenStrings() {
        assertEquals("登录后即可自动携带凭证解析与下载", context.getString(R.string.drive_login_hint))
        assertEquals("登录已过期，点击重新登录", context.getString(R.string.drive_login_expired))
        assertEquals("点击登录，支持解析下载", context.getString(R.string.drive_login_prompt))
        assertEquals("风控风险高，可能导致账号被限制", context.getString(R.string.drive_baidu_risk_warning))
        assertEquals("已用 1.0 GB / 2.0 GB", context.getString(R.string.drive_quota_usage, "1.0 GB", "2.0 GB"))
        assertEquals("已登录", context.getString(R.string.drive_status_logged_in))
        assertEquals("未登录", context.getString(R.string.drive_status_logged_out))
        assertEquals("夸", context.getString(R.string.drive_avatar_quark))
        assertEquals("迅", context.getString(R.string.drive_avatar_xunlei))
        assertEquals("度", context.getString(R.string.drive_avatar_baidu))
    }

    @Test
    fun exposesPlatformShareStrings() {
        assertEquals("分享文件", context.getString(R.string.cloud_share_title))
        assertEquals("提取码", context.getString(R.string.cloud_share_passcode))
        assertEquals("无提取码", context.getString(R.string.cloud_share_no_passcode))
        assertEquals("设置提取码", context.getString(R.string.cloud_share_set_passcode))
        assertEquals("永久有效", context.getString(R.string.cloud_share_permanent))
        assertEquals("30 天", context.getString(R.string.cloud_share_thirty_days))
        assertEquals("创建分享", context.getString(R.string.cloud_share_create))
        assertEquals("生成分享链接（自动带提取码）", context.getString(R.string.cloud_action_share_desc_auto))
        assertEquals("生成分享链接（可设提取码/有效期）", context.getString(R.string.cloud_action_share_desc_custom))
        assertEquals(
            "迅雷分享必须带提取码，可自动生成 4 位（或自定义）。",
            context.getString(R.string.cloud_share_xunlei_note)
        )
        assertEquals(
            "百度网盘非会员超过 300MB 会被限速，下载速度可能较慢。是否继续下载？",
            context.getString(R.string.cloud_baidu_large_file_message)
        )
    }
}
