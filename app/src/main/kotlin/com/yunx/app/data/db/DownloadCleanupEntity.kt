package com.yunx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 下载关联的云端临时资源清理记录，独立于下载任务以支持删除任务后重试。 */
@Entity(tableName = "download_cleanup")
data class DownloadCleanupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val platform: String,
    val resourceId: String,
    val credential: String,
    val createdAt: Long = System.currentTimeMillis()
)
