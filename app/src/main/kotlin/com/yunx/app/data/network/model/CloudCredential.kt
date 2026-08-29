package com.yunx.app.data.network.model

/**
 * 网盘认证凭证的最小类型边界。
 * 平台 API 仍接收原始字符串，但解析层不再用裸 String 区分 Cookie 和 Access Token。
 */
sealed interface CloudCredential {
    val value: String

    data class Cookie(override val value: String) : CloudCredential
    data class AccessToken(override val value: String) : CloudCredential

    fun isUsable(): Boolean = value.isNotBlank()
}
