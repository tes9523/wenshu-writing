package com.wenshu.writing.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
private val shortDateFormatter = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return dateFormatter.format(Date(timestamp))
}

fun formatShortTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return shortDateFormatter.format(Date(timestamp))
}

fun formatWordCount(count: Int): String = when {
    count >= 10_000 -> String.format(Locale.CHINA, "%.1f 万字", count / 10_000.0)
    else -> "${count} 字"
}
