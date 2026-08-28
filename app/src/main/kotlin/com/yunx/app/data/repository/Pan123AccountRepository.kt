package com.yunx.app.data.repository

import com.yunx.app.data.db.Pan123AccountDao
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.network.Pan123Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 123 云盘账号仓库：账号+密码登录 → JWT 落库（依据《123网盘API文档_面向Agent.md》§5.1）。
 * 凭证 = data.token（Bearer JWT，约 90 天过期）；token 失效时重新走登录（无 refresh 接口，文档 §3.3）。
 */
class Pan123AccountRepository(
    private val dao: Pan123AccountDao,
    private val api: Pan123Api
) {

    /** authInvalidListener 落库用独立作用域（非 UI 线程，API 回调内直接调用） */
    private val invalidScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // token 失效（HTTP/code 401）：标记登录态失效（不清库，保留昵称；重登自动清除）
        api.authInvalidListener = {
            invalidScope.launch { markExpired() }
        }
    }

    fun observeAccount(): Flow<Pan123AccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): Pan123AccountEntity? = dao.getAccount()

    /** 标记登录态失效（幂等：已标记则跳过；重登自动清除） */
    suspend fun markExpired() {
        dao.getAccount()?.let {
            if (it.invalidAt == 0L) dao.upsert(it.copy(invalidAt = System.currentTimeMillis()))
        }
    }

    /** 账号密码登录（POST user.123pan.cn/api/user/sign_in，无需签名）→ token 落库，返回 true */
    suspend fun login(account: String, password: String): Boolean {
        val token = api.login(account.trim(), password)
        if (token.isBlank()) return false
        val nickname = api.fetchNickname(token)?.takeIf { it.isNotBlank() } ?: account.trim()
        dao.upsert(
            Pan123AccountEntity(
                id = "pan123",
                accessToken = token,
                account = account.trim(),
                nickname = nickname
            )
        )
        return true
    }

    /** 校验当前 token 是否仍有效（失败自动清库，下次重新登录） */
    suspend fun validate(): Boolean {
        val acc = dao.getAccount() ?: return false
        val ok = api.fetchNickname(acc.accessToken) != null
        if (!ok) dao.clear()
        return ok
    }

    suspend fun logout() {
        dao.clear()
    }
}