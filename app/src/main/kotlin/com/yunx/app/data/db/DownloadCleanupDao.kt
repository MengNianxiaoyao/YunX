package com.yunx.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DownloadCleanupDao {
    @Insert
    suspend fun insert(cleanup: DownloadCleanupEntity): Long

    @Query("SELECT * FROM download_cleanup ORDER BY createdAt ASC")
    suspend fun getAll(): List<DownloadCleanupEntity>

    @Query("SELECT * FROM download_cleanup WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: Long): DownloadCleanupEntity?

    @Query("DELETE FROM download_cleanup WHERE id = :id")
    suspend fun delete(id: Long)
}
