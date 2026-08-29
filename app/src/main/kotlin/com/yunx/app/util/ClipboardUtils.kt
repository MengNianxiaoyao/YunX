package com.yunx.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** 复制文本到系统剪贴板（label 仅作为剪贴板条目的元数据描述，不直接展示） */
fun copyToClipboard(context: Context, text: String, label: String = "text") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
