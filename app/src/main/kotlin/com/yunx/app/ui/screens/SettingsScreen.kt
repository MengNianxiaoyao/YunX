package com.yunx.app.ui.screens
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunx.app.R
import com.yunx.app.data.backup.AuthBackupManager
import com.yunx.app.data.backup.AuthCrypto
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.download.DownloadSaver
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.data.update.UpdateChecker
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.text.UiText
import com.yunx.app.ui.text.resolve
import com.yunx.app.util.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 可选的下载线程数档位；限制峰值连接数，避免内存、网络和服务端压力。 */
private val threadOptions = listOf(1, 2, 4, 8, 16, 32)

/** 按平台下载线程数设置项 */
private data class ThreadPlatform(@StringRes val labelRes: Int, val platform: String)

private val threadPlatforms = listOf(
    ThreadPlatform(R.string.platform_quark, DownloadPlatform.QUARK),
    ThreadPlatform(R.string.platform_uc, DownloadPlatform.UC),
    ThreadPlatform(R.string.platform_xunlei, DownloadPlatform.XUNLEI),
    ThreadPlatform(R.string.platform_baidu, DownloadPlatform.BAIDU),
    ThreadPlatform(R.string.platform_c139, DownloadPlatform.C139),
    ThreadPlatform(R.string.platform_pan123, DownloadPlatform.PAN123),
)

