package com.yunx.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 字节数转人类可读大小（保留 1 位小数，B/KB/MB/GB/TB） */
fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return String.format("%.1f %s", value, units[i])
}

private val fileDateInputPatterns = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    "yyyy-MM-dd'T'HH:mm:ssZ",
    "yyyy-MM-dd'T'HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd"
)

/** 统一文件时间显示为 yyyy-MM-dd HH:mm，兼容各网盘返回的常见时间格式。 */
fun formatFileModifyTime(value: String): String {
    val raw = value.trim()
    if (raw.isEmpty()) return raw

    raw.toLongOrNull()?.let { timestamp ->
        val millis = if (raw.length <= 10) timestamp * 1000 else timestamp
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }

    val normalized = raw.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
    fileDateInputPatterns.forEach { pattern ->
        runCatching { SimpleDateFormat(pattern, Locale.US).parse(normalized) }.getOrNull()?.let {
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(it)
        }
    }
    return value
}
