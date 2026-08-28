package com.yunx.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface C139AccountDao {

    @Query("SELECT * FROM c139_account WHERE id = 'c139'")
    fun observeAccount(): Flow<C139AccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: C139AccountEntity)

    @Query("SELECT * FROM c139_account WHERE id = 'c139'")
    suspend fun getAccount(): C139AccountEntity?

    @Query("DELETE FROM c139_account WHERE id = 'c139'")
    suspend fun clear()

    /** 标记登录态失效（保留行，仅写时间戳；不涉及加密字段） */
    @Query("UPDATE c139_account SET invalidAt = :ts WHERE id = 'c139'")
    suspend fun markInvalid(ts: Long)
}
