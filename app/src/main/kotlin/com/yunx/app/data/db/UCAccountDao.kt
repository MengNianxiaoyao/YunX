package com.yunx.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UCAccountDao {

    @Query("SELECT * FROM uc_account WHERE id = 'uc'")
    fun observeAccount(): Flow<UCAccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: UCAccountEntity)

    @Query("SELECT * FROM uc_account WHERE id = 'uc'")
    suspend fun getAccount(): UCAccountEntity?

    @Query("DELETE FROM uc_account WHERE id = 'uc'")
    suspend fun clear()

    /** 标记登录态失效（保留行，仅写时间戳；不涉及加密字段） */
    @Query("UPDATE uc_account SET invalidAt = :ts WHERE id = 'uc'")
    suspend fun markInvalid(ts: Long)
}