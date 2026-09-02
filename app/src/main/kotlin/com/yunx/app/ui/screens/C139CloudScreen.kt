package com.yunx.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.yunx.app.ui.viewmodel.C139CloudViewModel

/**
 * 139 网盘（和彩云）云盘浏览页（P2-4 第三刀：ActionSheet/RenameDialog/MoveSheet 收敛至共享版，
 * 本文件仅保留 139 分享弹窗——提取码系统自动生成，仅选有效期）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun C139CloudScreen(
    viewModel: C139CloudViewModel,
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
        brandTitle = stringResource(R.string.platform_c139),
        stateAnimatedLabel = "c139CloudState",
        scrollBehavior = scrollBehavior,
        onExit = onExit,
        onDownloadStarted = onDownloadStarted,
        modifier = modifier,
        callbacks = CloudBrowserCallbacks(
            ActionSheet = { file, onDismiss, onRename, onMove, onShare, onDelete ->
                CloudActionSheet(
                    file = file,
                    shareDesc = stringResource(CloudSheetSpec.SHARE_DESC_AUTO),
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
                        rootDirFallback = "/",
                        onMove = { if (viewModel.multiSelectMode) viewModel.moveSelected(it) else viewModel.moveFile(it) },
                        onDismiss = { showMove = false }
                    )
                }
            },
            ShareSheet = {
                if (showShare) {
                    C139ShareSheet(
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

/** 分享设置弹窗（139 提取码系统自动生成，仅选有效期） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun C139ShareSheet(
    viewModel: C139CloudViewModel,
    onDismiss: () -> Unit
) {
    var period by remember { mutableStateOf<Int?>(null) }
    val periodOptions = listOf<Pair<Int, Int?>>(
        R.string.cloud_share_permanent to null,
        R.string.cloud_share_one_day to 1,
        R.string.cloud_share_seven_days to 7,
        R.string.cloud_share_thirty_days to 30
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
        ) {
            Text(stringResource(R.string.cloud_share_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.cloud_share_c139_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.cloud_share_expiration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periodOptions.forEach { (name, value) ->
                    FilterChip(
                        selected = period == value,
                        onClick = { period = value },
                        label = { Text(stringResource(name)) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (viewModel.multiSelectMode) {
                        viewModel.shareSelected(period)
                    } else {
                        viewModel.shareFile(period)
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_share_create))
            }
        }
    }
}
