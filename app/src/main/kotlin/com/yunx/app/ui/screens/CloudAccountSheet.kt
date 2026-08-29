package com.yunx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.QuarkAccountEntity
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.data.db.XunleiAccountEntity
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.util.copyToClipboard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 云盘账号统一展示模型（P2-2）：6 种 Room Entity → 单一 UI 形态的适配层。
 * 各平台真实差异（凭证类型、额外信息行、文案）全部数据化在 [toAccountUi] 映射里。
 */
data class CloudAccountUi(
    /** 平台名（徽标「XX网盘 · 已登录」） */
    val platformName: String,
    val nickname: String,
    /** 信息行（登录账号/设备号等，按序展示；登录时间由 Composable 自动追加为首行） */
    val infoRows: List<Pair<String, String>> = emptyList(),
    /** 凭证区标签（"Cookie" / "Token（JWT）"）；null = 不展示凭证区（如迅雷只显示设备号） */
    val credentialLabel: String? = null,
    /** 凭证内容（可展开/复制） */
    val credential: String? = null,
    /** 复制成功提示文案 */
    val credentialCopiedHint: String = "Cookie 已复制",
    /** 剪贴板条目标签 */
    val clipboardLabel: String = "credential",
    /** 退出登录二次确认文案 */
    val logoutConfirmText: String,
    /** updatedAt（登录时间，仅作 remember 键与格式化） */
    val updatedAt: Long
)

private fun loginTimeText(updatedAt: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(updatedAt))

/** 夸克 → Cookie 版 */
fun QuarkAccountEntity.toAccountUi() = CloudAccountUi(
    platformName = "夸克网盘",
    nickname = nickname,
    credentialLabel = "Cookie",
    credential = cookie,
    clipboardLabel = "quark_cookie",
    logoutConfirmText = "确定要退出当前夸克账号吗？退出后将清除本地 Cookie。",
    updatedAt = updatedAt
)

/** UC → Cookie 版 */
fun UCAccountEntity.toAccountUi() = CloudAccountUi(
    platformName = "UC网盘",
    nickname = nickname,
    credentialLabel = "Cookie",
    credential = cookie,
    clipboardLabel = "uc_cookie",
    logoutConfirmText = "确定要退出当前 UC 账号吗？退出后将清除本地 Cookie。",
    updatedAt = updatedAt
)

/** 迅雷 → 无凭证版（只显示设备号；不主动暴露 token） */
fun XunleiAccountEntity.toAccountUi() = CloudAccountUi(
    platformName = "迅雷网盘",
    nickname = nickname,
    infoRows = listOf("设备号" to deviceId.ifBlank { "-" }),
    credentialLabel = null,
    credential = null,
    logoutConfirmText = "确定要退出当前迅雷账号吗？",
    updatedAt = updatedAt
)

/** 百度 → Cookie 版 */
fun BaiduAccountEntity.toAccountUi() = CloudAccountUi(
    platformName = "百度网盘",
    nickname = nickname,
    credentialLabel = "Cookie",
    credential = cookie,
    clipboardLabel = "baidu_cookie",
    logoutConfirmText = "确定要退出当前百度账号吗？退出后将清除本地 Cookie。",
    updatedAt = updatedAt
)

/** 139 → Cookie 版 */
fun C139AccountEntity.toAccountUi() = CloudAccountUi(
    platformName = "139网盘",
    nickname = nickname,
    credentialLabel = "Cookie",
    credential = cookie,
    clipboardLabel = "c139_cookie",
    logoutConfirmText = "确定要退出当前 139 账号吗？退出后将清除本地 Cookie。",
    updatedAt = updatedAt
)

/** 123 → Token 版（含登录账号行） */
fun Pan123AccountEntity.toAccountUi() = CloudAccountUi(
    platformName = "123云盘",
    nickname = nickname,
    infoRows = listOf("登录账号" to account.ifBlank { nickname }),
    credentialLabel = "Token（JWT）",
    credential = accessToken,
    credentialCopiedHint = "Token 已复制",
    clipboardLabel = "pan123_token",
    logoutConfirmText = "确定要退出当前 123 账号吗？退出后将清除本地凭证。",
    updatedAt = updatedAt
)

/**
 * 已登录账号的底部弹窗（P2-2 统一版，替代 6 份平台 AccountSheet）：
 * 展示用户信息、登录时间、凭证（可展开/复制），并提供退出登录（二次确认）。
 * UC / 迅雷原为简化版，统一后获得与夸克一致的完整体验。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAccountSheet(
    account: CloudAccountUi,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showFullCredential by rememberSaveable { mutableStateOf(false) }
    // 退出登录二次确认
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val credential = account.credential
    val credentialPreviewLimit = 200
    val credentialTruncated = (credential?.length ?: 0) > credentialPreviewLimit
    val displayCredential = if (showFullCredential || !credentialTruncated) {
        credential.orEmpty()
    } else {
        credential.orEmpty().take(credentialPreviewLimit) + "…"
    }
    val loginTime = remember(account.updatedAt) {
        loginTimeText(account.updatedAt)
    }

    // 打开即完全展开，跳过半折叠状态
    // ModalBottomSheet 为独立窗口，需自带 Snackbar 宿主
    val snackbarHostState = rememberGlobalSnackbarHostState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 内容滚动到底后继续上滑的滚动量直接消费，避免传给 Sheet 造成上下抽动
    val scrollState = rememberScrollState()
    val sheetNestedScroll = remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val dy = available.y
                if (dy > 0 && scrollState.value >= scrollState.maxValue) {
                    return Offset(0f, dy)
                }
                return Offset.Zero
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showFullCredential) {
                        // 展开凭证：占满全屏并允许内部滚动
                        Modifier
                            .fillMaxHeight()
                            .verticalScroll(scrollState)
                            .nestedScroll(sheetNestedScroll)
                    } else {
                        // 未展开：自适应内容高度，不滚动
                        Modifier
                    }
                )
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
        ) {
            // 用户信息
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = account.nickname.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = account.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${account.platformName} · 已登录",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 登录信息
            Text(
                text = "登录信息",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow(label = "登录时间", value = loginTime)
                    account.infoRows.forEach { (label, value) ->
                        Spacer(modifier = Modifier.height(12.dp))
                        InfoRow(label = label, value = value)
                    }
                    if (credential != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = account.credentialLabel.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayCredential,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (credentialTruncated) {
                                TextButton(onClick = { showFullCredential = !showFullCredential }) {
                                    Text(if (showFullCredential) "收起" else "展开全部")
                                }
                            }
                            TextButton(
                                onClick = {
                                    copyToClipboard(context, credential, account.clipboardLabel)
                                    SnackbarController.show(account.credentialCopiedHint)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("复制")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 退出登录
            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("退出登录")
            }

            // 复制提示（ModalBottomSheet 为独立窗口，需自带 Snackbar 宿主）
            SnackbarHost(hostState = snackbarHostState)
        }
    }

    // 退出登录二次确认
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text(account.logoutConfirmText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    }
                ) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
