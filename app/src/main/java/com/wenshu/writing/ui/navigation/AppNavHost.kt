package com.wenshu.writing.ui.navigation

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.feature.ai.AiChatScreen
import com.wenshu.writing.feature.ai.AiProviderScreen
import com.wenshu.writing.feature.editor.ChapterEditorScreen
import com.wenshu.writing.feature.library.LibraryScreen
import com.wenshu.writing.feature.memo.MemoEditorScreen
import com.wenshu.writing.feature.memo.MemoScreen
import com.wenshu.writing.feature.pipeline.PipelineScreen
import com.wenshu.writing.feature.settings.AppearanceScreen
import com.wenshu.writing.feature.settings.SettingsScreen
import com.wenshu.writing.feature.skill.SkillEditorScreen
import com.wenshu.writing.feature.skill.SkillScreen
import com.wenshu.writing.feature.splash.SplashScreen
import com.wenshu.writing.feature.works.ChapterListScreen
import com.wenshu.writing.feature.works.WorksScreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 输入法弹出时隐藏底部导航栏，避免它的高度叠加到 imePadding 上产生大片空白。
    val imeVisible = WindowInsets.isImeVisible
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute } && !imeVisible

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    navController = navController
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onFinished = {
                        navController.navigate(Routes.WORKS) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.WORKS) {
                WorksScreen(
                    container = container,
                    onOpenNovel = { novelId ->
                        navController.navigate(Routes.chapterList(novelId))
                    }
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.AI) {
                AiChatScreen(
                    container = container,
                    onOpenProviders = {
                        navController.navigate(Routes.AI_PROVIDERS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenPipeline = { novelId, skillId ->
                        navController.navigate(Routes.pipeline(novelId, skillId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onOpenAiProviders = {
                        navController.navigate(Routes.AI_PROVIDERS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSkills = {
                        navController.navigate(Routes.SKILLS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenAppearance = {
                        navController.navigate(Routes.APPEARANCE) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.APPEARANCE) {
                AppearanceScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.MEMO) {
                MemoScreen(
                    container = container,
                    onOpenNote = { noteId ->
                        navController.navigate(Routes.memoEditor(noteId))
                    }
                )
            }
            composable(
                route = Routes.MEMO_EDITOR,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType })
            ) { entry ->
                MemoEditorScreen(
                    container = container,
                    noteId = entry.arguments?.getLong("noteId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SKILLS) {
                SkillScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenSkill = { skillId ->
                        navController.navigate(Routes.skillEditor(skillId))
                    }
                )
            }
            composable(
                route = Routes.SKILL_EDITOR,
                arguments = listOf(navArgument("skillId") { type = NavType.LongType })
            ) { entry ->
                SkillEditorScreen(
                    container = container,
                    skillId = entry.arguments?.getLong("skillId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.AI_PROVIDERS) {
                AiProviderScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.CHAPTER_LIST,
                arguments = listOf(navArgument("novelId") { type = NavType.LongType })
            ) { entry ->
                ChapterListScreen(
                    container = container,
                    novelId = entry.arguments?.getLong("novelId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenChapter = { chapterId ->
                        navController.navigate(Routes.chapterEditor(chapterId))
                    }
                )
            }
            composable(
                route = Routes.CHAPTER_EDITOR,
                arguments = listOf(navArgument("chapterId") { type = NavType.LongType })
            ) { entry ->
                ChapterEditorScreen(
                    container = container,
                    chapterId = entry.arguments?.getLong("chapterId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.PIPELINE,
                arguments = listOf(
                    navArgument("novelId") { type = NavType.LongType },
                    navArgument("skillId") { type = NavType.LongType }
                )
            ) { entry ->
                PipelineScreen(
                    container = container,
                    novelId = entry.arguments?.getLong("novelId") ?: 0L,
                    skillId = entry.arguments?.getLong("skillId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    currentRoute: String?,
    navController: NavController
) {
    NavigationBar {
        topLevelDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label) }
            )
        }
    }
}
