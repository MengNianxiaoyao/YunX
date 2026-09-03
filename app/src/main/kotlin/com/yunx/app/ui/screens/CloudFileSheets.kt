package com.yunx.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.R
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.CrumbBar
import com.yunx.app.ui.resolve.ShareFileRow
import com.yunx.app.ui.viewmodel.CloudUiState
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel

/** 文件操作菜单类型（FileActionSheet 内切换） */
private enum class ActionStep { MENU, MOVE, SHARE, RENAME, DELETE }

/** 有效期选项：名称资源 + expired_type 值 */
private val expireOptions = listOf(
    R.string.cloud_share_permanent to 1,
    R.string.cloud_share_one_day to 2,
    R.string.cloud_share_seven_days to 3,
    R.string.cloud_share_thirty_days to 4
)

/**
 * 夸克云盘文件操作弹窗：更多按钮 → 操作菜单（下载/分享/移动/重命名/删除），
 * 内部按步骤切换：移动选目录 / 分享设置 / 重命名输入 / 删除确认。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileActionSheet(
    file: ShareFile,
    viewModel: QuarkCloudViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(ActionStep.MENU) }
    // 移动目标浏览用独立状态（moveUiState），不影响主列表
    val moveState by viewModel.moveUiState.collectAsState()
    val operating = viewModel.isOperating

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (!operating) onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        when (step) {
            ActionStep.MENU -> ActionMenu(
                file = file,
                onDownload = {
                    viewModel.downloadFile()
                    onDismiss()
                },
                onDownloadFolder = {
                    viewModel.downloadFolder()
                    onDismiss()
                },
                onShare = { step = ActionStep.SHARE },
                onMove = {
                    viewModel.openMoveRoot()
                    step = ActionStep.MOVE
                },
                onRename = { step = ActionStep.RENAME },
                onDelete = { step = ActionStep.DELETE }
            )

            ActionStep.MOVE -> MoveStep(
                file = file,
                viewModel = viewModel,
                moveState = moveState,
                operating = operating,
                onBack = { step = ActionStep.MENU },
                onDone = onDismiss
            )

            ActionStep.SHARE -> ShareStep(
                file = file,
                viewModel = viewModel,
                operating = operating,
                onBack = { step = ActionStep.MENU }
            )

            ActionStep.RENAME -> RenameStep(
                file = file,
                viewModel = viewModel,
                operating = operating,
                onBack = { step = ActionStep.MENU },
                onDone = onDismiss
            )

            ActionStep.DELETE -> DeleteStep(
                file = file,
                viewModel = viewModel,
                operating = operating,
                onBack = { step = ActionStep.MENU },
                onDone = onDismiss
            )
        }
    }

    // 分享创建成功：展示链接与提取码（可复制）
    viewModel.shareResult?.let { info ->
        ShareResultDialog(
            info = info,
            onDismiss = { viewModel.dismissShareResult() }
        )
    }
}

/** 操作菜单主界面 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionMenu(
    file: ShareFile,
    onDownload: () -> Unit,
    onDownloadFolder: (() -> Unit)? = null,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        // 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (file.isdir) Icons.Outlined.Folder else Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fname,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = if (file.isdir) stringResource(R.string.cloud_action_file_type_folder) else stringResource(R.string.cloud_file_type_file),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // 操作项
        if (!file.isdir) {
            ActionItem(
                icon = Icons.Outlined.Download,
                title = stringResource(R.string.resolve_action_download),
                desc = stringResource(R.string.cloud_action_download_desc),
                tint = MaterialTheme.colorScheme.primary,
                onClick = onDownload
            )
        } else if (onDownloadFolder != null) {
            ActionItem(
                icon = Icons.Outlined.Download,
                title = stringResource(R.string.cloud_action_download_folder),
                desc = stringResource(R.string.cloud_action_download_folder_desc),
                tint = MaterialTheme.colorScheme.primary,
                onClick = onDownloadFolder
            )
        }
        ActionItem(
            icon = Icons.Outlined.Share,
            title = stringResource(R.string.cloud_action_share),
            desc = stringResource(R.string.cloud_action_share_desc_custom),
            tint = MaterialTheme.colorScheme.primary,
            onClick = onShare
        )
        ActionItem(
            icon = Icons.AutoMirrored.Outlined.DriveFileMove,
            title = stringResource(R.string.cloud_action_move_to),
            desc = stringResource(R.string.cloud_action_move_to_desc),
            tint = MaterialTheme.colorScheme.primary,
            onClick = onMove
        )
        ActionItem(
            icon = Icons.Outlined.Edit,
            title = stringResource(R.string.cloud_action_rename),
            desc = stringResource(R.string.cloud_action_rename_desc),
            tint = MaterialTheme.colorScheme.primary,
            onClick = onRename
        )
        ActionItem(
            icon = Icons.Outlined.Delete,
            title = stringResource(R.string.cloud_action_delete),
            desc = stringResource(R.string.cloud_action_delete_desc_recycle),
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete
        )
    }
}

/** 操作项行 */
@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = MaterialTheme.shapes.large,
            color = tint.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 移动：浏览目标目录并确认（独立浏览状态 moveUiState，不影响主列表） */
