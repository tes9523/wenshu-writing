package com.wenshu.writing.di

import android.content.Context
import com.wenshu.writing.data.local.AppDatabase
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.ChatRepository
import com.wenshu.writing.data.repository.MemoRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PipelineRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.data.repository.SettingRepository
import com.wenshu.writing.data.repository.SkillRepository
import com.wenshu.writing.feature.ai.AiChatViewModel

/**
 * 轻量依赖容器。后续接入 Hilt 或拆分模块时，只需替换此处的装配逻辑。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database: AppDatabase = AppDatabase.getInstance(appContext)

    val novelRepository = NovelRepository(
        novelDao = database.novelDao(),
        chapterDao = database.chapterDao(),
        settingDao = database.settingDao(),
        memoDao = database.memoDao()
    )

    val settingRepository = SettingRepository(
        settingDao = database.settingDao()
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
        settingDao = database.settingDao()
    )

    val aiClient = AiClient()

    /**
     * 进程级 AI 对话 ViewModel：不随页面或 Activity 销毁而清空，
     * App 切到后台、Activity 重建时正在进行的生成任务继续运行，返回后接续渲染。
     */
    val aiChatViewModel: AiChatViewModel by lazy {
        AiChatViewModel(
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
