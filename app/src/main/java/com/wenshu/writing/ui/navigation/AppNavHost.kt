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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.feature.ai.AiChatScreen
import com.wenshu.writing.feature.ai.AiChatViewModel
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
import com.wenshu.writing.feature.update.UPDATE_NOTES
import com.wenshu.writing.feature.update.UpdateAnnouncementDialog
import com.wenshu.writing.feature.update.UpdateAnnouncementStore
import com.wenshu.writing.feature.update.UpdateNote
import com.wenshu.writing.feature.update.currentVersionName
import com.wenshu.writing.feature.works.ChapterListScreen
import com.wenshu.writing.feature.works.TrashScreen
import com.wenshu.writing.feature.works.WorksScreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    // AI 对话的 ViewModel 由 AppContainer 持有（进程级单例）：
    // 切换页面、App 退到后台、Activity 重建都不会打断正在进行的生成任务。
    val aiChatViewModel: AiChatViewModel = container.aiChatViewModel
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 输入法弹出时隐藏底部导航栏，避免它的高度叠加到 imePadding 上产生大片空白。
    val imeVisible = WindowInsets.isImeVisible
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute } && !imeVisible

    // 点击通知栏「返回对话」→ 切回 AI 会话页（保留各 Tab 状态）。
    // 冷启动时需等启动页结束、导航图就绪后再跳转。
    val openChatPage: () -> Unit = {
        navController.navigate(Routes.AI) {
            popUpTo(Routes.WORKS) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(Unit) {
        container.openChatRequests.collect {
            if (navController.currentDestination?.route != Routes.SPLASH) {
                container.consumePendingOpenChat()
                openChatPage()
            }
        }
    }
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute != Routes.SPLASH && container.consumePendingOpenChat()) {
            openChatPage()
        }
    }

    // 版本更新公告：同一版本首次打开时自动弹出一次，重装或升级后重新弹出。
    val context = LocalContext.current
    var updateNote by remember { mutableStateOf<UpdateNote?>(null) }
    LaunchedEffect(Unit) {
        val version = currentVersionName(context) ?: return@LaunchedEffect
        if (!UpdateAnnouncementStore.hasSeen(context, version)) {
            UpdateAnnouncementStore.markSeen(context, version)
            updateNote = UPDATE_NOTES.firstOrNull { it.version == version }
                ?: UpdateNote(version, "本次更新已安装", emptyList())
        }
    }

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
                    },
                    onOpenTrash = {
                        navController.navigate(Routes.WORKS_TRASH) {
                            launchSingleTop = true
                        }
                    },
                    onOpenChat = {
                        navController.navigate(Routes.AI) {
                            popUpTo(Routes.WORKS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Routes.WORKS_TRASH) {
                TrashScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
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
                    viewModel = aiChatViewModel,
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

    updateNote?.let { note ->
        UpdateAnnouncementDialog(
            note = note,
            onDismiss = { updateNote = null }
        )
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
                        // 顶部 tab 以书稿页为栈底：切换时保存/恢复各页状态，
                        // 避免每次切换都重建页面、重复全量加载列表。
                        popUpTo(Routes.WORKS) {
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