@Composable
private fun MoveStep(
    file: ShareFile,
    viewModel: QuarkCloudViewModel,
    moveState: CloudUiState,
    operating: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        StepHeader(title = stringResource(R.string.cloud_action_move_title), subtitle = file.fname, onBack = onBack)

        Spacer(modifier = Modifier.height(8.dp))
        CrumbBar(
            rootTitle = stringResource(R.string.resolve_root_directory),
            pathNames = (moveState as? CloudUiState.Loaded)?.pathNames ?: emptyList(),
            onNavigate = { viewModel.moveNavigateToLevel(it) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 返回上一级：固定在目录区上方（不参与 AnimatedContent 过渡，避免与目录内容交叉叠加）
        if ((moveState as? CloudUiState.Loaded)?.pathNames?.isNotEmpty() == true) {
            BackToParentItem(onClick = { viewModel.moveBack() })
            Spacer(modifier = Modifier.height(4.dp))
        }
        // 移动目录切换：淡入过渡
        AnimatedContent(
            targetState = moveState,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "moveState"
        ) { s ->
            when (s) {
                is CloudUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is CloudUiState.Error -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) { Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                is CloudUiState.Loaded -> {
                    val dirs = s.files.filter { it.isdir }
                if (dirs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.cloud_action_move_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(dirs, key = { it.fid }) { dir ->
                            ShareFileRow(file = dir, onClick = { viewModel.openMoveFolder(dir) })
                        }
                    }
                }
            }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        val dirName = (moveState as? CloudUiState.Loaded)?.pathNames?.lastOrNull() ?: stringResource(R.string.resolve_root_directory)
        Button(
            onClick = {
                val to = (moveState as? CloudUiState.Loaded)?.dir ?: "0"
                viewModel.moveFile(to)
                onDone()
            },
            enabled = !operating,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (operating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_action_move_to_here, dirName))
            }
        }
    }
}

/** 分享：提取码 + 有效期设置 */
@Composable
private fun ShareStep(
    file: ShareFile,
    viewModel: QuarkCloudViewModel,
    operating: Boolean,
    onBack: () -> Unit
) {
    var withPassword by remember { mutableStateOf(false) }
    var passcode by remember { mutableStateOf("") }
    var expiredType by remember { mutableStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        StepHeader(title = stringResource(R.string.cloud_share_title), subtitle = file.fname, onBack = onBack)

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
            expireOptions.forEach { (nameRes, value) ->
                FilterChip(
                    selected = expiredType == value,
                    onClick = { expiredType = value },
                    label = { Text(stringResource(nameRes)) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.shareFile(
                    urlType = if (withPassword) 2 else 1,
                    passcode = passcode,
                    expiredType = expiredType
                )
                // 不在此关闭：保留弹窗，等 shareResult 弹出分享结果
            },
            enabled = !operating && (!withPassword || passcode.length == 4),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (operating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_share_create))
            }
        }
    }
}

/** 重命名输入 */
@Composable
private fun RenameStep(
    file: ShareFile,
    viewModel: QuarkCloudViewModel,
    operating: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    var name by remember { mutableStateOf(file.fname) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        StepHeader(title = stringResource(R.string.cloud_action_rename_title), subtitle = file.fname, onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.cloud_action_new_filename)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (name.isNotBlank() && name != file.fname) {
                    viewModel.renameFile(name.trim())
                    onDone()
                } else {
                    onBack()
                }
            },
            enabled = !operating && name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(stringResource(R.string.cloud_action_confirm_rename))
        }
    }
}

/** 删除确认 */
@Composable
private fun DeleteStep(
    file: ShareFile,
    viewModel: QuarkCloudViewModel,
    operating: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!operating) onBack() },
        title = { Text(stringResource(R.string.cloud_delete_file_title)) },
        text = { Text(stringResource(R.string.cloud_delete_single_confirmation, file.fname)) },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.deleteFile()
                    onDone()
                },
                enabled = !operating
            ) {
                Text(stringResource(R.string.cloud_action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.cloud_action_cancel)) }
        }
    )
}

