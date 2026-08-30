package com.yunx.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.CrumbBar
import com.yunx.app.ui.resolve.ShareFileRow
import com.yunx.app.ui.viewmodel.BaseCloudViewModel
import com.yunx.app.ui.viewmodel.CloudUiState

/**
 * 云盘文件操作共享弹窗族（P2-4 第三刀）：
 * ActionSheet / RenameDialog / MoveSheet 五家逐字相同（仅 share 描述与根目录 fallback 差异），收敛于此。
 * ShareSheet 差异真实（提取码语义/档位值类型），仍留各平台 Screen。
 */
object CloudSheetSpec {
    /** 分享条目描述：是否为「自动带提取码」平台（139/迅雷） */
    const val SHARE_DESC_AUTO = "生成分享链接（自动带提取码）"
    const val SHARE_DESC_CUSTOM = "生成分享链接（可设提取码/有效期）"
}

/** 文件操作菜单（下载/分享/移动/重命名/删除；P2-4 第三刀共享版） */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CloudActionSheet(
    file: ShareFile,
    shareDesc: String,
    onDownload: () -> Unit,
    onDownloadFolder: (() -> Unit)? = null,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (file.isdir) Icons.AutoMirrored.Outlined.DriveFileMove else Icons.Outlined.Download,
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
                        text = if (file.isdir) "文件夹" else "文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            if (!file.isdir) {
                CloudActionItem(Icons.Outlined.Download, "下载", "使用内置下载功能保存到本机", MaterialTheme.colorScheme.primary, onDownload)
            } else if (onDownloadFolder != null) {
                CloudActionItem(Icons.Outlined.Download, "下载文件夹", "递归下载整个文件夹，保持目录结构", MaterialTheme.colorScheme.primary, onDownloadFolder)
            }
            CloudActionItem(Icons.Outlined.Share, "分享", shareDesc, MaterialTheme.colorScheme.primary, onShare)
            CloudActionItem(Icons.AutoMirrored.Outlined.DriveFileMove, "移动到", "移动到网盘的其他目录", MaterialTheme.colorScheme.primary, onMove)
            CloudActionItem(Icons.Outlined.Edit, "重命名", "修改文件名", MaterialTheme.colorScheme.primary, onRename)
            CloudActionItem(Icons.Outlined.Delete, "删除", "删除到回收站", MaterialTheme.colorScheme.error, onDelete)
        }
    }
}

@Composable
private fun CloudActionItem(
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
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 重命名弹窗（P2-4 第三刀共享版） */
@Composable
fun CloudRenameDialog(
    file: ShareFile,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(file.fname) }
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新文件名") },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    if (name.isNotBlank() && name != file.fname) onRename(name.trim())
                },
                enabled = name.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 移动目录选择弹窗（独立浏览，不影响主列表；P2-4 第三刀共享版）。
 * @param rootDirFallback 根目录标识 fallback（139 "/"、123 "0"、迅雷 ""、百度 "/"）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudMoveSheet(
    viewModel: BaseCloudViewModel,
    rootDirFallback: String,
    onMove: (toDir: String) -> Unit,
    onDismiss: () -> Unit
) {
    val moveState by viewModel.moveUiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.openMoveRoot() }
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
            Text("移动到", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            CrumbBar(
                rootTitle = "根目录",
                pathNames = (moveState as? CloudUiState.Loaded)?.pathNames ?: emptyList(),
                onNavigate = { viewModel.moveNavigateToLevel(it) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 返回上一级：固定在目录区上方（不参与 AnimatedContent 过渡，避免与目录内容交叉叠加）
            if ((moveState as? CloudUiState.Loaded)?.pathNames?.isNotEmpty() == true) {
                BackToParentItem(onClick = { viewModel.moveBack() })
                Spacer(modifier = Modifier.height(4.dp))
            }
            AnimatedContent(
                targetState = moveState,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "cloudMoveState"
            ) { s ->
                when (s) {
                    is CloudUiState.Loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    is CloudUiState.Error -> Box(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                    is CloudUiState.Loaded -> {
                        val dirs = s.files.filter { it.isdir }
                        if (dirs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "当前目录没有子文件夹，可直接移动到此处",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
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
            val dirName = (moveState as? CloudUiState.Loaded)?.pathNames?.lastOrNull() ?: "根目录"
            Button(
                onClick = {
                    val to = (moveState as? CloudUiState.Loaded)?.dir ?: rootDirFallback
                    onMove(to)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("移动到此处（$dirName）")
            }
        }
    }
}

/** 随机生成 4 位提取码（去易混淆字符 0/O/1/I/l，ShareSheet 留空时自动填充用） */
internal fun randomPasscode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    return (1..4).map { chars.random() }.joinToString("")
}
