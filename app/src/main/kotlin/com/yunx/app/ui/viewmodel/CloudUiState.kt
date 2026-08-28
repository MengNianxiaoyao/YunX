package com.yunx.app.ui.viewmodel

import com.yunx.app.data.network.model.ShareFile
import kotlinx.coroutines.flow.StateFlow

/**
 * 云盘浏览统一 UiState（P2-1：6 份同构 sealed interface 合并）。
 * 各平台 ViewModel 共用同一状态形态，为 P2-4 泛型化 Screen / BaseCloudViewModel 铺路。
 */
sealed interface CloudUiState {
    data object Loading : CloudUiState

    /**
     * @param dir 当前目录标识：各平台语义不同——夸克/UC/迅雷为目录 fid，
     *   百度为绝对路径（根 "/"），139 为 fileId（根 "/"），123 为目录 id（根 "0"）
     */
    data class Loaded(
        val files: List<ShareFile>,
        val pathNames: List<String>,
        val dir: String,
        val hasMore: Boolean = false,
        val cursor: String? = null
    ) : CloudUiState

    data class Error(val message: String) : CloudUiState
}

/**
 * 云盘目录浏览最小能力接口（P2-2：统一 SaveSheet 只依赖此接口而非具体 VM 类型；
 * P2-4 的 BaseCloudViewModel 将完整实现它）。
 * 6 个 CloudViewModel 已验证方法签名一致。
 */
interface CloudDirBrowser {
    val uiState: StateFlow<CloudUiState>

    /** 回到根目录 */
    fun loadRoot()

    /** 进入子目录 */
    fun openFolder(file: ShareFile)

    /** 返回上一级 */
    fun back()

    /** 面包屑跳转到指定层级 */
    fun navigateToLevel(level: Int)
}
