package com.yunx.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.yunx.app.R

/**
 * 主页底部导航的 4 个 Tab。
 */
enum class MainTab(
    @StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Resolve(R.string.nav_resolve, Icons.Filled.Link, Icons.Outlined.Link),
    Drive(R.string.nav_drive, Icons.Filled.Cloud, Icons.Outlined.Cloud),
    Download(R.string.nav_download, Icons.Filled.Download, Icons.Outlined.Download),
    Settings(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}
