package com.wenshu.writing.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.data.repository.SkillRepository
import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.ApiProviderType
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.Skill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val message: String? = null
)

class AiChatViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val novelRepository: NovelRepository,
    private val skillRepository: SkillRepository,
    private val aiClient: AiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var nextMessageId = 0L

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

    /** 选中 @ 面板中的 Skill：移除输入中的 @ 片段，并立即把该技能标记为激活。 */
    fun selectMentionSkill(skillId: Long) {
        val state = _uiState.value
        val skill = state.skills.firstOrNull { it.id == skillId } ?: return
        val atIndex = state.input.lastIndexOf('@')
        val newInput = if (atIndex >= 0) state.input.substring(0, atIndex) else state.input

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

    fun dismissMentionPanel() {
        _uiState.update { it.copy(mentionQuery = null, mentionResults = emptyList()) }
    }

    fun selectNovel(novelId: Long) {
        _uiState.update { it.copy(selectedNovelId = novelId, selectedChapterId = null) }
    }

    fun selectChapter(chapterId: Long) {
        _uiState.update { it.copy(selectedChapterId = chapterId) }
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

        val userMessage = ChatMessage(id = nextMessageId++, role = ChatRole.USER, content = text)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
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
            val result = aiClient.chat(
                config = config,
                history = _uiState.value.messages,
                systemPrompt = systemPrompt
            )
            result.onSuccess { reply ->
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(
                            id = nextMessageId++,
                            role = ChatRole.ASSISTANT,
                            content = reply
                        ),
                        isSending = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(
                            id = nextMessageId++,
                            role = ChatRole.ASSISTANT,
                            content = error.message ?: "请求失败，请检查网络或 API 配置",
                            isError = true
                        ),
                        isSending = false
                    )
                }
            }
        }
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
        _uiState.update { it.copy(messages = emptyList()) }
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
            val active = skills.filter { it.enabled }
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
            aiClient: AiClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AiChatViewModel(
                    preferencesRepository,
                    novelRepository,
                    skillRepository,
                    aiClient
                ) as T
        }
    }
}
