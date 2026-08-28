package com.yunx.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel

/** 百度非会员限速阈值：>300MB 提示 */
private const val BAIDU_LIMIT_BYTES = 300L * 1024 * 1024

/**
 * 百度网盘云盘浏览页（P2-4 第三刀：ActionSheet/RenameDialog/MoveSheet 收敛至共享版，
 * 本文件仅保留百度分享弹窗（强制 4 位提取码 + 有效期）与 >300MB 限速提示拦截）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaiduCloudScreen(
    viewModel: BaiduCloudViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    onExit: () -> Unit,
    onDownloadStarted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRename by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }

    // 百度非会员 >300MB 限速提示：记住「不再显示」，单文件/批量下载前拦截
    val settingsRepo = remember { SettingsRepository(context) }
    var limitHintDismissed by remember { mutableStateOf(settingsRepo.baiduLimitHintDismissed) }
    var showBaiduLimitDialog by remember { mutableStateOf(false) }
    var pendingBaiduDownload by remember { mutableStateOf<String?>(null) } // "single" / "batch"

    /** 判断是否需要弹限速提示；需要则记录待执行动作并弹窗，否则直接执行 */
    fun maybeShowBaiduLimit(files: List<ShareFile>, action: String, onProceed: () -> Unit) {
        if (!limitHintDismissed && files.any { it.fsize > BAIDU_LIMIT_BYTES }) {
            pendingBaiduDownload = action
            showBaiduLimitDialog = true
        } else {
            onProceed()
        }
    }

    CloudBrowserScreen(
        viewModel = viewModel,
        brandTitle = "百度网盘",
        stateAnimatedLabel = "baiduCloudState",
        scrollBehavior = scrollBehavior,
        onExit = onExit,
        onDownloadStarted = onDownloadStarted,
        modifier = modifier,
        callbacks = CloudBrowserCallbacks(
            ActionSheet = { file, onDismiss, onRename, onMove, onShare, onDelete ->
                CloudActionSheet(
                    file = file,
                    shareDesc = CloudSheetSpec.SHARE_DESC_CUSTOM,
                    onDownload = {
                        onDismiss()
                        maybeShowBaiduLimit(listOf(file), "single") { viewModel.downloadFile() }
                    },
                    onDownloadFolder = {
                        onDismiss()
                        viewModel.downloadFolder()
                    },
                    onRename = onRename,
                    onMove = onMove,
                    onShare = onShare,
                    onDelete = onDelete,
                    onDismiss = onDismiss
                )
            },
            RenameDialog = {
                if (showRename && viewModel.actionFile != null) {
                    CloudRenameDialog(
                        file = viewModel.actionFile!!,
                        onRename = { viewModel.renameFile(it) },
                        onDismiss = { showRename = false }
                    )
                }
            },
            MoveSheet = {
                if (showMove) {
                    CloudMoveSheet(
                        viewModel = viewModel,
                        rootDirFallback = "/",
                        onMove = { if (viewModel.multiSelectMode) viewModel.moveSelected(it) else viewModel.moveFile(it) },
                        onDismiss = { showMove = false }
                    )
                }
            },
            ShareSheet = {
                if (showShare) {
                    BaiduShareSheet(
                        viewModel = viewModel,
                        onDismiss = { showShare = false }
                    )
                }
            },
            onBatchDownload = {
                maybeShowBaiduLimit(viewModel.selected, "batch") { viewModel.downloadSelected() }
            },
            onOpenRename = { showRename = true },
            onOpenMove = { showMove = true },
            onOpenShare = { showShare = true },
            onDeleteSingle = { viewModel.deleteFile() },
            onDeleteBatch = { viewModel.deleteSelected() }
        )
    )

    // 百度非会员 >300MB 限速提示弹窗（可勾选不再显示）
    if (showBaiduLimitDialog) {
        var neverShow by remember { mutableStateOf(limitHintDismissed) }
        AlertDialog(
            onDismissRequest = { showBaiduLimitDialog = false },
            title = { Text("下载大文件提示") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "百度网盘非会员超过 300MB 会被限速，下载速度可能较慢。是否继续下载？",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = neverShow, onCheckedChange = { neverShow = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("不再显示此提示", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        limitHintDismissed = neverShow
                        settingsRepo.baiduLimitHintDismissed = neverShow
                        showBaiduLimitDialog = false
                        when (pendingBaiduDownload) {
                            "single" -> viewModel.downloadFile()
                            "batch" -> viewModel.downloadSelected()
                        }
                        pendingBaiduDownload = null
                    }
                ) { Text("继续下载") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBaiduLimitDialog = false
                    pendingBaiduDownload = null
                }) { Text("取消") }
            }
        )
    }
}

/** 分享设置弹窗（百度必须带 4 位提取码 + 有效期） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaiduShareSheet(
    viewModel: BaiduCloudViewModel,
    onDismiss: () -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(0) }
    val periodOptions = listOf(
        "永久有效" to 0,
        "1 天" to 1,
        "7 天" to 7,
        "30 天" to 30
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
        ) {
            Text("分享文件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "百度分享必须带 4 位提取码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = passcode,
                onValueChange = { passcode = it.take(4).filter { c -> c.isLetterOrDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("提取码（4 位字母数字）") },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("有效期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periodOptions.forEach { (name, value) ->
                    FilterChip(
                        selected = period == value,
                        onClick = { period = value },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (viewModel.multiSelectMode) {
                        viewModel.shareSelected(period, passcode)
                    } else {
                        viewModel.shareFile(period, passcode)
                    }
                    onDismiss()
                },
                enabled = passcode.length == 4,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建分享")
            }
        }
    }
}