/** 分享结果：链接 + 提取码 + 复制 */
@Composable
internal fun ShareResultDialog(
    info: com.yunx.app.data.network.model.ShareInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Dialog 内提示宿主（AlertDialog 为独立窗口）
    val snackbarHostState = rememberGlobalSnackbarHostState()
    // 拼接分享文案（按平台区分：139 / 123 / UC / 迅雷 / 百度 / 夸克）
    val platformNameRes = when {
        info.shareUrl.contains("139.com") -> R.string.platform_c139
        info.shareUrl.contains("123pan") || info.shareUrl.contains("123865") -> R.string.platform_pan123
        info.shareUrl.contains("uc.cn") -> R.string.platform_uc
        info.shareUrl.contains("xunlei.com") -> R.string.platform_xunlei
        info.shareUrl.contains("baidu.com") -> R.string.platform_baidu
        else -> R.string.platform_quark
    }
    val platformName = stringResource(platformNameRes)
    val shareCopiedHint = stringResource(R.string.cloud_share_text_copied)
    val sharePrefix = stringResource(R.string.cloud_share_result_prefix, platformName, info.title)
    val shareLink = stringResource(R.string.cloud_share_result_link, info.shareUrl)
    val sharePasscode = if (info.passcode.isNotBlank()) {
        stringResource(R.string.cloud_share_result_passcode, info.passcode)
    } else {
        ""
    }
    val shareText = buildString {
        append(sharePrefix)
        append(shareLink)
        append(sharePasscode)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_share_success_title)) },
        text = {
            Column {
                // 等宽展示分享文案，便于整段复制
                Text(
                    text = shareText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.cloud_share_expiration_value, expireLabel(info.expiredType)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Dialog 内提示（AlertDialog 为独立窗口，需自带 Snackbar 宿主）
                SnackbarHost(hostState = snackbarHostState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("share_text", shareText))
                    SnackbarController.show(shareCopiedHint)
                }
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.cloud_action_copy_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cloud_action_done)) }
        }
    )
}

/** 步骤头部：返回按钮 + 标题 */
@Composable
private fun StepHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cloud_action_back)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun expireLabel(type: Int): String = when (type) {
    2 -> stringResource(R.string.cloud_share_one_day)
    3 -> stringResource(R.string.cloud_share_seven_days)
    4 -> stringResource(R.string.cloud_share_thirty_days)
    else -> stringResource(R.string.cloud_share_permanent)
}

/** 批量操作步骤类型 */
internal enum class BatchStep { MENU, SHARE, MOVE, DELETE }

/**
 * 批量操作弹窗（长按多选后）：下载 / 分享 / 移动 / 删除。
 * 分享/移动/删除复用与单文件一致的表单与独立目录浏览。
 * @param initialStep 初始步骤（底部栏点击下载/删除直接执行，分享/移动传入对应步骤）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchActionSheet(
    viewModel: QuarkCloudViewModel,
    onDismiss: () -> Unit,
    initialStep: BatchStep = BatchStep.MENU
) {
    var step by remember { mutableStateOf(initialStep) }
    val moveState by viewModel.moveUiState.collectAsState()
    val operating = viewModel.isOperating
    val count = viewModel.selected.size

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (!operating) onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        when (step) {
            BatchStep.MENU -> BatchMenu(
                count = count,
                onDownload = {
                    viewModel.downloadSelected()
                    onDismiss()
                },
                onShare = { step = BatchStep.SHARE },
                onMove = {
                    viewModel.openMoveRoot()
                    step = BatchStep.MOVE
                },
                onDelete = { step = BatchStep.DELETE }
            )

            BatchStep.SHARE -> BatchShareStep(
                count = count,
                viewModel = viewModel,
                operating = operating,
                onBack = { step = BatchStep.MENU }
            )

            BatchStep.MOVE -> BatchMoveStep(
                count = count,
                viewModel = viewModel,
                moveState = moveState,
                operating = operating,
                onBack = { step = BatchStep.MENU },
                onDone = onDismiss
            )

            BatchStep.DELETE -> BatchDeleteStep(
                count = count,
                viewModel = viewModel,
                operating = operating,
                onBack = { step = BatchStep.MENU },
                onDone = onDismiss
            )
        }
    }

    // 分享创建成功：展示链接与提取码（保留弹窗以正常显示）
    viewModel.shareResult?.let { info ->
        ShareResultDialog(
            info = info,
            onDismiss = { viewModel.dismissShareResult() }
        )
    }
}

/** 批量操作菜单主界面 */
@Composable
private fun BatchMenu(
    count: Int,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        // 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cloud_batch_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = pluralStringResource(R.plurals.cloud_selected_count, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        ActionItem(
            icon = Icons.Outlined.Download,
            title = stringResource(R.string.resolve_action_download),
            desc = stringResource(R.string.cloud_batch_download_desc),
            tint = MaterialTheme.colorScheme.primary,
            onClick = onDownload
        )
        ActionItem(
            icon = Icons.Outlined.Share,
            title = stringResource(R.string.cloud_action_share),
            desc = stringResource(R.string.cloud_batch_share_desc),
            tint = MaterialTheme.colorScheme.primary,
            onClick = onShare
        )
        ActionItem(
            icon = Icons.AutoMirrored.Outlined.DriveFileMove,
            title = stringResource(R.string.cloud_action_move_to),
            desc = stringResource(R.string.cloud_batch_move_desc),
            tint = MaterialTheme.colorScheme.primary,
            onClick = onMove
        )
        ActionItem(
            icon = Icons.Outlined.Delete,
            title = stringResource(R.string.cloud_action_delete),
            desc = stringResource(R.string.cloud_batch_delete_desc),
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete
        )
    }
}

