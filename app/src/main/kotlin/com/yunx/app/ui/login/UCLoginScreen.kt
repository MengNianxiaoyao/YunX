package com.yunx.app.ui.login

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
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
import com.yunx.app.data.network.UCConstants
import com.yunx.app.ui.viewmodel.UCAccountViewModel
import kotlinx.coroutines.launch

/**
 * UC 网盘登录页：WebView 登录 + 手动输入 Cookie + 教程弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UCLoginScreen(
    viewModel: UCAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var isSavingManual by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showTutorial = true }

    val loginSuccessHint = stringResource(R.string.login_success)
    val loginNotDetectedHint = stringResource(R.string.login_not_detected)
    val cookieInvalidHint = stringResource(R.string.login_cookie_invalid_pus)

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
            setInitialScale(0)
            settings.userAgentString = UCConstants.USER_AGENT
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    // 强制覆盖页面 viewport：允许缩放 + 适配屏幕宽度（桌面版页面无 viewport 或限制了缩放时生效）
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
            loadUrl(UCConstants.LOGIN_URL)
        }
    }

    // 自动登录检测：网页内登录完成（Cookie 出现并通过接口校验）即自动保存登录；右上角「保存」保留作手动兜底
    rememberWebLoginAutoDetect(
        sampleCredential = { CookieManager.getInstance().getCookie(UCConstants.COOKIE_DOMAIN).orEmpty() },
        isPlausible = { UCConstants.isValidCookie(it) },
        validateAndSave = { viewModel.saveUCAccount(it) },
        isPaused = { isSaving || isSavingManual || showCookieDialog },
        onInFlightChange = { isSaving = it },
        onAutoSaved = onSaved
    )

    DisposableEffect(Unit) { onDispose { webView.destroy() } }
    BackHandler(enabled = !isSaving && !isSavingManual) { onBack() }

    // 全局 Snackbar 宿主
    val snackbarHostState = rememberGlobalSnackbarHostState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title_format, stringResource(R.string.platform_uc)), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { if (!isSaving && !isSavingManual) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cloud_action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!isSaving && !isSavingManual) showCookieDialog = true },
                        enabled = !isSaving && !isSavingManual
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(R.string.login_manual_cookie_description),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val cookie = CookieManager.getInstance()
                                    .getCookie(UCConstants.COOKIE_DOMAIN).orEmpty()
                                val saved = viewModel.saveUCAccount(cookie)
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
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else { Text(stringResource(R.string.login_save_action)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    if (showTutorial) {
        AlertDialog(
            onDismissRequest = { showTutorial = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.login_tutorial_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.login_tutorial_uc_step1), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.login_tutorial_step_auto), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.login_tutorial_cookie_pus), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.login_tutorial_uc_step4), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showTutorial = false }) { Text(stringResource(R.string.login_tutorial_got_it)) } }
        )
    }

    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingManual) showCookieDialog = false },
            title = { Text(stringResource(R.string.login_cookie_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.login_cookie_hint_pus),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cookieInput, onValueChange = { cookieInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.login_cookie_placeholder)) }, minLines = 4, maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isSavingManual = true
                            val saved = viewModel.saveUCAccount(cookieInput.trim())
                            isSavingManual = false
                            if (saved) {
                                SnackbarController.show(loginSuccessHint)
                                showCookieDialog = false
                                onSaved()
                            } else {
                                SnackbarController.show(cookieInvalidHint)
                            }
                        }
                    },
                    enabled = cookieInput.isNotBlank() && !isSavingManual
                ) {
                    if (isSavingManual) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                    else { Text(stringResource(R.string.login_save_action)) }
                }
            },
            dismissButton = { TextButton(onClick = { if (!isSavingManual) showCookieDialog = false }, enabled = !isSavingManual) { Text(stringResource(R.string.cloud_action_cancel)) } }
        )
    }
}
