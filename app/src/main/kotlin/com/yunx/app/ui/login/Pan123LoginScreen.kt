package com.yunx.app.ui.login

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yunx.app.R
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.viewmodel.Pan123AccountViewModel
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 123 云盘登录页（网页登录方案，与夸克/百度等一致）：
 * - WebView 打开官网个人盘主页 [Pan123Constants.WEB_LOGIN_URL]，由用户手动登录（验证码/扫码由官网处理）；
 * - 登录成功后网页 SPA 会把 Bearer JWT 写入当前域 localStorage，键名 authorToken（旧账号密码登录的 data.token 同源同形）；
 * - 右上角「保存」/自动登录检测均从 localStorage 提取该值，经 user/info 接口校验后落库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pan123LoginScreen(
    viewModel: Pan123AccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // 手动输入 Token 弹窗状态
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var isSavingManual by remember { mutableStateOf(false) }

    // 登录教程弹窗：进入页面即展示一次
    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showTutorial = true }

    val loginSuccessHint = stringResource(R.string.login_success)
    val loginNotDetectedHint = stringResource(R.string.login_not_detected)
    val tokenInvalidHint = stringResource(R.string.login_token_invalid)

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true   // 123 云盘把登录态（authorToken）存在 localStorage，必须开启
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
            setInitialScale(0)
            // 桌面 UA：yun.123pan.cn 个人盘是桌面 SPA；移动 UA 会跳到不完整的移动版页面
            settings.userAgentString = Pan123Constants.WEB_UA
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    // 强制覆盖页面 viewport：适配屏幕宽度 + 允许双指缩放（桌面版页面无 viewport 或限制缩放时生效）
                    view?.evaluateJavascript(
                        "(function(){var m=document.querySelector('meta[name=\"viewport\"]');" +
                            "var c='width=device-width,initial-scale=1.0,maximum-scale=5.0,user-scalable=yes';" +
                            "if(m){m.setAttribute('content',c);}else{var n=document.createElement('meta');n.name='viewport';n.content=c;document.head.appendChild(n);}" +
                            "window.dispatchEvent(new Event('resize'));})()",
                        null
                    )
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(Pan123Constants.WEB_LOGIN_URL)
        }
    }

    // 页面销毁时释放 WebView
    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    // 系统返回键 → 返回主页（保存中禁用）
    BackHandler(enabled = !isSaving && !isSavingManual) { onBack() }

    // 自动登录检测：网页登录完成（authorToken 写入 localStorage）即自动提取并校验登录，无需手动点「保存」
    rememberWebLoginAutoDetect(
        sampleCredential = { webView.readLocalStorageValue(Pan123Constants.LOCAL_STORAGE_TOKEN_KEY) },
        isPlausible = { it.isNotBlank() },
        validateAndSave = { viewModel.saveToken(it) },
        isPaused = { isSaving || isSavingManual || showTokenDialog },
        onInFlightChange = { isSaving = it },
        onAutoSaved = onSaved
    )

    // 全局 Snackbar 宿主
    val snackbarHostState = rememberGlobalSnackbarHostState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title_format, stringResource(R.string.platform_pan123)), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { if (!isSaving && !isSavingManual) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cloud_action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!isSaving && !isSavingManual) showTokenDialog = true },
                        enabled = !isSaving && !isSavingManual
                    ) {
                        Icon(
                            Icons.Outlined.ContentPaste,
                            contentDescription = stringResource(R.string.login_manual_token_description),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                // 提取网页 localStorage 的 authorToken 作为登录凭证
                                val token = webView.readLocalStorageValue(Pan123Constants.LOCAL_STORAGE_TOKEN_KEY)
                                val saved = if (token.isBlank()) false else viewModel.saveToken(token)
                                isSaving = false
                                if (saved) {
                                    SnackbarController.show(loginSuccessHint)
                                    onSaved()
                                } else {
                                    SnackbarController.show(loginNotDetectedHint)
                                }
                            }
                        },
                        enabled = !isSaving && !isSavingManual
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.login_save_action))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // 登录教程弹窗
    if (showTutorial) {
        AlertDialog(
            onDismissRequest = { showTutorial = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.login_tutorial_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.login_tutorial_pan123_step1),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.login_tutorial_pan123_step2),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.login_tutorial_pan123_step3),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.login_tutorial_pan123_step4),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.login_tutorial_pan123_step5),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTutorial = false }) { Text(stringResource(R.string.login_tutorial_got_it)) }
            }
        )
    }

    // 手动输入 Token 弹窗
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingManual) showTokenDialog = false },
            title = { Text(stringResource(R.string.login_manual_token_description)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.login_token_dialog_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.login_token_placeholder)) },
                        minLines = 4,
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isSavingManual = true
                            val saved = viewModel.saveToken(tokenInput.trim())
                            isSavingManual = false
                            if (saved) {
                                SnackbarController.show(loginSuccessHint)
                                showTokenDialog = false
                                onSaved()
                            } else {
                                SnackbarController.show(tokenInvalidHint)
                            }
                        }
                    },
                    enabled = tokenInput.isNotBlank() && !isSavingManual
                ) {
                    if (isSavingManual) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.login_save_action))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isSavingManual) showTokenDialog = false },
                    enabled = !isSavingManual
                ) { Text(stringResource(R.string.cloud_action_cancel)) }
            }
        )
    }
}

/**
 * 读取当前 WebView 页面 localStorage 中 [key] 的值（123 云盘登录态存在 authorToken）。
 * ⚠️ 依赖 123 站点私有实现（键名 authorToken / 值为 JWT），官网改版可能失效——失效时用户可走「粘贴 Token」兜底。
 * JS 侧用 encodeURIComponent 包一层返回，避免 JWT 特殊字符干扰 evaluateJavascript 的 JSON 返回值解析；
 * 页面未就绪 / 跨域（如还停留在登录跳转中间页）时返回空串，由调用方继续轮询。
 */
private suspend fun WebView.readLocalStorageValue(key: String): String =
    withTimeoutOrNull(2_000) {
        suspendCancellableCoroutine { cont ->
            try {
                evaluateJavascript(
                    "(function(){try{var v=localStorage.getItem('" + key + "');" +
                        "return v===null?'':encodeURIComponent(v)}catch(e){return ''}})()"
                ) { result ->
                    val raw = result?.trim() ?: ""
                    val value = when {
                        raw.isEmpty() || raw == "\"\"" || raw == "null" -> ""
                        raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ->
                            raw.substring(1, raw.length - 1)
                        else -> raw
                    }
                    val decoded = if (value.isBlank()) "" else
                        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault("")
                    if (cont.isActive) cont.resume(decoded)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume("")
            }
        }
    } ?: ""