/** 批量分享：提取码 + 有效期 */
@Composable
private fun BatchShareStep(
    count: Int,
    viewModel: QuarkCloudViewModel,
    operating: Boolean,
    onBack: () -> Unit
) {
    var withPassword by remember { mutableStateOf(false) }
    var passcode by remember { mutableStateOf("") }
    var expiredType by remember { mutableStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        StepHeader(title = stringResource(R.string.cloud_share_title), subtitle = pluralStringResource(R.plurals.cloud_selected_count, count, count), onBack = onBack)

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
            expireOptions.forEach { (nameRes, value) ->
                FilterChip(
                    selected = expiredType == value,
                    onClick = { expiredType = value },
                    label = { Text(stringResource(nameRes)) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.shareSelected(
                    urlType = if (withPassword) 2 else 1,
                    passcode = passcode,
                    expiredType = expiredType
                )
                // 不关闭：等 shareResult 弹出分享结果
            },
            enabled = !operating && (!withPassword || passcode.length == 4),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (operating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_share_create))
            }
        }
    }
}

/** 批量移动：浏览目标目录并确认 */
@Composable
private fun BatchMoveStep(
    count: Int,
    viewModel: QuarkCloudViewModel,
    moveState: CloudUiState,
    operating: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    // 首次进入该步骤：加载移动目标根目录（否则 moveUiState 停留在 Loading 一直转圈）
    LaunchedEffect(Unit) {
        viewModel.openMoveRoot()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        StepHeader(title = stringResource(R.string.cloud_action_move_title), subtitle = pluralStringResource(R.plurals.cloud_selected_count, count, count), onBack = onBack)

        Spacer(modifier = Modifier.height(8.dp))
        CrumbBar(
            rootTitle = stringResource(R.string.resolve_root_directory),
            pathNames = (moveState as? CloudUiState.Loaded)?.pathNames ?: emptyList(),
            onNavigate = { viewModel.moveNavigateToLevel(it) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 返回上一级：固定在目录区上方（不参与 AnimatedContent 过渡，避免与目录内容交叉叠加）
        if ((moveState as? CloudUiState.Loaded)?.pathNames?.isNotEmpty() == true) {
            BackToParentItem(onClick = { viewModel.moveBack() })
            Spacer(modifier = Modifier.height(4.dp))
        }
        // 移动目录切换：淡入过渡
        AnimatedContent(
            targetState = moveState,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "batchMoveState"
        ) { s ->
            when (s) {
                is CloudUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is CloudUiState.Error -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) { Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                is CloudUiState.Loaded -> {
                    val dirs = s.files.filter { it.isdir }
                if (dirs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.cloud_action_move_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(dirs, key = { it.fid }) { dir ->
                            ShareFileRow(file = dir, onClick = { viewModel.openMoveFolder(dir) })
                        }
                    }
                }
            }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        val dirName = (moveState as? CloudUiState.Loaded)?.pathNames?.lastOrNull() ?: stringResource(R.string.resolve_root_directory)
        Button(
            onClick = {
                val to = (moveState as? CloudUiState.Loaded)?.dir ?: "0"
                viewModel.moveSelected(to)
                onDone()
            },
            enabled = !operating,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (operating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_action_move_to_here, dirName))
            }
        }
    }
}

/** 批量删除确认 */
@Composable
private fun BatchDeleteStep(
    count: Int,
    viewModel: QuarkCloudViewModel,
    operating: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!operating) onBack() },
        title = { Text(stringResource(R.string.cloud_delete_file_title)) },
        text = { Text(stringResource(R.string.cloud_drive_delete_confirmation, count)) },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.deleteSelected()
                    onDone()
                },
                enabled = !operating
            ) {
                Text(stringResource(R.string.cloud_action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.cloud_action_cancel)) }
        }
    )
}
