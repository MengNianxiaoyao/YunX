package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.BookmarkEntity
import com.yunx.app.data.repository.BookmarkRepository
import com.yunx.app.ui.SnackbarController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 网盘链接收藏 ViewModel：收藏列表（Room Flow → StateFlow）+ 分类合并 + CRUD。
 */
class BookmarkViewModel(private val repository: BookmarkRepository) : ViewModel() {

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 分类列表：预置分类 + 数据库中已出现的自定义分类（去重，保持预置在前） */
    val categories: StateFlow<List<String>> = repository.observeCategories()
        .map { db -> (BookmarkEntity.PRESET_CATEGORIES + db).distinct() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BookmarkEntity.PRESET_CATEGORIES
        )

    fun addBookmark(
        link: String,
        title: String,
        platform: String,
        pwd: String,
        category: String
    ) {
        val trimmed = link.trim()
        if (trimmed.isBlank()) {
            SnackbarController.show("请输入网盘链接")
            return
        }
        viewModelScope.launch {
            repository.insert(
                BookmarkEntity(
                    link = trimmed,
                    title = title.trim(),
                    platform = platform,
                    pwd = pwd.trim(),
                    category = category.ifBlank { BookmarkEntity.DEFAULT_CATEGORY }
                )
            )
            SnackbarController.show("已收藏")
        }
    }

    fun updateCategory(id: Long, category: String) {
        val cat = category.ifBlank { BookmarkEntity.DEFAULT_CATEGORY }
        viewModelScope.launch {
            repository.updateCategory(id, cat)
            SnackbarController.show("已移动到「$cat」")
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            SnackbarController.show("已删除")
        }
    }

    class Factory(private val repository: BookmarkRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BookmarkViewModel::class.java))
            return BookmarkViewModel(repository) as T
        }
    }
}
