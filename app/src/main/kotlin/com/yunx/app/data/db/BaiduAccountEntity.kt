package com.yunx.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 百度网盘登录凭证（cookie 落库，后续所有 API 请求携带）。
 * 关键字段：BDUSS / STOKEN（WebView 登录后从 document.cookie 提取）。
 */
@Entity(tableName = "baidu_account")
data class BaiduAccountEntity(
    @PrimaryKey
    val id: String = "baidu",
    val cookie: String = "",
    val nickname: String = "",
    /** 登录态失效标记时间戳（0 = 正常；> 0 时 UI 显示"登录已过期，点击重新登录"） */
    @ColumnInfo(defaultValue = "0")
    val invalidAt: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)