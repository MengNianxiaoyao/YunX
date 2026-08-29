package com.yunx.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.components.ScrollToTopButton
import com.yunx.app.ui.items.MultiSelectAction
import com.yunx.app.ui.items.MultiSelectBar
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.CrumbBar
import com.yunx.app.ui.resolve.ShareFileRow
import com.yunx.app.ui.viewmodel.BaseCloudViewModel
import com.yunx.app.ui.viewmodel.CloudUiState

/**
 * 云盘浏览页共享骨架（P2-4 第二刀）：6 个平台 Screen 的列表/多选/面包屑/刷新/加载更多收敛于此。
 *
 * 平台差异经 [CloudBrowserCallbacks] 注入（操作菜单/重命名/移动/分享弹窗仍是各平台的，
 * 因为 ShareSheet 的提取码/有效期档位差异大；本组件负责列表区与公共对话框：删除确认/处理中/分享结果）。
 *
 * @param brandTitle 平台名（标题/面包屑根名，如 "139网盘"）
 * @param stateAnimatedLabel AnimatedContent label（各平台保持唯一即可）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBrowserScreen(
    viewModel: BaseCloudViewModel,
    brandTitle: String,
    stateAnimatedLabel: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onExit: () -> Unit,
    onDownloadStarted: () -> Unit = {},
    modifier: Modifier = Modifier,
    callbacks: CloudBrowserCallbacks
) {
    val state by viewModel.uiState.collectAsState()
    // 系统返回键 → 子目录返回上一级，根目录返回账号列表（对齐解析页返回行为）
    BackHandler {
        val s = state
        if (s is CloudUiState.Loaded && s.pathNames.isNotEmpty()) viewModel.back() else onExit()
    }
    // 文件列表滚动状态（返回顶部按钮用）
    val listState = rememberLazyListState()
    var showActionSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.cloudMessage) {
        viewModel.cloudMessage?.let {
            SnackbarController.show(it)
            viewModel.consumeMessage()
        }
    }

    // 下载入队后切到下载页（一次性消费）
    LaunchedEffect(viewModel.downloadTriggered) {
        if (viewModel.downloadTriggered > 0) {
            viewModel.consumeDownloadTriggered()
            onDownloadStarted()
        }
    }

    // 首次登录后进入：VM 在 MainScreen 组合时即创建并 loadRoot()，当时未登录会停在 Error 态；
    // 登录完成后进入本页补一次加载（refresh 对非 Loaded 态走 loadRoot）。Loaded 态不重复请求。
    LaunchedEffect(Unit) {
        if (viewModel.uiState.value is CloudUiState.Error) viewModel.refresh()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(140))
            },
            label = stateAnimatedLabel
        ) { s ->
            when (s) {
                is CloudUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is CloudUiState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onExit) { Text("返回") }
                            TextButton(onClick = { viewModel.loadRoot() }) { Text("重试") }
                        }
                    }
                }

                is CloudUiState.Loaded -> Box(modifier = Modifier.fillMaxSize()) {
                    PullToRefreshBox(
                        isRefreshing = viewModel.refreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 16.dp,
                                bottom = if (viewModel.multiSelectMode) 96.dp else 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (viewModel.multiSelectMode) {
                                            IconButton(onClick = { viewModel.exitMultiSelect() }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "取消选择")
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "已选 ${viewModel.selected.size} 项",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = if (viewModel.selected.size == s.files.size) "已全选" else "点击选择更多文件",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            TextButton(onClick = { viewModel.toggleSelectAll(s.files) }) {
                                                Text(if (viewModel.selected.size == s.files.size) "取消全选" else "全选")
                                            }
                                        } else {
                                            IconButton(onClick = onExit) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = brandTitle,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "共 ${s.files.size} 项",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    if (!viewModel.multiSelectMode) {
                                        CrumbBar(
                                            rootTitle = brandTitle,
                                            pathNames = s.pathNames,
                                            onNavigate = { viewModel.navigateToLevel(it) }
                                        )
                                    }
                                }
                            }

                            if (s.pathNames.isNotEmpty()) {
                                item {
                                    BackToParentItem(onClick = { viewModel.back() })
                                }
                            }

                            if (s.files.isEmpty()) {
                                item {
                                    Text(
                                        text = "此目录为空",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            items(s.files, key = { it.fid }) { file ->
                                ShareFileRow(
                                    file = file,
                                    modifier = Modifier.animateItem(),
                                    onClick = {
                                        if (viewModel.multiSelectMode) {
                                            viewModel.toggleSelect(file)
                                        } else if (file.isdir) {
                                            viewModel.openFolder(file)
                                        } else {
                                            viewModel.openActions(file)
                                            showActionSheet = true
                                        }
                                    },
                                    onMore = if (!viewModel.multiSelectMode && file.isdir) {
                                        {
                                            viewModel.openActions(file)
                                            showActionSheet = true
                                        }
                                    } else {
                                        null
                                    },
                                    onLongClick = if (!viewModel.multiSelectMode) {
                                        { viewModel.enterMultiSelect(file) }
                                    } else {
                                        null
                                    },
                                    selected = viewModel.selected.contains(file),
                                    showCheckbox = viewModel.multiSelectMode
                                )
                            }
                            if (s.hasMore) {
                                item { CloudLoadMoreItem(viewModel.isLoadingMore) { viewModel.loadMore() } }
                            }
                        }
                    }

                    // 返回顶部按钮（上滑离开顶部后显示；多选模式下上移避开底部批量栏）
                    ScrollToTopButton(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 16.dp,
                                bottom = if (viewModel.multiSelectMode) 104.dp else 16.dp
                            )
                    )

                    AnimatedVisibility(
                        visible = viewModel.multiSelectMode,
                        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        MultiSelectBar(
                            count = viewModel.selected.size,
                            actions = listOf(
                                MultiSelectAction("下载", Icons.Outlined.Download, MaterialTheme.colorScheme.primary) {
                                    callbacks.onBatchDownload()
                                },
                                MultiSelectAction("分享", Icons.Outlined.Share, MaterialTheme.colorScheme.primary) {
                                    callbacks.onOpenShare()
                                },
                                MultiSelectAction("移动", Icons.AutoMirrored.Outlined.DriveFileMove, MaterialTheme.colorScheme.primary) {
                                    viewModel.openMoveRoot()
                                    callbacks.onOpenMove()
                                },
                                MultiSelectAction("删除", Icons.Outlined.Delete, MaterialTheme.colorScheme.error) {
                                    showDeleteConfirm = true
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    // 文件操作菜单（平台各自实现：ShareSheet 差异大）
    if (showActionSheet && viewModel.actionFile != null) {
        // 函数类型调用不支持命名参数，按声明顺序位置传参：
        // (file, onDismiss, onRename, onMove, onShare, onDelete)
        callbacks.ActionSheet(
            viewModel.actionFile!!,
            {
                showActionSheet = false
                viewModel.dismissActions()
            },
            { showActionSheet = false; callbacks.onOpenRename() },
            { showActionSheet = false; viewModel.openMoveRoot(); callbacks.onOpenMove() },
            { showActionSheet = false; callbacks.onOpenShare() },
            { showActionSheet = false; showDeleteConfirm = true }
        )
    }

    // 平台弹窗族（各自持有显示状态；骨架只负责在意图点回调）
    callbacks.RenameDialog()
    callbacks.MoveSheet()
    callbacks.ShareSheet()

    viewModel.shareResult?.let { info ->
        ShareResultDialog(
            info = info,
            onDismiss = { viewModel.dismissShareResult() }
        )
    }

    if (showDeleteConfirm) {
        val isBatch = viewModel.multiSelectMode
        val deleting = if (isBatch) "选中的 ${viewModel.selected.size} 项" else "「${viewModel.actionFile?.fname ?: ""}」"
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除文件") },
            text = { Text("确定要删除$deleting 吗？删除后进入回收站。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        if (isBatch) callbacks.onDeleteBatch() else callbacks.onDeleteSingle()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    // 操作执行中加载弹窗（下载文件夹/批量下载显示进度）
    if (viewModel.isOperating) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDownload() }) {
                    Text("中断", color = MaterialTheme.colorScheme.error)
                }
            },
            title = { Text("处理中") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = viewModel.folderProgress ?: "正在处理，请稍候…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }
}

/** 平台差异注入点（P2-4 第二刀）：弹窗族由各平台 Screen 提供，各自持有显示状态 */
class CloudBrowserCallbacks(
    /** 文件操作菜单（下载/分享/移动/重命名/删除条目；平台自绘） */
    val ActionSheet: @Composable (
        file: ShareFile,
        onDismiss: () -> Unit,
        onRename: () -> Unit,
        onMove: () -> Unit,
        onShare: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit,
    /** 重命名弹窗（平台自持显示状态） */
    val RenameDialog: @Composable () -> Unit,
    /** 移动弹窗（平台自持显示状态） */
    val MoveSheet: @Composable () -> Unit,
    /** 分享弹窗（平台自持显示状态） */
    val ShareSheet: @Composable () -> Unit,
    /** 批量下载入口（百度有 >300MB 限速拦截） */
    val onBatchDownload: () -> Unit,
    /** 打开重命名（记录意图） */
    val onOpenRename: () -> Unit,
    /** 打开移动（已 openMoveRoot） */
    val onOpenMove: () -> Unit,
    /** 打开分享 */
    val onOpenShare: () -> Unit,
    /** 删除单个文件（删除确认对话框确认后回调） */
    val onDeleteSingle: () -> Unit,
    /** 批量删除选中（删除确认对话框确认后回调） */
    val onDeleteBatch: () -> Unit
)
