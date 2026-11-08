package com.wenshu.writing.di

import android.content.Context
import com.wenshu.writing.data.local.AppDatabase
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.ChatRepository
import com.wenshu.writing.data.repository.FileSyncManager
import com.wenshu.writing.data.repository.MemoRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PipelineRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.data.repository.SettingRepository
import com.wenshu.writing.data.repository.SkillRepository
import com.wenshu.writing.feature.ai.AiChatViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 轻量依赖容器。后续接入 Hilt 或拆分模块时，只需替换此处的装配逻辑。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database: AppDatabase = AppDatabase.getInstance(appContext)

    /** 通知栏点击「返回对话」时发出的跳转请求；AppNavHost 订阅后切到 AI 会话页。 */
    private val _openChatRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val openChatRequests = _openChatRequests.asSharedFlow()

    /** 冷启动时 Notification 先于界面就绪，用标记位补一次跳转。 */
    private var pendingOpenChat = false

    fun requestOpenChat() {
        pendingOpenChat = true
        _openChatRequests.tryEmit(Unit)
    }

    fun consumePendingOpenChat(): Boolean {
        val value = pendingOpenChat
        pendingOpenChat = false
        return value
    }

    /** wenshu 外部目录双向同步（书稿文件夹、设定文件）。 */
    val fileSyncManager = FileSyncManager(appContext) { id ->
        database.novelDao().getById(id)?.title
    }

    val novelRepository = NovelRepository(
        novelDao = database.novelDao(),
        chapterDao = database.chapterDao(),
        settingDao = database.settingDao(),
        memoDao = database.memoDao(),
        fileSync = fileSyncManager
    )

    val settingRepository = SettingRepository(
        settingDao = database.settingDao(),
        fileSync = fileSyncManager
    )

    val preferencesRepository = PreferencesRepository(
        settingsDao = database.settingsDao()
    )

    val memoRepository = MemoRepository(
        memoDao = database.memoDao()
    )

    val skillRepository = SkillRepository(
        skillDao = database.skillDao(),
        context = appContext
    )

    val chatRepository = ChatRepository(
        chatDao = database.chatDao()
    )

    val pipelineRepository = PipelineRepository(
        pipelineDao = database.pipelineDao(),
        settingRepository = settingRepository
    )

    val aiClient = AiClient()

    /**
     * 进程级 AI 对话 ViewModel：不随页面或 Activity 销毁而清空，
     * App 切到后台、Activity 重建时正在进行的生成任务继续运行，返回后接续渲染。
     */
    val aiChatViewModel: AiChatViewModel by lazy {
        AiChatViewModel(
            context = appContext,
            preferencesRepository = preferencesRepository,
            novelRepository = novelRepository,
            skillRepository = skillRepository,
            chatRepository = chatRepository,
            pipelineRepository = pipelineRepository,
            settingRepository = settingRepository,
            memoRepository = memoRepository,
            aiClient = aiClient
        )
    }
}
