package com.yunx.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.R
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.QuarkAccountEntity
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.data.db.XunleiAccountEntity
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel
import com.yunx.app.ui.viewmodel.C139CloudViewModel
import com.yunx.app.ui.viewmodel.DriveQuotaViewModel
import com.yunx.app.ui.viewmodel.Pan123CloudViewModel
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel
import com.yunx.app.ui.viewmodel.UCCloudViewModel
import com.yunx.app.ui.viewmodel.XunleiCloudViewModel

/**
 * 网盘账号展示模型。
 */
private data class DriveAccount(
    val id: String,
    val name: String,
    val description: String,
    val avatarText: String,
    val isLoggedIn: Boolean = false,
    /** 登录态已失效（invalidAt > 0）：卡片显示过期提示，点击跳登录页 */
    val expired: Boolean = false,
    /** 前置风险披露（P1-7）：未登录描述以警示色显示风险警告 */
    val riskWarning: Boolean = false
)

/**
 * 网盘页：
 * - 夸克未登录：点击进入登录页；
 * - 夸克已登录：副标题显示昵称，点击弹出账号信息底部弹窗（可查看 Cookie / 退出登录）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    scrollBehavior: TopAppBarScrollBehavior,
    quarkAccount: QuarkAccountEntity?,
    ucAccount: UCAccountEntity?,
    xunleiAccount: XunleiAccountEntity?,
    baiduAccount: BaiduAccountEntity?,
    c139Account: C139AccountEntity?,
    pan123Account: Pan123AccountEntity?,
    /** 夸克云盘浏览 ViewModel（网盘 Tab 内切换展示，非全屏） */
    quarkCloudViewModel: QuarkCloudViewModel,
    /** UC 网盘云盘浏览 ViewModel */
    ucCloudViewModel: UCCloudViewModel,
    /** 迅雷网盘云盘浏览 ViewModel */
    xunleiCloudViewModel: XunleiCloudViewModel,
    /** 百度网盘云盘浏览 ViewModel */
    baiduCloudViewModel: BaiduCloudViewModel,
    /** 139 网盘云盘浏览 ViewModel */
    c139CloudViewModel: C139CloudViewModel,
    /** 123 云盘浏览 ViewModel */
    pan123CloudViewModel: Pan123CloudViewModel,
    /** 网盘空间详情 ViewModel（顶部空间总览） */
    driveQuotaViewModel: DriveQuotaViewModel,
    onQuarkLogin: () -> Unit,
    onQuarkLogout: () -> Unit,
    /** 夸克云盘下载入队后切换到「下载」Tab */
    onDownloadStarted: () -> Unit = {},
    onUCLogin: () -> Unit,
    onUCLogout: () -> Unit,
    onXunleiLogin: () -> Unit,
    onXunleiLogout: () -> Unit,
    onBaiduLogin: () -> Unit,
    onBaiduLogout: () -> Unit,
    onC139Login: () -> Unit,
    onC139Logout: () -> Unit,
    onPan123Login: () -> Unit,
    onPan123Logout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showQuarkSheet by remember { mutableStateOf(false) }
    var showUCSheet by remember { mutableStateOf(false) }
    var showXunleiSheet by remember { mutableStateOf(false) }
    var showBaiduSheet by remember { mutableStateOf(false) }
    var showC139Sheet by remember { mutableStateOf(false) }
    var showPan123Sheet by remember { mutableStateOf(false) }
    // 夸克云盘浏览：网盘 Tab 内切换（非全屏），切 Tab 再回来仍保留
    var showCloud by rememberSaveable { mutableStateOf(false) }
    // UC 网盘云盘浏览：网盘 Tab 内切换（非全屏）
    var showUCCloud by rememberSaveable { mutableStateOf(false) }
    // 迅雷网盘云盘浏览：网盘 Tab 内切换（非全屏）
    var showXunleiCloud by rememberSaveable { mutableStateOf(false) }
    // 百度网盘云盘浏览：网盘 Tab 内切换（非全屏）
    var showBaiduCloud by rememberSaveable { mutableStateOf(false) }
    // 139 网盘云盘浏览：网盘 Tab 内切换（非全屏）
    var showC139Cloud by rememberSaveable { mutableStateOf(false) }
    // 123 云盘浏览：网盘 Tab 内切换（非全屏）
    var showPan123Cloud by rememberSaveable { mutableStateOf(false) }

    // 夸克：登录态由数据库驱动；已登录则副标题显示昵称（失效则显示重新登录提示）
    val quarkExpired = (quarkAccount?.invalidAt ?: 0L) > 0L
    val quark = DriveAccount(
        id = "quark",
        name = stringResource(R.string.platform_quark),
        description = if (quarkExpired) stringResource(R.string.drive_login_expired)
            else quarkAccount?.nickname ?: stringResource(R.string.drive_login_prompt),
        avatarText = stringResource(R.string.drive_avatar_quark),
        isLoggedIn = quarkAccount != null,
        expired = quarkExpired
    )
    val ucExpired = (ucAccount?.invalidAt ?: 0L) > 0L
    val uc = DriveAccount(
        id = "uc",
        name = stringResource(R.string.platform_uc),
        description = if (ucExpired) stringResource(R.string.drive_login_expired)
            else ucAccount?.nickname ?: stringResource(R.string.drive_login_prompt),
        avatarText = "UC",
        isLoggedIn = ucAccount != null,
        expired = ucExpired
    )
    val xunleiExpired = (xunleiAccount?.invalidAt ?: 0L) > 0L
    val xunlei = DriveAccount(
        id = "xunlei",
        name = stringResource(R.string.platform_xunlei),
        description = if (xunleiExpired) stringResource(R.string.drive_login_expired)
            else xunleiAccount?.nickname ?: stringResource(R.string.drive_login_prompt),
        avatarText = stringResource(R.string.drive_avatar_xunlei),
        isLoggedIn = xunleiAccount != null,
        expired = xunleiExpired
    )
    val baiduExpired = (baiduAccount?.invalidAt ?: 0L) > 0L
    val baidu = DriveAccount(
        id = "baidu",
        name = stringResource(R.string.platform_baidu),
        description = when {
            baiduExpired -> stringResource(R.string.drive_login_expired)
            // 未登录：前置风险披露（P1-7）——README 的风控警告移进应用内
            baiduAccount == null -> stringResource(R.string.drive_baidu_risk_warning)
            else -> baiduAccount.nickname
        },
        avatarText = stringResource(R.string.drive_avatar_baidu),
        isLoggedIn = baiduAccount != null,
        expired = baiduExpired,
        riskWarning = baiduAccount == null
    )
    val c139Expired = (c139Account?.invalidAt ?: 0L) > 0L
    val c139 = DriveAccount(
        id = "c139",
        name = stringResource(R.string.platform_c139),
        description = if (c139Expired) stringResource(R.string.drive_login_expired)
            else c139Account?.nickname ?: stringResource(R.string.drive_login_prompt),
        avatarText = "139",
        isLoggedIn = c139Account != null,
        expired = c139Expired
    )
    val pan123Expired = (pan123Account?.invalidAt ?: 0L) > 0L
    val pan123 = DriveAccount(
        id = "pan123",
        name = stringResource(R.string.platform_pan123),
        description = if (pan123Expired) stringResource(R.string.drive_login_expired)
            else pan123Account?.nickname ?: stringResource(R.string.drive_login_prompt),
        avatarText = "123",
        isLoggedIn = pan123Account != null,
        expired = pan123Expired
    )

    // 仅首次进入或登录账号发生变化时自动加载；切换主 Tab 不重复刷新。
    val accounts = listOf(
        quarkAccount,
        ucAccount,
        xunleiAccount,
        baiduAccount,
        c139Account,
        pan123Account
    )
    LaunchedEffect(accounts) {
        driveQuotaViewModel.loadIfAccountsChanged(accounts)
    }
    // 下拉刷新状态：绑定空间配额加载中状态
    val isRefreshing by driveQuotaViewModel.loading.collectAsState()

    // 账号列表 ↔ 夸克云盘 ↔ UC 云盘 ↔ 迅雷云盘 ↔ 百度云盘 ↔ 139 云盘 ↔ 123 云盘：平滑过渡（淡入 + 轻微缩放，不僵硬）
    AnimatedContent(
        targetState = when {
            showCloud -> 1
            showUCCloud -> 2
            showXunleiCloud -> 3
            showBaiduCloud -> 4
            showC139Cloud -> 5
            showPan123Cloud -> 6
            else -> 0
        },
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f))
                .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.98f))
        },
        label = "driveContent"
    ) { target ->
        when (target) {
            1 -> CloudDriveScreen(
                viewModel = quarkCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            2 -> UCCloudScreen(
            viewModel = ucCloudViewModel,
            scrollBehavior = scrollBehavior,
            onExit = { showUCCloud = false },
            onDownloadStarted = onDownloadStarted
        )
        3 -> XunleiCloudScreen(
            viewModel = xunleiCloudViewModel,
            scrollBehavior = scrollBehavior,
            onExit = { showXunleiCloud = false },
            onDownloadStarted = onDownloadStarted
        )
        4 -> BaiduCloudScreen(
            viewModel = baiduCloudViewModel,
            scrollBehavior = scrollBehavior,
            onExit = { showBaiduCloud = false },
            onDownloadStarted = onDownloadStarted
        )
        5 -> C139CloudScreen(
            viewModel = c139CloudViewModel,
            scrollBehavior = scrollBehavior,
            onExit = { showC139Cloud = false },
            onDownloadStarted = onDownloadStarted
        )
        6 -> Pan123CloudScreen(
            viewModel = pan123CloudViewModel,
            scrollBehavior = scrollBehavior,
            onExit = { showPan123Cloud = false },
            onDownloadStarted = onDownloadStarted
        )
            else -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { driveQuotaViewModel.loadAll() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.drive_login_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                item(key = quark.id) {
                    DriveAccountCard(
                        account = quark,
                        quota = driveQuotaViewModel.quarkQuota.collectAsState().value,
                        onClick = if (quark.isLoggedIn && !quark.expired) {
                            { showCloud = true }
                        } else {
                            onQuarkLogin
                        },
                        onMoreClick = if (quark.isLoggedIn) {
                            { showQuarkSheet = true }
                        } else {
                            null
                        }
                    )
                }
                item(key = uc.id) {
                    DriveAccountCard(
                        account = uc,
                        quota = driveQuotaViewModel.ucQuota.collectAsState().value,
                        onClick = if (uc.isLoggedIn && !uc.expired) {
                            { showUCCloud = true }
                        } else {
                            onUCLogin
                        },
                        onMoreClick = if (uc.isLoggedIn) {
                            { showUCSheet = true }
                        } else {
                            null
                        }
                    )
                }
                item(key = xunlei.id) {
                    DriveAccountCard(
                        account = xunlei,
                        quota = driveQuotaViewModel.xunleiQuota.collectAsState().value,
                        onClick = if (xunlei.isLoggedIn && !xunlei.expired) {
                            { showXunleiCloud = true }
                        } else {
                            onXunleiLogin
                        },
                        onMoreClick = if (xunlei.isLoggedIn) {
                            { showXunleiSheet = true }
                        } else {
                            null
                        }
                    )
                }
                item(key = baidu.id) {
                    DriveAccountCard(
                        account = baidu,
                        quota = driveQuotaViewModel.baiduQuota.collectAsState().value,
                        onClick = if (baidu.isLoggedIn && !baidu.expired) {
                            { showBaiduCloud = true }
                        } else {
                            onBaiduLogin
                        },
                        onMoreClick = if (baidu.isLoggedIn) {
                            { showBaiduSheet = true }
                        } else {
                            null
                        }
                    )
                }
                item(key = c139.id) {
                    DriveAccountCard(
                        account = c139,
                        quota = driveQuotaViewModel.c139Quota.collectAsState().value,
                        onClick = if (c139.isLoggedIn && !c139.expired) {
                            { showC139Cloud = true }
                        } else {
                            onC139Login
                        },
                        onMoreClick = if (c139.isLoggedIn) {
                            { showC139Sheet = true }
                        } else {
                            null
                        }
                    )
                }
                item(key = pan123.id) {
                    DriveAccountCard(
                        account = pan123,
                        quota = driveQuotaViewModel.pan123Quota.collectAsState().value,
                        onClick = if (pan123.isLoggedIn && !pan123.expired) {
                            { showPan123Cloud = true }
                        } else {
                            onPan123Login
                        },
                        onMoreClick = if (pan123.isLoggedIn) {
                            { showPan123Sheet = true }
                        } else {
                            null
                        }
                    )
                }
            }
            }
        }
    }

    // 已登录夸克：点击卡片弹出账号信息底部弹窗
    if (showQuarkSheet && quarkAccount != null) {
        CloudAccountSheet(
            account = quarkAccount.toAccountUi(),
            onLogout = {
                onQuarkLogout()
                showQuarkSheet = false
            },
            onDismiss = { showQuarkSheet = false }
        )
    }

    // 已登录 UC：点击卡片弹出账号信息底部弹窗
    if (showUCSheet && ucAccount != null) {
        CloudAccountSheet(
            account = ucAccount.toAccountUi(),
            onLogout = {
                onUCLogout()
                showUCSheet = false
            },
            onDismiss = { showUCSheet = false }
        )
    }

    // 已登录迅雷：点击卡片弹出账号信息底部弹窗
    if (showXunleiSheet && xunleiAccount != null) {
        CloudAccountSheet(
            account = xunleiAccount.toAccountUi(),
            onLogout = {
                onXunleiLogout()
                showXunleiSheet = false
            },
            onDismiss = { showXunleiSheet = false }
        )
    }

    // 已登录百度：点击卡片弹出账号信息底部弹窗
    if (showBaiduSheet && baiduAccount != null) {
        CloudAccountSheet(
            account = baiduAccount.toAccountUi(),
            onLogout = {
                onBaiduLogout()
                showBaiduSheet = false
            },
            onDismiss = { showBaiduSheet = false }
        )
    }

    // 已登录 139：点击卡片弹出账号信息底部弹窗
    if (showC139Sheet && c139Account != null) {
        CloudAccountSheet(
            account = c139Account.toAccountUi(),
            onLogout = {
                onC139Logout()
                showC139Sheet = false
            },
            onDismiss = { showC139Sheet = false }
        )
    }

    // 已登录 123：点击卡片弹出账号信息底部弹窗
    if (showPan123Sheet && pan123Account != null) {
        CloudAccountSheet(
            account = pan123Account.toAccountUi(),
            onLogout = {
                onPan123Logout()
                showPan123Sheet = false
            },
            onDismiss = { showPan123Sheet = false }
        )
    }
}

