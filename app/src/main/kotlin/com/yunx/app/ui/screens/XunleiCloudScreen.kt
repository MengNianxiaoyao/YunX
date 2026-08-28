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
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.ui.viewmodel.XunleiCloudViewModel

/**
 * 迅雷云盘浏览页（P2-4 第三刀：ActionSheet/RenameDialog/MoveSheet 收敛至共享版，
 * 本文件仅保留迅雷分享弹窗——必带提取码，可自动生成，可自定义 4 位）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XunleiCloudScreen(
    viewModel: XunleiCloudViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    onExit: () -> Unit,
    onDownloadStarted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showRename by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }

    CloudBrowserScreen(
        viewModel = viewModel,
        brandTitle = "迅雷网盘",
        stateAnimatedLabel = "xunleiCloudState",
        scrollBehavior = scrollBehavior,
        onExit = onExit,
        onDownloadStarted = onDownloadStarted,
        modifier = modifier,
        callbacks = CloudBrowserCallbacks(
            ActionSheet = { file, onDismiss, onRename, onMove, onShare, onDelete ->
                CloudActionSheet(
                    file = file,
                    shareDesc = CloudSheetSpec.SHARE_DESC_AUTO,
                    onDownload = { onDismiss(); viewModel.downloadFile() },
                    onDownloadFolder = { onDismiss(); viewModel.downloadFolder() },
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
                        rootDirFallback = "",
                        onMove = { if (viewModel.multiSelectMode) viewModel.moveSelected(it) else viewModel.moveFile(it) },
                        onDismiss = { showMove = false }
                    )
                }
            },
            ShareSheet = {
                if (showShare) {
                    XunleiShareSheet(
                        viewModel = viewModel,
                        onDismiss = { showShare = false }
                    )
                }
            },
            onBatchDownload = { viewModel.downloadSelected() },
            onOpenRename = { showRename = true },
            onOpenMove = { showMove = true },
            onOpenShare = { showShare = true },
            onDeleteSingle = { viewModel.deleteFile() },
            onDeleteBatch = { viewModel.deleteSelected() }
        )
    )
}

/** 分享设置弹窗（迅雷必带提取码；可自动生成，可自定义 4 位） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XunleiShareSheet(
    viewModel: XunleiCloudViewModel,
    onDismiss: () -> Unit
) {
    var expiredType by remember { mutableStateOf(1) }
    var passcode by remember { mutableStateOf("") }
    val expireOptions = listOf(
        "永久有效" to 1,
        "1 天" to 2,
        "7 天" to 3,
        "30 天" to 4
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
                "迅雷分享必须带提取码，可自动生成 4 位（或自定义）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = passcode,
                onValueChange = { passcode = it.take(4).filter { c -> c.isLetterOrDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("提取码（4 位字母数字，可留空）") },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("有效期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                expireOptions.forEach { (name, value) ->
                    FilterChip(
                        selected = expiredType == value,
                        onClick = { expiredType = value },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (viewModel.multiSelectMode) {
                        viewModel.shareSelected(expiredType, passcode)
                    } else {
                        viewModel.shareFile(expiredType, passcode)
                    }
                    onDismiss()
                },
                enabled = passcode.isBlank() || passcode.length == 4,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建分享")
            }
        }
    }
}
