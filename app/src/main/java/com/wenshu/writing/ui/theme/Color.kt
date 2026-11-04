package com.wenshu.writing.ui.theme

import androidx.compose.ui.graphics.Color

// 墨蓝（默认）
val InkBlue = Color(0xFF2E3B52)
val InkBlueLight = Color(0xFF4A5A78)
val InkBlueContainerLight = Color(0xFFDCE3F2)

// 暖纸色
val PaperBackground = Color(0xFFFAF8F3)
val PaperSurface = Color(0xFFFFFDF9)
val PaperVariant = Color(0xFFF1ECE3)

// 点缀金
val AccentGold = Color(0xFFB6895B)
val AccentGoldContainer = Color(0xFFF2E2D2)

val TextPrimary = Color(0xFF1C2026)
val TextSecondary = Color(0xFF5C6068)
val OutlineWarm = Color(0xFFCFC7B9)

// 深色模式
val DarkBackground = Color(0xFF111318)
val DarkSurface = Color(0xFF191C22)
val DarkSurfaceVariant = Color(0xFF262A32)
val DarkPrimary = Color(0xFFB4C2DE)
val DarkPrimaryContainer = Color(0xFF3A4760)
val DarkSecondary = Color(0xFFD7B08A)
val DarkSecondaryContainer = Color(0xFF4A3A2B)
val DarkTextPrimary = Color(0xFFE8E5DE)
val DarkTextSecondary = Color(0xFFB9B5AC)
val DarkOutline = Color(0xFF4B505A)

/** 主题色预设：浅色模式主色 + 深色模式主色 + 浅色模式容器色。 */
data class ThemeColorPreset(
    val key: String,
    val label: String,
    val lightPrimary: Color,
    val lightPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkPrimaryContainer: Color
)

val ThemeColorPresets = listOf(
    ThemeColorPreset("INK", "墨蓝", InkBlue, InkBlueContainerLight, DarkPrimary, DarkPrimaryContainer),
    ThemeColorPreset("VIOLET", "淡紫", Color(0xFF6B5CE7), Color(0xFFE4E0FF), Color(0xFFC7BFFF), Color(0xFF4A3D9E)),
    ThemeColorPreset("BLUE", "雅蓝", Color(0xFF2F6FED), Color(0xFFD9E4FF), Color(0xFFA9C4FF), Color(0xFF1E4DA8)),
    ThemeColorPreset("TEAL", "青", Color(0xFF0E7C86), Color(0xFFD0F0F2), Color(0xFF7FD4DC), Color(0xFF0B5560)),
    ThemeColorPreset("GREEN", "翠", Color(0xFF1E8A4C), Color(0xFFCFEFD9), Color(0xFF8FD8A8), Color(0xFF146036)),
    ThemeColorPreset("ROSE", "茜", Color(0xFFD34E7E), Color(0xFFFBDCE7), Color(0xFFF2A0BE), Color(0xFF8E2F52))
)

fun themeColorPreset(key: String): ThemeColorPreset =
    ThemeColorPresets.firstOrNull { it.key == key } ?: ThemeColorPresets.first()

/** 背景色预设（浅色模式）；空 key 表示跟随主题。深色模式下背景色跟随深色主题。 */
data class BackgroundColorPreset(val key: String, val label: String, val color: Color)

val BackgroundColorPresets = listOf(
    BackgroundColorPreset("", "跟随主题", PaperBackground),
    BackgroundColorPreset("MINT", "薄荷", Color(0xFFF1F7F1)),
    BackgroundColorPreset("CREAM", "暖黄", Color(0xFFFBF6E9)),
    BackgroundColorPreset("MIST", "雾蓝", Color(0xFFF0F4FA)),
    BackgroundColorPreset("BLUSH", "粉白", Color(0xFFFBF2F4)),
    BackgroundColorPreset("PEACH", "蜜桃", Color(0xFFFBF3EE)),
    BackgroundColorPreset("LILAC", "淡紫", Color(0xFFF6F3FB))
)

fun backgroundColorPreset(key: String): BackgroundColorPreset =
    BackgroundColorPresets.firstOrNull { it.key == key } ?: BackgroundColorPresets.first()
