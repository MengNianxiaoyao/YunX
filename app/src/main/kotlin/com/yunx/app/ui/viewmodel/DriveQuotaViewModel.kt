package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.network.CloudFileSource
import com.yunx.app.data.network.model.QuotaInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 网盘空间详情 ViewModel：并发加载 6 个平台的容量使用情况（仅已登录平台请求）。
 * 网盘页顶部「空间总览」展示用。
 */
class DriveQuotaViewModel(
    private val quarkSource: CloudFileSource,
    private val ucSource: CloudFileSource,
    private val xunleiSource: CloudFileSource,
    private val baiduSource: CloudFileSource,
    private val c139Source: CloudFileSource,
    private val pan123Source: CloudFileSource
) : ViewModel() {

    private val _quarkQuota = MutableStateFlow<QuotaInfo?>(null)
    val quarkQuota: StateFlow<QuotaInfo?> = _quarkQuota.asStateFlow()

    private val _ucQuota = MutableStateFlow<QuotaInfo?>(null)
    val ucQuota: StateFlow<QuotaInfo?> = _ucQuota.asStateFlow()

    private val _xunleiQuota = MutableStateFlow<QuotaInfo?>(null)
    val xunleiQuota: StateFlow<QuotaInfo?> = _xunleiQuota.asStateFlow()

    private val _baiduQuota = MutableStateFlow<QuotaInfo?>(null)
    val baiduQuota: StateFlow<QuotaInfo?> = _baiduQuota.asStateFlow()

    private val _c139Quota = MutableStateFlow<QuotaInfo?>(null)
    val c139Quota: StateFlow<QuotaInfo?> = _c139Quota.asStateFlow()

    private val _pan123Quota = MutableStateFlow<QuotaInfo?>(null)
    val pan123Quota: StateFlow<QuotaInfo?> = _pan123Quota.asStateFlow()

    /** 是否加载中 */
    val loading = MutableStateFlow(false)

    /** 最近一次自动加载对应的账号版本；切换 Tab 重建 Composable 时用于跳过重复请求。 */
    private var loadedAccounts: List<Any?>? = null

    fun loadIfAccountsChanged(accounts: List<Any?>) {
        if (loadedAccounts == accounts) return
        loadedAccounts = accounts.toList()
        loadAll()
    }

    /** 并发加载全部已登录平台的空间（各平台独立请求，互不阻塞；未登录平台自动跳过） */
    fun loadAll() {
        if (loading.value) return // 防止下拉刷新与进入页面初始化重复触发
        loading.value = true
        viewModelScope.launch {
            coroutineScope {
                loadQuota(0, quarkSource, _quarkQuota)
                loadQuota(1, ucSource, _ucQuota)
                loadQuota(2, xunleiSource, _xunleiQuota)
                loadQuota(3, baiduSource, _baiduQuota)
                loadQuota(4, c139Source, _c139Quota)
                loadQuota(5, pan123Source, _pan123Quota)
            }
            loading.value = false
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.loadQuota(
        accountIndex: Int,
        source: CloudFileSource,
        state: MutableStateFlow<QuotaInfo?>
    ) {
        launch {
            if (loadedAccounts?.getOrNull(accountIndex) != null) {
                state.value = runCatching { source.quota() }.getOrNull()
            } else {
                state.value = null
            }
        }
    }

    class Factory(
        private val quarkSource: CloudFileSource,
        private val ucSource: CloudFileSource,
        private val xunleiSource: CloudFileSource,
        private val baiduSource: CloudFileSource,
        private val c139Source: CloudFileSource,
        private val pan123Source: CloudFileSource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DriveQuotaViewModel(
                quarkSource,
                ucSource,
                xunleiSource,
                baiduSource,
                c139Source,
                pan123Source
            ) as T
    }
}
