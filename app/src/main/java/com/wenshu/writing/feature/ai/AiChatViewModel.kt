package com.wenshu.writing.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.ChatRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.data.repository.SkillRepository
import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.ApiProviderType
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.ChatSession
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.Skill
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiChatUiState(
    val providerName: String? = null,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val apiType: ApiProviderType = ApiProviderType.OPENAI,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val novels: List<Novel> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val selectedNovelId: Long? = null,
    val selectedChapterId: Long? = null,
    val skills: List<Skill> = emptyList(),
    val mentionQuery: String? = null,
    val mentionResults: List<Skill> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: Long? = null,
    val showSessionList: Boolean = false,
    /** 选中内置流水线 Skill 后由界面跳转到流水线页面。 */
    val pipelineSkill: Skill? = null,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val novelRepository: NovelRepository,
    private val skillRepository: SkillRepository,
    private val chatRepository: ChatRepository,
    private val aiClient: AiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        // 只读取当前激活的提供商，切换逻辑统一放在「AI 提供商」页面，避免对话页自动改变激活项。
        viewModelScope.launch {
            combine(
                preferencesRepository.aiProfiles,
                preferencesRepository.settings
            ) { profiles, settings -> profiles to settings }
                .collect { (profiles, settings) ->
                    val active = profiles.firstOrNull { it.id == settings.activeAiProfileId }
                        ?: profiles.firstOrNull()
                    _uiState.update {
                        it.copy(
                            providerName = active?.name,
                            baseUrl = active?.baseUrl ?: "",
                            apiKey = active?.apiKey ?: "",
                            model = active?.model ?: "",
                            apiType = active?.apiType ?: ApiProviderType.OPENAI
                        )
                    }
                }
        }

        viewModelScope.launch {
            skillRepository.skills.collect { skills ->
                _uiState.update { current ->
                    val query = current.mentionQuery
                    current.copy(
                        skills = skills,
                        mentionResults = if (query == null) {
                            emptyList()
                        } else {
                            filterMentionSkills(skills, query)
                        }
                    )
                }
            }
        }

        // 会话列表永久持久化；默认打开最近一次会话，没有则不创建空会话。
        viewModelScope.launch {
            chatRepository.sessions.collect { sessions ->
                _uiState.update { current ->
                    val activeId = current.activeSessionId
                        ?.takeIf { id -> sessions.any { it.id == id } }
                    current.copy(sessions = sessions, activeSessionId = activeId)
                }
            }
        }

        viewModelScope.launch {
            novelRepository.novels.collect { novels ->
                _uiState.update { current ->
                    val selectedNovelId = current.selectedNovelId
                        ?.takeIf { id -> novels.any { it.id == id } }
                        ?: novels.firstOrNull()?.id
                    current.copy(novels = novels, selectedNovelId = selectedNovelId)
                }
            }
        }

        viewModelScope.launch {
            _uiState
                .map { it.selectedNovelId }
                .distinctUntilChanged()
                .collectLatest { novelId ->
                    if (novelId == null) {
                        _uiState.update {
                            it.copy(chapters = emptyList(), selectedChapterId = null)
                        }
                    } else {
                        novelRepository.observeChapters(novelId).collect { chapters ->
                            _uiState.update { current ->
                                val selectedChapterId = current.selectedChapterId
                                    ?.takeIf { id -> chapters.any { it.id == id } }
                                    ?: chapters.firstOrNull()?.id
                                current.copy(
                                    chapters = chapters,
                                    selectedChapterId = selectedChapterId
                                )
                            }
                        }
                    }
                }
        }

        // 会话上下文完全隔离：切换会话只加载该会话自己的消息。
        viewModelScope.launch {
            _uiState
                .map { it.activeSessionId }
                .distinctUntilChanged()
                .flatMapLatest { sessionId ->
                    if (sessionId == null) flowOf(emptyList()) else chatRepository.observeMessages(sessionId)
                }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
        }
    }

    fun updateInput(value: String) {
        _uiState.update { state ->
            val query = mentionQueryOf(value)
            state.copy(
                input = value,
                mentionQuery = query,
                mentionResults = if (query == null) {
                    emptyList()
                } else {
                    filterMentionSkills(state.skills, query)
                }
            )
        }
    }

    /**
     * 选中 @ 面板中的 Skill：普通技能立即激活并注入提示词；
     * 内置流水线技能则交给界面跳转到分阶段流程。
     */
    fun selectMentionSkill(skillId: Long) {
        val state = _uiState.value
        val skill = state.skills.firstOrNull { it.id == skillId } ?: return
        val atIndex = state.input.lastIndexOf('@')
        val newInput = if (atIndex >= 0) state.input.substring(0, atIndex) else state.input

        if (skill.isPipeline) {
            _uiState.update { current ->
                current.copy(
                    input = newInput,
                    mentionQuery = null,
                    mentionResults = emptyList(),
                    pipelineSkill = skill
                )
            }
            return
        }

        _uiState.update { current ->
            current.copy(
                input = newInput,
                mentionQuery = null,
                mentionResults = emptyList(),
                skills = current.skills.map {
                    if (it.id == skillId) it.copy(enabled = true) else it
                },
                message = "已激活技能「${skill.name}」"
            )
        }
        viewModelScope.launch { skillRepository.setEnabled(skillId, true) }
    }

    fun consumePipelineSkill() {
        _uiState.update { it.copy(pipelineSkill = null) }
    }

    fun dismissMentionPanel() {
        _uiState.update { it.copy(mentionQuery = null, mentionResults = emptyList()) }
    }

    fun selectNovel(novelId: Long) {
        _uiState.update { it.copy(selectedNovelId = novelId, selectedChapterId = null) }
    }

    fun selectChapter(chapterId: Long) {
        _uiState.update { it.copy(selectedChapterId = chapterId) }
    }

    fun openSessionList() {
        _uiState.update { it.copy(showSessionList = true) }
    }

    fun closeSessionList() {
        _uiState.update { it.copy(showSessionList = false) }
    }

    fun selectSession(sessionId: Long) {
        _uiState.update { it.copy(activeSessionId = sessionId, showSessionList = false) }
    }

    /** 新建会话；bookId 为空表示全局通用会话。 */
    fun createSession(bookId: Long?) {
        viewModelScope.launch {
            val novelTitle = _uiState.value.novels.firstOrNull { it.id == bookId }?.title
            val title = novelTitle?.let { "《$it》新对话" } ?: "全局新对话"
            val id = chatRepository.createSession(title, bookId)
            _uiState.update { it.copy(activeSessionId = id, showSessionList = false) }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            _uiState.update { current ->
                if (current.activeSessionId == sessionId) {
                    current.copy(activeSessionId = null, messages = emptyList())
                } else {
                    current
                }
            }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.input.trim()
        if (text.isEmpty() || state.isSending) return

        if (state.baseUrl.isBlank() || state.apiKey.isBlank() || state.model.isBlank()) {
            _uiState.update {
                it.copy(message = "请先在「设置 → AI 提供商」中配置可用的提供商")
            }
            return
        }

        _uiState.update {
            it.copy(
                input = "",
                isSending = true,
                mentionQuery = null,
                mentionResults = emptyList()
            )
        }

        val config = AiConfig(
            baseUrl = state.baseUrl.trim(),
            apiKey = state.apiKey.trim(),
            model = state.model.trim(),
            apiType = state.apiType
        )
        val systemPrompt = buildSystemPrompt(state.skills)
        viewModelScope.launch {
            val sessionId = ensureSession(firstMessage = text, bookId = state.selectedNovelId)
            chatRepository.appendMessage(sessionId, ChatRole.USER, text)
            val history = chatRepository.getMessages(sessionId)
            aiClient.chat(config = config, history = history, systemPrompt = systemPrompt)
                .onSuccess { reply ->
                    chatRepository.appendMessage(sessionId, ChatRole.ASSISTANT, reply)
                }
                .onFailure { error ->
                    chatRepository.appendMessage(
                        sessionId = sessionId,
                        role = ChatRole.ASSISTANT,
                        content = error.message ?: "请求失败，请检查网络或 API 配置",
                        isError = true
                    )
                }
            _uiState.update { it.copy(isSending = false) }
        }
    }

    /** 首次发送时创建会话，标题取首句前 20 个字便于识别。 */
    private suspend fun ensureSession(firstMessage: String, bookId: Long?): Long {
        val existing = _uiState.value.activeSessionId
        if (existing != null) return existing
        val id = chatRepository.createSession(firstMessage.take(20), bookId)
        _uiState.update { it.copy(activeSessionId = id) }
        return id
    }

    fun insertToChapter(content: String) {
        val state = _uiState.value
        val chapterId = state.selectedChapterId
        if (chapterId == null) {
            _uiState.update { it.copy(message = "请先选择要插入的章节") }
            return
        }
        val chapterTitle = state.chapters.firstOrNull { it.id == chapterId }?.title
        viewModelScope.launch {
            val title = novelRepository.appendToChapter(chapterId, content)
            _uiState.update {
                it.copy(
                    message = if (title == null) {
                        "插入失败：章节不存在"
                    } else {
                        "已插入到「${chapterTitle ?: title}」"
                    }
                )
            }
        }
    }

    fun clearConversation() {
        val sessionId = _uiState.value.activeSessionId ?: return
        viewModelScope.launch { chatRepository.clearMessages(sessionId) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        /** 取出输入框末尾正在输入的 @ 关键字；不处于 @ 语境时返回 null。 */
        private fun mentionQueryOf(text: String): String? {
            val atIndex = text.lastIndexOf('@')
            if (atIndex < 0) return null
            val before = text.getOrNull(atIndex - 1)
            if (before != null && !before.isWhitespace()) return null
            val query = text.substring(atIndex + 1)
            if (query.any { it.isWhitespace() }) return null
            return query
        }

        private fun filterMentionSkills(skills: List<Skill>, query: String): List<Skill> {
            val keyword = query.trim()
            return skills
                .filter {
                    keyword.isEmpty() ||
                        it.name.contains(keyword, ignoreCase = true) ||
                        it.description.contains(keyword, ignoreCase = true)
                }
        }

        /** 合并所有已激活 Skill 的主提示词与参考素材，追加到系统提示词后。 */
        private fun buildSystemPrompt(skills: List<Skill>): String {
            val active = skills.filter { it.enabled && !it.isPipeline }
            if (active.isEmpty()) return AiClient.DEFAULT_SYSTEM_PROMPT
            return buildString {
                append(AiClient.DEFAULT_SYSTEM_PROMPT)
                appendLine()
                appendLine()
                appendLine("以下是当前已激活的写作技能，请在回答时严格遵循：")
                active.forEach { skill ->
                    appendLine(skill.toPromptSection())
                    appendLine()
                }
            }
        }

        fun factory(
            preferencesRepository: PreferencesRepository,
            novelRepository: NovelRepository,
            skillRepository: SkillRepository,
            chatRepository: ChatRepository,
            aiClient: AiClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AiChatViewModel(
                    preferencesRepository,
                    novelRepository,
                    skillRepository,
                    chatRepository,
                    aiClient
                ) as T
        }
    }
}