/**
 * 设置页：下载线程数设置 + 主题外观 + 检查更新 + 日志与网盘认证。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    scrollBehavior: TopAppBarScrollBehavior,
    onThemeClick: () -> Unit,
    onAboutClick: () -> Unit,
    onSupportClick: () -> Unit,
    backupManager: AuthBackupManager,
     /** 用应用内置下载器下载更新 APK（URL + 文件名），由 MainScreen 注入 DownloadManager */
    onDownloadUpdateApk: (url: String, fileName: String, sha256: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showThreadsDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    // 检查更新结果（非空时弹更新对话框）
    var updateRelease by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    // 网盘认证导出弹窗（AES 加密 + 导出范围）
    var showExportAuthDialog by remember { mutableStateOf(false) }
    // 网盘认证导入：加密文件内容（非空时弹解密密码框）
    var pendingImportContent by remember { mutableStateOf<String?>(null) }
    var pendingPlaintextImport by remember { mutableStateOf<String?>(null) }
    var showImportAuthDialog by remember { mutableStateOf(false) }
    // 导出/导入处理中（PBKDF2 21万次迭代派生密钥，偶发 1~3s，期间显示加载弹窗）
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    // 按平台线程数：二级弹窗当前选择的平台
    var selectedThreadPlatform by remember { mutableStateOf(threadPlatforms.first()) }
    var showPlatformThreadDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    // 下载保存目录（SAF）：本地状态驱动 UI 刷新，同时同步 SharedPreferences
    val settingsRepo = remember { SettingsRepository(context) }
    var downloadDirUri by remember { mutableStateOf(settingsRepo.downloadDirUri) }
    var showDevMenu by remember { mutableStateOf(false) }
    // 网络与下载策略（本地状态驱动 UI，同时同步 SharedPreferences）
    var maxConcurrent by remember { mutableStateOf(settingsRepo.maxConcurrentDownloads) }
    var speedLimitBps by remember { mutableStateOf(settingsRepo.downloadSpeedLimit) }
    var retryCount by remember { mutableStateOf(settingsRepo.downloadRetryCount) }
    var showConcurrencyDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showRetryDialog by remember { mutableStateOf(false) }
    // 用户体验与系统适配：锁屏保持下载 / 通知栏速度
    var keepLocked by remember { mutableStateOf(settingsRepo.keepDownloadWhenLocked) }
    var ignoresBatteryOptimizations by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations())
    }
    var showSpeed by remember { mutableStateOf(settingsRepo.notificationShowSpeed) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ignoresBatteryOptimizations = context.isIgnoringBatteryOptimizations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 通知权限（Android 13+）：未授权时点击「通知栏下载进度」先申请，授权后生效
    val notifyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showSpeed = true
            settingsRepo.notificationShowSpeed = true
        }
    }
    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // 持久授权：应用重启后仍可写（API19+；Android 10/11+ 分区存储必需）
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            settingsRepo.downloadDirUri = uri.toString()
            downloadDirUri = uri.toString()
            SnackbarController.show(UiText.Resource(R.string.settings_download_directory_updated))
        }
    }
    // 导入网盘认证文件选择器：选择后先判断是否加密备份，加密则弹密码框
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                try {
                    val text = runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                    if (text == null) {
                        SnackbarController.show(UiText.Resource(R.string.settings_auth_import_read_failed))
                        return@launch
                    }
                    if (AuthCrypto.isEncrypted(text)) {
                        // 加密备份：关闭加载弹窗，弹解密密码框（解密在确认后执行）
                        pendingImportContent = text
                        showImportAuthDialog = true
                    } else {
                        pendingPlaintextImport = text
                    }
                } finally {
                    isImporting = false
                }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val content = pendingExportContent
        pendingExportContent = null
        if (uri != null && content != null) {
            scope.launch {
                val saved = backupManager.saveToFile(uri, context, content)
                SnackbarController.show(
                    UiText.Resource(
                        if (saved) R.string.settings_auth_export_saved
                        else R.string.settings_auth_export_save_failed
                    )
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionLabel(stringResource(R.string.settings_download_section))
        SettingsItem(
            icon = Icons.Outlined.Tune,
            title = stringResource(R.string.settings_download_threads_title),
            description = stringResource(
                R.string.settings_download_threads_description,
                SettingsRepository.DEFAULT_DOWNLOAD_THREADS,
                SettingsRepository.MAX_DOWNLOAD_THREADS
            ),
            onClick = { showThreadsDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 下载保存目录：系统文件夹选择器（SAF，适配各 Android 版本分区存储）；
        // 已自定义时卡片右侧内嵌「恢复默认」操作（不单独外露按钮）
        SettingsItem(
            icon = Icons.Outlined.FolderOpen,
            title = stringResource(R.string.settings_download_directory_title),
            description = downloadDirUri?.let {
                stringResource(
                    R.string.settings_download_directory_current,
                    DownloadSaver.safDirDisplay(it)
                )
            } ?: stringResource(R.string.settings_download_directory_default),
            onClick = { dirLauncher.launch(null) },
            trailing = if (downloadDirUri != null) {
                {
                    TextButton(
                        onClick = {
                            downloadDirUri = null
                            settingsRepo.downloadDirUri = null
            SnackbarController.show(UiText.Resource(R.string.settings_download_directory_restored))
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_download_directory_restore),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                null
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 网络与下载策略
        SettingsItem(
            icon = Icons.Outlined.Layers,
            title = stringResource(R.string.settings_download_concurrency_title),
            description = stringResource(
                R.string.settings_download_concurrency_description,
                maxConcurrent
            ),
            onClick = { showConcurrencyDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            icon = Icons.Outlined.Speed,
            title = stringResource(R.string.settings_download_speed_title),
            description = stringResource(
                R.string.settings_download_speed_description,
                speedLimitText(speedLimitBps)
            ),
            onClick = { showSpeedDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            icon = Icons.Outlined.Refresh,
            title = stringResource(R.string.settings_download_retry_title),
            description = if (retryCount == 0) {
                stringResource(R.string.settings_download_retry_disabled_description)
            } else {
                stringResource(R.string.settings_download_retry_enabled_description, retryCount)
            },
            onClick = { showRetryDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 用户体验与系统适配：锁屏保持下载 / 通知栏进度样式
        SettingsItem(
            icon = Icons.Outlined.Power,
            title = stringResource(R.string.settings_download_keep_locked_title),
            description = when {
                !keepLocked -> stringResource(R.string.settings_download_keep_locked_disabled)
                ignoresBatteryOptimizations -> stringResource(R.string.settings_download_keep_locked_allowed)
                else -> stringResource(R.string.settings_download_keep_locked_not_allowed)
            },
            onClick = {
                if (!ignoresBatteryOptimizations) {
                    if (!keepLocked) {
                        keepLocked = true
                        settingsRepo.keepDownloadWhenLocked = true
                    }
                    showBatteryDialog = true
                } else {
                    keepLocked = !keepLocked
                    settingsRepo.keepDownloadWhenLocked = keepLocked
                }
            },
            trailing = {
                Switch(
                    checked = keepLocked,
                    onCheckedChange = { enabled ->
                        keepLocked = enabled
                        settingsRepo.keepDownloadWhenLocked = enabled
                        if (enabled && !ignoresBatteryOptimizations) showBatteryDialog = true
                    }
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            icon = Icons.Outlined.Notifications,
            title = "下载通知详情",
            description = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED ->
                    "未授予通知权限，下载通知可能不可见（点按申请）"
                showSpeed -> "显示进度条和下载速度"
                else -> "仅显示基础通知，不显示进度条和速度"
            },
            onClick = {
                // Android 13+ 未授权：先申请通知权限，授权后自动开启完整通知
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    showSpeed = !showSpeed
                    settingsRepo.notificationShowSpeed = showSpeed
                }
            },
            trailing = { Switch(checked = showSpeed, onCheckedChange = null) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.settings_section_appearance))
        SettingsItem(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.settings_theme_title),
            description = stringResource(R.string.settings_theme_description),
            onClick = onThemeClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.settings_section_general))
        SettingsItem(
            icon = Icons.Outlined.SystemUpdate,
            title = stringResource(R.string.settings_update_check_title),
            description = stringResource(R.string.settings_update_check_description),
            onClick = {
                scope.launch {
                    SnackbarController.show(UiText.Resource(R.string.settings_update_checking))
                    val release = runCatching { UpdateChecker.fetchLatestRelease() }.getOrNull()
                    val current = UpdateChecker.currentVersion(context)
                    if (release == null) {
                        SnackbarController.show(UiText.Resource(R.string.settings_update_check_failed))
                    } else if (UpdateChecker.compareVersions(release.tagName, current) > 0) {
                        updateRelease = release
                    } else {
                        SnackbarController.show(UiText.Resource(R.string.settings_update_already_latest))
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem(
            icon = Icons.AutoMirrored.Outlined.Article,
            title = stringResource(R.string.settings_log_export_title),
            description = stringResource(R.string.settings_log_export_description),
            onClick = { showLogDialog = true }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.settings_auth_backup_section))
        SettingsItem(
            icon = Icons.Outlined.Backup,
            title = stringResource(R.string.settings_auth_export_title),
            description = stringResource(R.string.settings_auth_export_description),
            onClick = { showExportAuthDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem(
            icon = Icons.Outlined.Restore,
            title = stringResource(R.string.settings_auth_import_title),
            description = stringResource(R.string.settings_auth_import_description),
            onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.settings_section_about))
        SettingsItem(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.about_title),
            description = stringResource(R.string.settings_about_description),
            onClick = onAboutClick,
            onLongClick = { showDevMenu = true } // 长按打开隐藏开发调试菜单
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem(
            icon = Icons.Outlined.VolunteerActivism,
            title = stringResource(R.string.support_title),
            description = stringResource(R.string.settings_support_description),
            onClick = onSupportClick
        )
    }

    // 导出日志方式选择弹窗
    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text(stringResource(R.string.settings_log_export_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_log_export_choose_method),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showLogDialog = false
                            scope.launch {
                                val file = withContext(Dispatchers.IO) { LogExporter.export(context) }
                                if (file != null && LogExporter.share(context, file)) {
                                     SnackbarController.show(UiText.Resource(R.string.settings_log_export_shared))
                                } else {
                                     SnackbarController.show(UiText.Resource(R.string.settings_log_export_failed))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_log_export_share))
                    }
                    TextButton(
                        onClick = {
                            showLogDialog = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    LogExporter.saveToDownloads(context)
                                }
                                SnackbarController.show(
                                    UiText.Resource(
                                        if (ok) R.string.settings_log_export_saved_downloads
                                        else R.string.settings_log_export_save_failed
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_log_export_save_downloads))
                    }
                    TextButton(
                        onClick = {
                            showLogDialog = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    LogExporter.clearLogcat()
                                }
                                SnackbarController.show(
                                    UiText.Resource(
                                        if (ok) R.string.settings_log_export_cleared
                                        else R.string.settings_log_export_clear_failed
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_log_export_clear_logcat))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text(stringResource(R.string.settings_log_export_cancel))
                }
            }
        )
    }

    // 隐藏开发调试菜单（长按「关于云析」打开）
    if (showDevMenu) {
        AlertDialog(
            onDismissRequest = { showDevMenu = false },
            title = { Text(stringResource(R.string.settings_developer_title)) },
            text = {
                Column {
                    Button(
                        onClick = {
                            showDevMenu = false
                            // 调试用途：直接弹出更新弹窗（不判断是否已是最新版），预览弹窗 UI
                            scope.launch {
                                val release = runCatching { UpdateChecker.fetchLatestRelease() }.getOrNull()
                                updateRelease = release ?: UpdateChecker.Release(
                                    tagName = context.getString(R.string.settings_developer_preview_version),
                                    body = context.getString(R.string.settings_developer_preview_release_notes),
                                    assets = emptyList(),
                                    publishedAt = ""
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_developer_preview_update)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevMenu = false }) {
                    Text(stringResource(R.string.settings_action_close))
                }
            }
        )
    }

    // 检查更新结果弹窗（发现新版本时展示，下载走系统浏览器）
    updateRelease?.let { release ->
        UpdateDialog(
            currentVersion = UpdateChecker.currentVersion(context),
            release = release,
            onDownload = {
                updateRelease = null
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", true) }
                if (apk != null) {
                    onDownloadUpdateApk(apk.downloadUrl, apk.name, UpdateChecker.expectedSha256(release.body).orEmpty())
                    SnackbarController.show(
                        UiText.Resource(R.string.settings_update_enqueued, listOf(apk.name))
                    )
                } else {
                    SnackbarController.show(UiText.Resource(R.string.settings_update_apk_not_found))
                }
            },
            onDownloadMirror = {
                updateRelease = null
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", true) }
                if (apk != null) {
                    onDownloadUpdateApk(UpdateChecker.mirrorUrl(apk.downloadUrl), apk.name, UpdateChecker.expectedSha256(release.body).orEmpty())
                    SnackbarController.show(
                        UiText.Resource(R.string.settings_update_mirror_enqueued, listOf(apk.name))
                    )
                } else {
                    SnackbarController.show(UiText.Resource(R.string.settings_update_apk_not_found))
                }
            },
            onLater = { updateRelease = null },
            onIgnore = {
                context.getSharedPreferences("yunx_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("ignored_version", release.tagName)
                    .apply()
                updateRelease = null
            }
        )
    }

    // 线程数选择弹窗（按平台）
    if (showThreadsDialog) {
        AlertDialog(
            onDismissRequest = { showThreadsDialog = false },
            title = { Text(stringResource(R.string.settings_download_threads_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_threads_dialog_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    threadPlatforms.forEach { item ->
                        val current = settingsRepo.downloadThreadsFor(item.platform)
                        val isXunlei = item.platform == DownloadPlatform.XUNLEI
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isXunlei) {
                                    selectedThreadPlatform = item
                                    showPlatformThreadDialog = true
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                 text = stringResource(item.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = when {
                                     isXunlei -> stringResource(R.string.settings_threads_fixed, 8)
                                     else -> stringResource(R.string.settings_threads_value, current)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isXunlei) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            if (!isXunlei) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThreadsDialog = false }) {
                    Text(stringResource(R.string.settings_dialog_cancel))
                }
            }
        )
    }

    // 单个平台线程数选择（二级弹窗）
    if (showPlatformThreadDialog) {
        val current = settingsRepo.downloadThreadsFor(selectedThreadPlatform.platform)
        val availableThreadOptions = if (selectedThreadPlatform.platform == DownloadPlatform.BAIDU) {
            threadOptions.filter { it <= SettingsRepository.BAIDU_MAX_DOWNLOAD_THREADS }
        } else {
            threadOptions
        }
        AlertDialog(
            onDismissRequest = { showPlatformThreadDialog = false },
            title = {
                Text(
                    stringResource(
                        R.string.settings_platform_threads_title,
                        stringResource(selectedThreadPlatform.labelRes)
                    )
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    availableThreadOptions.chunked(2).forEach { rowValues ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowValues.forEach { value ->
                                RadioThreadRow(
                                    value = value,
                                    threads = current,
                                    onSelect = { v ->
                                        settingsRepo.setDownloadThreads(selectedThreadPlatform.platform, v)
                                        showPlatformThreadDialog = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // 奇数个时补空占位，保持两列对齐
                            if (rowValues.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlatformThreadDialog = false }) {
                    Text(stringResource(R.string.settings_dialog_cancel))
                }
            }
        )
    }

    // 导出网盘认证弹窗（AES 加密密码 + 导出范围）
    if (showExportAuthDialog) {
        ExportAuthDialog(
            onDismiss = { showExportAuthDialog = false },
            onConfirm = { password, onlyLoggedIn ->
                showExportAuthDialog = false
                isExporting = true
                scope.launch {
                    try {
                        val content = runCatching {
                            withContext(Dispatchers.IO) { backupManager.export(password, onlyLoggedIn) }
                        }.getOrNull()
                        if (content == null) {
                            SnackbarController.show(UiText.Resource(R.string.settings_auth_export_failed))
                            return@launch
                        }
                        pendingExportContent = content
                        exportLauncher.launch("yunx_auth_backup_${System.currentTimeMillis()}.yunx")
                    } finally {
                        isExporting = false
                    }
                }
            }
        )
    }

    // 导入加密备份弹窗（解密密码）
    if (showImportAuthDialog) {
        ImportAuthDialog(
            onDismiss = {
                showImportAuthDialog = false
                pendingImportContent = null
            },
            onConfirm = { password ->
                showImportAuthDialog = false
                val content = pendingImportContent
                pendingImportContent = null
                if (content != null) {
                    isImporting = true
                    scope.launch {
                        try {
                            val count = try {
                                withContext(Dispatchers.IO) { backupManager.import(content, password) }
                            } catch (e: javax.crypto.AEADBadTagException) {
                                SnackbarController.show(UiText.Resource(R.string.settings_auth_decryption_failed))
                                return@launch
                            } catch (e: Exception) {
                                SnackbarController.show(UiText.Resource(R.string.settings_auth_import_failed))
                                return@launch
                            }
                            SnackbarController.show(importResultText(count))
                        } finally {
                            isImporting = false
                        }
                    }
                }
            }
        )
    }

    pendingPlaintextImport?.let { content ->
        val unknownPlatform = stringResource(R.string.settings_auth_unknown_platform)
        val platforms = runCatching {
            JSONObject(content).optJSONArray("accounts")?.let { accounts ->
                (0 until accounts.length()).mapNotNull { accounts.optJSONObject(it)?.optString("platform") }
                    .map(::platformDisplayName)
                    .distinct()
                    .joinToString(context.getString(R.string.settings_auth_platform_separator)) {
                        it.resolve(context)
                    }
            }.orEmpty()
        }.getOrDefault("")
        AlertDialog(
            onDismissRequest = { pendingPlaintextImport = null },
            title = { Text(stringResource(R.string.settings_auth_import_confirm_plaintext_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_auth_import_confirm_plaintext,
                        platforms.ifBlank { unknownPlatform }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPlaintextImport = null
                    scope.launch {
                        val count = runCatching { backupManager.importJson(content) }.getOrElse {
                            SnackbarController.show(UiText.Resource(R.string.settings_auth_import_invalid_backup))
                            return@launch
                        }
                        SnackbarController.show(importResultText(count))
                    }
                }) { Text(stringResource(R.string.settings_auth_import_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlaintextImport = null }) {
                    Text(stringResource(R.string.settings_log_export_cancel))
                }
            }
        )
    }

    // 导出/导入处理中：转圈加载弹窗（PBKDF2 派生密钥耗时较长，避免用户以为界面卡死）
    if (isExporting) OperationLoadingDialog(R.string.settings_auth_export_loading)
    if (isImporting) OperationLoadingDialog(R.string.settings_auth_import_loading)

    // 最大同时下载任务数
    if (showConcurrencyDialog) {
        val options = listOf(1, 2, 3, 5, 8)
        AlertDialog(
            onDismissRequest = { showConcurrencyDialog = false },
            title = { Text(stringResource(R.string.settings_download_concurrency_title)) },
            text = {
                Column {
                    options.forEach { v ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = maxConcurrent == v,
                                onClick = {
                                    maxConcurrent = v
                                    settingsRepo.maxConcurrentDownloads = v
                                    showConcurrencyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.settings_download_concurrency_option, v),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConcurrencyDialog = false }) {
                    Text(stringResource(R.string.settings_dialog_cancel))
                }
            }
        )
    }

    // 下载速度限制：预设档位 + 自定义（KB/s）
    if (showSpeedDialog) {
        val presets = listOf(0L, 1L * 1024 * 1024, 2L * 1024 * 1024, 5L * 1024 * 1024, 10L * 1024 * 1024)
        // 弹窗内临时选择（不立即写设置）：null=未操作，-1=自定义，其余=预设值
        var tempSelected by remember { mutableStateOf<Long?>(null) }
        // 自定义输入：打开时若当前是自定义档位，带出原值（重新打开保留）
        var customKb by remember {
            mutableStateOf(
                if (speedLimitBps > 0 && speedLimitBps !in presets) (speedLimitBps / 1024).toString() else ""
            )
        }
        val effective = tempSelected ?: speedLimitBps
        // 自定义选中态：显式识别「-1=自定义」哨兵；未操作时按当前值是否为自定义档位判断
        val isCustom = when {
            tempSelected == -1L -> true
            tempSelected == null -> speedLimitBps > 0 && speedLimitBps !in presets
            else -> false
        }
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(stringResource(R.string.settings_download_speed_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    presets.forEach { v ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !isCustom && effective == v,
                                onClick = { tempSelected = v }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (v == 0L) {
                                    stringResource(R.string.settings_download_speed_unlimited)
                                } else {
                                    speedLimitText(v)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    // 自定义档位：点击单选即可选中（进入自定义模式）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustom,
                            onClick = {
                                tempSelected = -1L
                                // 当前已是自定义值时带出原值，便于修改
                                if (speedLimitBps > 0 && speedLimitBps !in presets && customKb.isBlank()) {
                                    customKb = (speedLimitBps / 1024).toString()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = customKb,
                            onValueChange = {
                                customKb = it.filter(Char::isDigit).take(6)
                                // 输入即视为选择自定义
                                tempSelected = -1L
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.settings_download_speed_custom)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 以当前选中项为准：选自定义则应用输入；选预设则应用预设值
                        if (isCustom) {
                            val kb = customKb.toLongOrNull()?.coerceAtLeast(1L)
                            if (kb != null) {
                                speedLimitBps = kb * 1024
                                settingsRepo.downloadSpeedLimit = kb * 1024
                            }
                            // 自定义输入为空：保持原值
                        } else if (tempSelected != null) {
                            val v = tempSelected ?: speedLimitBps
                            speedLimitBps = v
                            settingsRepo.downloadSpeedLimit = v
                        }
                        // 未做任何选择：保持当前值
                        showSpeedDialog = false
                    }
                ) { Text(stringResource(R.string.settings_dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(stringResource(R.string.settings_dialog_cancel))
                }
            }
        )
    }

    // 失败自动重试次数
    if (showRetryDialog) {
        val options = listOf(0, 1, 2, 3, 5, 8, 10)
        AlertDialog(
            onDismissRequest = { showRetryDialog = false },
            title = { Text(stringResource(R.string.settings_download_retry_title)) },
            text = {
                Column {
                    options.forEach { v ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = retryCount == v,
                                onClick = {
                                    retryCount = v
                                    settingsRepo.downloadRetryCount = v
                                    showRetryDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (v == 0) {
                                    stringResource(R.string.settings_download_retry_disabled_option)
                                } else {
                                    stringResource(R.string.settings_download_retry_enabled_option, v)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRetryDialog = false }) {
                    Text(stringResource(R.string.settings_dialog_cancel))
                }
            }
        )
    }

    // 锁屏保持下载：引导加入「忽略电池优化」白名单
    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text(stringResource(R.string.settings_battery_optimization_title)) },
            text = {
                Text(
                    text = stringResource(R.string.settings_battery_optimization_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryDialog = false
                        val opened = runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }.isSuccess
                        if (!opened) {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }.onFailure {
                                SnackbarController.show(
                                    UiText.Resource(R.string.settings_battery_optimization_open_failed)
                                )
                            }
                        }
                    }
                ) { Text(stringResource(R.string.download_background_guide_authorize)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text(stringResource(R.string.download_background_guide_not_now))
                }
            }
        )
    }
}

/** 导出网盘认证弹窗：AES 加密密码 + 导出范围（仅已登录 / 全部绑定） */
@Composable
private fun ExportAuthDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String, onlyLoggedIn: Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var onlyLoggedIn by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_auth_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_auth_export_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_auth_backup_password_min_length)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.settings_auth_export_scope),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = onlyLoggedIn,
                        onClick = { onlyLoggedIn = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_auth_export_logged_in), style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !onlyLoggedIn,
                        onClick = { onlyLoggedIn = false }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_auth_export_all), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password, onlyLoggedIn) },
                enabled = password.length >= 12
            ) { Text(stringResource(R.string.settings_auth_export_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_log_export_cancel)) }
        }
    )
}

/** 导入加密备份弹窗：输入解密密码 */
@Composable
private fun ImportAuthDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_auth_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_auth_import_encrypted_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_auth_backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) { Text(stringResource(R.string.settings_auth_decrypt_import)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_log_export_cancel)) }
        }
    )
}

/** 操作处理中弹窗：转圈加载 + 提示文案，禁止关闭（防止中途取消导致导入/导出状态不一致） */
@Composable
private fun OperationLoadingDialog(@StringRes messageRes: Int) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(messageRes)) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    /** 长按回调（隐藏菜单等）；null 时不启用长按 */
    onLongClick: (() -> Unit)? = null,
    /** 自定义尾部内容（如「恢复默认」操作）；null 时显示默认 ChevronRight */
    trailing: @Composable (() -> Unit)? = null
) {
    val shape = MaterialTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/** 线程数单选行（用于弹窗两列布局，每行占半宽） */
@Composable
private fun RadioThreadRow(
    value: Int,
    threads: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = threads == value,
            onClick = { onSelect(value) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$value 线程",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** 速度限制展示文案：0=不限速；>=1MB/s 显示 MB/s，否则 KB/s */
@Composable
private fun speedLimitText(bps: Long): String {
    if (bps <= 0) return stringResource(R.string.settings_download_speed_unlimited)
    return if (bps >= 1024 * 1024) {
        val mb = bps / (1024.0 * 1024.0)
        if (mb >= 10) {
            stringResource(R.string.settings_download_speed_mb_whole, mb)
        } else {
            stringResource(R.string.settings_download_speed_mb_decimal, mb)
        }
    } else {
        stringResource(R.string.settings_download_speed_kb, bps / 1024)
    }
}

private fun platformDisplayName(platform: String): UiText = when (platform) {
    DownloadPlatform.QUARK -> UiText.Resource(R.string.platform_quark)
    DownloadPlatform.UC -> UiText.Resource(R.string.platform_uc)
    DownloadPlatform.XUNLEI -> UiText.Resource(R.string.platform_xunlei)
    DownloadPlatform.BAIDU -> UiText.Resource(R.string.platform_baidu)
    DownloadPlatform.C139 -> UiText.Resource(R.string.platform_c139)
    DownloadPlatform.PAN123 -> UiText.Resource(R.string.platform_pan123)
    else -> UiText.Resource(R.string.settings_auth_unknown_platform)
}

private fun importResultText(count: Int): UiText = if (count > 0) {
    UiText.Plural(
        R.plurals.settings_auth_imported_accounts,
        count,
        listOf(count)
    )
} else {
    UiText.Resource(R.string.settings_auth_import_no_accounts)
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(packageName) == true
}
