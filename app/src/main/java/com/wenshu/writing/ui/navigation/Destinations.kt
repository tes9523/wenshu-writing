package com.wenshu.writing.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val SPLASH = "splash"
    const val WORKS = "works"
    const val LIBRARY = "library"
    const val AI = "ai"
    const val MEMO = "memo"
    const val SETTINGS = "settings"

    const val AI_PROVIDERS = "ai_providers"
    const val APPEARANCE = "settings/appearance"
    const val SKILLS = "skills"
    const val SKILL_EDITOR = "skill_editor/{skillId}"
    const val MEMO_EDITOR = "memo_note/{noteId}"

    const val CHAPTER_LIST = "novel/{novelId}/chapters"
    const val CHAPTER_EDITOR = "chapter/{chapterId}"
    const val PIPELINE = "pipeline/{novelId}/{skillId}"

    fun chapterList(novelId: Long) = "novel/$novelId/chapters"
    fun chapterEditor(chapterId: Long) = "chapter/$chapterId"
    fun skillEditor(skillId: Long) = "skill_editor/$skillId"
    fun memoEditor(noteId: Long) = "memo_note/$noteId"
    fun pipeline(novelId: Long, skillId: Long) = "pipeline/$novelId/$skillId"
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
    TopLevelDestination(Routes.MEMO, "备忘录", Icons.Outlined.Lightbulb),
    TopLevelDestination(Routes.SETTINGS, "设置", Icons.Outlined.Settings)
)
