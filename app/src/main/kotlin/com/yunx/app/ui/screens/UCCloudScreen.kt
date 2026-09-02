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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.R
import com.yunx.app.ui.viewmodel.UCCloudViewModel

/**
 * UC 网盘云盘浏览页（P2-4 第三刀：ActionSheet/RenameDialog/MoveSheet 收敛至共享版，
 * 本文件仅保留 UC 分享弹窗——可选提取码 + 有效期；urlType 2=带码 1=公开）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UCCloudScreen(
    viewModel: UCCloudViewModel,
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
        brandTitle = stringResource(R.string.platform_uc),
        stateAnimatedLabel = "ucCloudState",
        scrollBehavior = scrollBehavior,
        onExit = onExit,
        onDownloadStarted = onDownloadStarted,
        modifier = modifier,
        callbacks = CloudBrowserCallbacks(
            ActionSheet = { file, onDismiss, onRename, onMove, onShare, onDelete ->
                CloudActionSheet(
                    file = file,
                    shareDesc = stringResource(CloudSheetSpec.SHARE_DESC_CUSTOM),
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
                        rootDirFallback = "0",
                        onMove = { if (viewModel.multiSelectMode) viewModel.moveSelected(it) else viewModel.moveFile(it) },
                        onDismiss = { showMove = false }
                    )
                }
            },
            ShareSheet = {
                if (showShare) {
                    UCShareSheet(
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

/** 分享设置弹窗（可选提取码 + 有效期；urlType 2=带码 1=公开） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UCShareSheet(
    viewModel: UCCloudViewModel,
    onDismiss: () -> Unit
) {
    var withPassword by remember { mutableStateOf(false) }
    var passcode by remember { mutableStateOf("") }
    var expiredType by remember { mutableStateOf(1) }
    val expireOptions = listOf(R.string.cloud_share_permanent to 1, R.string.cloud_share_one_day to 2, R.string.cloud_share_seven_days to 3, R.string.cloud_share_thirty_days to 4)
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
            Text(stringResource(R.string.cloud_share_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.cloud_share_passcode), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !withPassword,
                    onClick = { withPassword = false },
                    label = { Text(stringResource(R.string.cloud_share_no_passcode)) },
                    colors = FilterChipDefaults.filterChipColors()
                )
                FilterChip(
                    selected = withPassword,
                    onClick = {
                        withPassword = true
                        if (passcode.isBlank()) passcode = randomPasscode()
                    },
                    label = { Text(stringResource(R.string.cloud_share_set_passcode)) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
            if (withPassword) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it.take(4).filter { c -> c.isLetterOrDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cloud_share_passcode_four_digits)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.cloud_share_expiration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                expireOptions.forEach { (name, value) ->
                    FilterChip(
                        selected = expiredType == value,
                        onClick = { expiredType = value },
                        label = { Text(stringResource(name)) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (viewModel.multiSelectMode) {
                        viewModel.shareSelected(
                            urlType = if (withPassword) 2 else 1,
                            passcode = passcode,
                            expiredType = expiredType
                        )
                    } else {
                        viewModel.shareFile(
                            urlType = if (withPassword) 2 else 1,
                            passcode = passcode,
                            expiredType = expiredType
                        )
                    }
                    onDismiss()
                },
                enabled = !withPassword || passcode.length == 4,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_share_create))
            }
        }
    }
}
