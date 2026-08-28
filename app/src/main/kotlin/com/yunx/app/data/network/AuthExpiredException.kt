package com.yunx.app.data.network

/**
 * 登录态失效（token 过期 / 被撤销 / refresh 失败）。
 * 与通用 IllegalStateException 区分：捕获方应标记账号 invalidAt（保留昵称供展示），
 * 由 UI 引导用户重新登录，而不是清库或笼统报"操作失败"。
 */
class AuthExpiredException(message: String) : IllegalStateException(message)
