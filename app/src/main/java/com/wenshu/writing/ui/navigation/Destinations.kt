package com.wenshu.writing.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val WORKS = "works"
    const val LIBRARY = "library"
    const val AI = "ai"
    const val SETTINGS = "settings"

    const val CHAPTER_LIST = "novel/{novelId}/chapters"
    const val CHAPTER_EDITOR = "chapter/{chapterId}"

    fun chapterList(novelId: Long) = "novel/$novelId/chapters"
    fun chapterEditor(chapterId: Long) = "chapter/$chapterId"
}

data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val topLevelDestinations = listOf(
    TopLevelDestination(Routes.WORKS, "书稿", Icons.Outlined.AutoStories),
    TopLevelDestination(Routes.LIBRARY, "设定库", Icons.Outlined.Groups),
    TopLevelDestination(Routes.AI, "AI 对话", Icons.Outlined.AutoAwesome),
    TopLevelDestination(Routes.SETTINGS, "设置", Icons.Outlined.Settings)
)