@Composable
private fun DriveAccountCard(
    account: DriveAccount,
    /** 网盘空间详情（已登录且有数据时在卡片内显示进度条）；null 不显示 */
    quota: QuotaInfo? = null,
    onClick: (() -> Unit)? = null,
    /** 已登录时右侧「三个点」更多按钮（打开账号弹窗）；null 则不显示 */
    onMoreClick: (() -> Unit)? = null
) {
    val cardShape = MaterialTheme.shapes.large
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val content: @Composable () -> Unit = {
        DriveAccountCardContent(
            account = account,
            quota = quota,
            clickable = onClick != null,
            onMoreClick = onMoreClick
        )
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = cardColors
        ) { content() }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = cardColors
        ) { content() }
    }
}

@Composable
private fun DriveAccountCardContent(
    account: DriveAccount,
    quota: QuotaInfo? = null,
    clickable: Boolean,
    onMoreClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 品牌头像（暂用首字母，后续可替换为品牌图标）
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = if (account.isLoggedIn) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = account.avatarText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = account.description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (account.expired || account.riskWarning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            // 已登录且有空间数据：卡片内展示剩余空间进度条（出现时淡入 + 纵向展开，避免突兀）
            AnimatedVisibility(
                visible = account.isLoggedIn && quota != null,
                enter = fadeIn(tween(300)) + expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(300)
                ),
                exit = fadeOut(tween(200)) + shrinkVertically(animationSpec = tween(200))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    quota?.let { QuotaInlineBar(it) }
                }
            }
        }

        when {
            account.isLoggedIn && onMoreClick != null -> IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.drive_more_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            account.isLoggedIn -> LoginBadge(isLoggedIn = true)
            clickable -> Text(
                text = stringResource(R.string.drive_login_action),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            else -> LoginBadge(isLoggedIn = false)
        }
    }
}

/** 网盘卡片内空间进度条：已用 / 总容量 + 细进度条 */
@Composable
private fun QuotaInlineBar(quota: QuotaInfo) {
    val ratio = if (quota.total > 0) {
        (quota.used.toFloat() / quota.total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column {
        Text(
            text = stringResource(R.string.drive_quota_usage, formatBytes(quota.used), formatBytes(quota.total)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
private fun LoginBadge(isLoggedIn: Boolean) {
    val (label, color) = if (isLoggedIn) {
        stringResource(R.string.drive_status_logged_in) to MaterialTheme.colorScheme.primary
    } else {
        stringResource(R.string.drive_status_logged_out) to MaterialTheme.colorScheme.outline
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 字节数格式化：B / KB / MB / GB / TB */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B"
    else String.format("%.1f %s", value, units[unit])
}
