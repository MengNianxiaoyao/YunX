package com.yunx.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 夸克网盘登录凭证（cookie 落库，后续所有 API 请求携带）。
 */
@Entity(tableName = "quark_account")
data class QuarkAccountEntity(
    @PrimaryKey
    val id: String = "quark",
    val cookie: String = "",
    val nickname: String = "",
    /** 登录态失效标记时间戳（0 = 正常；> 0 时 UI 显示"登录已过期，点击重新登录"） */
    @ColumnInfo(defaultValue = "0")
    val invalidAt: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)