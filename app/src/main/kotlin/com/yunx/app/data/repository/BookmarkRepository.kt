package com.yunx.app.data.repository

import com.yunx.app.data.db.BookmarkDao
import com.yunx.app.data.db.BookmarkEntity
import com.yunx.app.data.security.AndroidKeystoreCredentialCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Encrypts bookmark passcodes at the Room boundary. */
class BookmarkRepository(
    private val dao: BookmarkDao
) {
    private val cipher = AndroidKeystoreCredentialCipher()
    fun observeAll(): Flow<List<BookmarkEntity>> = dao.observeAll().map { bookmarks ->
        bookmarks.map { bookmark ->
            bookmark.copy(
                pwd = runCatching { cipher.decrypt(bookmark.pwd, PURPOSE) }.getOrDefault("")
            )
        }
    }

    fun observeCategories(): Flow<List<String>> = dao.observeCategories()

    suspend fun insert(bookmark: BookmarkEntity): Long = dao.insert(
        bookmark.copy(pwd = cipher.encrypt(bookmark.pwd, PURPOSE))
    )

    suspend fun updateCategory(id: Long, category: String) = dao.updateCategory(id, category)

    suspend fun delete(id: Long) = dao.delete(id)

    private companion object {
        const val PURPOSE = "bookmark.passcode"
    }
}
