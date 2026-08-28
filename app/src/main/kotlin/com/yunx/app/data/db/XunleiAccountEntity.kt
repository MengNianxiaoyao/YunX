package com.yunx.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 迅雷网盘登录凭证（access_token 落库，pan API 请求携带 Bearer）。
 */
@Entity(tableName = "xunlei_account")
data class XunleiAccountEntity(
    @PrimaryKey
    val id: String = "xunlei",
    val accessToken: String = "",
    val refreshToken: String = "",
    val deviceId: String = "",
    val captchaToken: String = "",
    val nickname: String = "",
    /** 登录态失效标记时间戳（0 = 正常；> 0 时 UI 显示"登录已过期，点击重新登录"） */
    @ColumnInfo(defaultValue = "0")
    val invalidAt: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)