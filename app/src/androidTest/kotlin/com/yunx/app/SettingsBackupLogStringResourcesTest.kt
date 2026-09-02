package com.yunx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackupLogStringResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun formatsBackupImportFeedback() {
        assertEquals(
            "此文件未加密，包含以下网盘的登录凭证：夸克网盘、UC 网盘。导入将覆盖同一网盘的现有凭证，是否继续？",
            context.getString(
                R.string.settings_auth_import_confirm_plaintext,
                "夸克网盘、UC 网盘"
            )
        )
        assertEquals(
            "已导入 2 个网盘账号",
            context.resources.getQuantityString(
                R.plurals.settings_auth_imported_accounts,
                2,
                2
            )
        )
        assertEquals("日志已分享", context.getString(R.string.settings_log_export_shared))
        assertEquals("凭证导出失败，请重试", context.getString(R.string.settings_auth_export_failed))
        assertEquals("下载保存目录已更新", context.getString(R.string.settings_download_directory_updated))
        assertEquals("已恢复默认下载目录", context.getString(R.string.settings_download_directory_restored))
    }
}
