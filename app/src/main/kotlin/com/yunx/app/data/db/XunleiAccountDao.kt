package com.yunx.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface XunleiAccountDao {

    @Query("SELECT * FROM xunlei_account WHERE id = 'xunlei'")
    fun observeAccount(): Flow<XunleiAccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: XunleiAccountEntity)

    @Query("SELECT * FROM xunlei_account WHERE id = 'xunlei'")
    suspend fun getAccount(): XunleiAccountEntity?

    @Query("DELETE FROM xunlei_account WHERE id = 'xunlei'")
    suspend fun clear()

    /** 标记登录态失效（保留行，仅写时间戳；不涉及加密字段） */
    @Query("UPDATE xunlei_account SET invalidAt = :ts WHERE id = 'xunlei'")
    suspend fun markInvalid(ts: Long)
}