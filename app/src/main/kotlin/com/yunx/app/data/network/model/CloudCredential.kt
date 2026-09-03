package com.yunx.app.data.network.model

/**
 * 网盘认证凭证的最小类型边界。
 * 平台 API 只接收与自身登录态匹配的子类型，调用方不再用裸 String 传递存储凭证；
 * 分享域密钥（stoken/提取码/sekey）、登录输入（账号/密码）、设备指纹与持久化字段仍为 String。
 */
sealed interface CloudCredential {
    val value: String

    data class Cookie(override val value: String) : CloudCredential
    data class AccessToken(override val value: String) : CloudCredential

    /**
     * 迅雷三元组：请求必须同时携带 accessToken/deviceId/captchaToken。
     * [value] 为 accessToken（兼容以 token 为空判断的旧逻辑）。
     */
    data class Xunlei(
        val accessToken: String,
        val deviceId: String,
        val captchaToken: String
    ) : CloudCredential {
        override val value: String get() = accessToken
    }

    fun isUsable(): Boolean = value.isNotBlank()
}

/** 凭证类型收窄失败时抛出的明确错误（替代裸 String 传错平台的静默失败）。 */
internal fun CloudCredential.requireCookie(): CloudCredential.Cookie =
    this as? CloudCredential.Cookie
        ?: throw IllegalArgumentException("凭证类型错误：当前平台需要 Cookie 登录态")

internal fun CloudCredential.requireAccessToken(): CloudCredential.AccessToken =
    this as? CloudCredential.AccessToken
        ?: throw IllegalArgumentException("凭证类型错误：当前平台需要 AccessToken 登录态")

internal fun CloudCredential.requireXunlei(): CloudCredential.Xunlei =
    this as? CloudCredential.Xunlei
        ?: throw IllegalArgumentException("凭证类型错误：当前平台需要迅雷三元组登录态")
