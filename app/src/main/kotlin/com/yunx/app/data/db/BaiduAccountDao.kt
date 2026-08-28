package com.yunx.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BaiduAccountDao {

    @Query("SELECT * FROM baidu_account WHERE id = 'baidu'")
    fun observeAccount(): Flow<BaiduAccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: BaiduAccountEntity)

    @Query("SELECT * FROM baidu_account WHERE id = 'baidu'")
    suspend fun getAccount(): BaiduAccountEntity?

    @Query("DELETE FROM baidu_account WHERE id = 'baidu'")
    suspend fun clear()

    /** 标记登录态失效（保留行，仅写时间戳；不涉及加密字段） */
    @Query("UPDATE baidu_account SET invalidAt = :ts WHERE id = 'baidu'")
    suspend fun markInvalid(ts: Long)
}