package com.yunx.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuarkAccountDao {

    @Query("SELECT * FROM quark_account WHERE id = 'quark'")
    fun observeAccount(): Flow<QuarkAccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: QuarkAccountEntity)

    @Query("SELECT * FROM quark_account WHERE id = 'quark'")
    suspend fun getAccount(): QuarkAccountEntity?

    @Query("DELETE FROM quark_account WHERE id = 'quark'")
    suspend fun clear()

    /** 标记登录态失效（保留行，仅写时间戳；不涉及加密字段） */
    @Query("UPDATE quark_account SET invalidAt = :ts WHERE id = 'quark'")
    suspend fun markInvalid(ts: Long)
}