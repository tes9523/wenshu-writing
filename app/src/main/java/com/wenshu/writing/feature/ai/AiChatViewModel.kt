package com.wenshu.writing.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.ChatRepository
import com.wenshu.writing.data.repository.MemoRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PipelineRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.data.repository.SkillRepository
import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.ApiProviderType
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 保存 AI 消息的目标类型。 */
enum class SaveTarget {
    CHAPTER, OUTLINE, MEMO
}

/** AI 识别到的大纲与同名书稿，等待用户选择「新建另一本 / 打开已有书稿」。 */
data class PendingOutlineImport(
    val title: String,
    val outline: String,
    val existingNovelId: Long
)

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
    val selectedNovelId: Long? = null,
    val skills: List<Skill> = emptyList(),
    val mentionQuery: String? = null,
    val mentionResults: List<Skill> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: Long? = null,
    /** 选中内置流水线 Skill 后由界面跳转到流水线页面。 */
    val pipelineSkill: Skill? = null,
    val message: String? = null,
    val toast: String? = null,
    val pendingOutline: PendingOutlineImport? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val novelRepository: NovelRepository,
    private val skillRepository: SkillRepository,
    private val chatRepository: ChatRepository,
    private val pipelineRepository: PipelineRepository,
    private val memoRepository: MemoRepository,
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

    fun selectSession(sessionId: Long) {
        _uiState.update { it.copy(activeSessionId = sessionId) }
    }

    /** 新建会话；bookId 为空表示全局通用会话。 */
    fun createSession(bookId: Long?) {
        viewModelScope.launch {
            val novelTitle = _uiState.value.novels.firstOrNull { it.id == bookId }?.title
            val title = novelTitle?.let { "《$it》新对话" } ?: "全局新对话"
            val id = chatRepository.createSession(title, bookId)
            _uiState.update { it.copy(activeSessionId = id) }
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

        val config = state.config()
        val systemPrompt = buildSystemPrompt(state.skills)
        viewModelScope.launch {
            val sessionId = ensureSession(firstMessage = text, bookId = state.selectedNovelId)
            chatRepository.appendMessage(sessionId, ChatRole.USER, text)
            val history = chatRepository.getMessages(sessionId)
            aiClient.chat(config = config, history = history, systemPrompt = systemPrompt)
                .onSuccess { reply ->
                    handleAssistantReply(sessionId, reply, text)
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

    /**
     * 保存 AI 回复，并解析大纲建档标记。
     * 识别到完整大纲且书名唯一时自动建书；书名重复则交给界面弹窗选择。
     */
    private suspend fun handleAssistantReply(
        sessionId: Long,
        rawReply: String,
        userText: String
    ) {
        val (cleanReply, bookTitle) = extractOutlineMarker(rawReply)
        chatRepository.appendMessage(
            sessionId = sessionId,
            role = ChatRole.ASSISTANT,
            content = cleanReply.ifBlank { rawReply }
        )
        if (bookTitle.isNullOrBlank() || userText.isBlank()) return

        val existing = novelRepository.findNovelByTitle(bookTitle)
        if (existing == null) {
            createNovelFromOutline(sessionId, bookTitle, userText)
        } else {
            _uiState.update {
                it.copy(
                    pendingOutline = PendingOutlineImport(
                        title = bookTitle,
                        outline = userText,
                        existingNovelId = existing.id
                    )
                )
            }
        }
    }

    private suspend fun createNovelFromOutline(sessionId: Long, title: String, outline: String) {
        val novelId = novelRepository.createNovel(title, "由 AI 大纲导入")
        pipelineRepository.appendOutline(novelId, "AI导入大纲", outline, kind = "outline")
        preferencesRepository.setActiveNovel(novelId)
        chatRepository.bindSessionBook(sessionId, novelId)
        _uiState.update { it.copy(selectedNovelId = novelId) }
        chatRepository.appendMessage(
            sessionId = sessionId,
            role = ChatRole.ASSISTANT,
            content = "已根据你提供的大纲创建新书《$title》，可前往书稿查看"
        )
    }

    /** 书名重复时：新建另一本，或打开已有书稿并追加大纲。 */
    fun confirmPendingOutline(createNew: Boolean) {
        val pending = _uiState.value.pendingOutline ?: return
        val sessionId = _uiState.value.activeSessionId ?: return
        _uiState.update { it.copy(pendingOutline = null) }
        viewModelScope.launch {
            if (createNew) {
                createNovelFromOutline(sessionId, pending.title, pending.outline)
            } else {
                pipelineRepository.appendOutline(
                    pending.existingNovelId,
                    "AI导入大纲",
                    pending.outline,
                    kind = "outline"
                )
                preferencesRepository.setActiveNovel(pending.existingNovelId)
                chatRepository.bindSessionBook(sessionId, pending.existingNovelId)
                _uiState.update { it.copy(selectedNovelId = pending.existingNovelId) }
                chatRepository.appendMessage(
                    sessionId = sessionId,
                    role = ChatRole.ASSISTANT,
                    content = "已打开书稿《${pending.title}》，大纲已写入该书稿"
                )
            }
        }
    }

    fun dismissPendingOutline() {
        _uiState.update { it.copy(pendingOutline = null) }
    }

    /** 删除单条消息，仅影响会话记录。 */
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
            _uiState.update { it.copy(toast = "已删除该消息") }
        }
    }

    /** 复用该条回复之前的上下文重新生成，并替换当前 AI 消息。 */
    fun regenerateMessage(messageId: Long) {
        val state = _uiState.value
        if (state.isSending) return
        val sessionId = state.activeSessionId ?: return
        val config = state.config()
        if (config.baseUrl.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) {
            _uiState.update {
                it.copy(message = "请先在「设置 → AI 提供商」中配置可用的提供商")
            }
            return
        }
        val systemPrompt = buildSystemPrompt(state.skills)
        _uiState.update { it.copy(isSending = true) }

        viewModelScope.launch {
            val all = chatRepository.getMessages(sessionId)
            val index = all.indexOfFirst { it.id == messageId }
            if (index <= 0) {
                _uiState.update { it.copy(isSending = false) }
                return@launch
            }
            val history = all.take(index)
            val userText = history.lastOrNull { it.role == ChatRole.USER }?.content.orEmpty()
            chatRepository.deleteMessage(messageId)
            aiClient.chat(config = config, history = history, systemPrompt = systemPrompt)
                .onSuccess { reply -> handleAssistantReply(sessionId, reply, userText) }
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

    /** 将整条 AI 消息保存到指定书稿的章节 / 大纲 / 备忘录。 */
    fun saveMessageContent(
        content: String,
        novelId: Long,
        target: SaveTarget,
        onResult: (Boolean) -> Unit
    ) {
        val novelTitle = _uiState.value.novels.firstOrNull { it.id == novelId }?.title ?: "书稿"
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        viewModelScope.launch {
            val success = runCatching {
                when (target) {
                    SaveTarget.CHAPTER -> {
                        val chapterTitle = "AI生成章节_$stamp"
                        val chapterId = novelRepository.createChapter(novelId, chapterTitle)
                        novelRepository.saveChapter(chapterId, chapterTitle, content)
                    }

                    SaveTarget.OUTLINE -> {
                        pipelineRepository.appendOutline(
                            novelId,
                            "AI导入大纲_$stamp",
                            content,
                            kind = "outline"
                        )
                    }

                    SaveTarget.MEMO -> {
                        memoRepository.create(
                            title = "AI备忘_$stamp",
                            content = content,
                            bookId = novelId
                        )
                    }
                }
            }.isSuccess

            val toast = when {
                !success -> "保存失败，请重试"
                target == SaveTarget.CHAPTER -> "保存成功，已写入【$novelTitle】章节"
                target == SaveTarget.OUTLINE -> "保存成功，已追加到【$novelTitle】大纲"
                else -> "保存成功，已存入【$novelTitle】备忘录"
            }
            _uiState.update { it.copy(toast = toast) }
            onResult(success)
        }
    }

    fun clearConversation() {
        val sessionId = _uiState.value.activeSessionId ?: return
        viewModelScope.launch { chatRepository.clearMessages(sessionId) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toast = null) }
    }

    private fun AiChatUiState.config() = AiConfig(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        apiType = apiType
    )

    companion object {
        /** 模型输出的大纲建档标记；由 App 解析并创建书稿。 */
        private const val OUTLINE_MARKER = "<<WENSHU_NEW_BOOK>>"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val OUTLINE_PROTOCOL = """
【大纲建档协议】当用户消息是一份完整小说大纲（包含书名与明确的故事主线、分卷或章节规划）时：
1. 正常给出简短的确认或建议，不要复述大纲；
2. 在回复的最后单独一行输出：<<WENSHU_NEW_BOOK>>{"title":"提取的书名"}
3. 书名取大纲中的正式名称，去掉《》和多余空格；无法确定书名或用户输入不是有效大纲时，不要输出该标记；
4. 不要输出其他 JSON，也不要虚构 bookId，书稿创建由 App 完成。
""".trim()

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

        /** 合并所有已激活 Skill 的主提示词与参考素材，并始终附加大纲建档协议。 */
        private fun buildSystemPrompt(skills: List<Skill>): String = buildString {
            append(AiClient.DEFAULT_SYSTEM_PROMPT)
            appendLine()
            appendLine()
            appendLine(OUTLINE_PROTOCOL)
            val active = skills.filter { it.enabled && !it.isPipeline }
            if (active.isNotEmpty()) {
                appendLine()
                appendLine("以下是当前已激活的写作技能，请在回答时严格遵循：")
                active.forEach { skill ->
                    appendLine(skill.toPromptSection())
                    appendLine()
                }
            }
        }

        /** 剥离建档标记，返回给用户展示的文本与提取到的书名。 */
        private fun extractOutlineMarker(reply: String): Pair<String, String?> {
            val index = reply.indexOf(OUTLINE_MARKER)
            if (index < 0) return reply to null
            val after = reply.substring(index + OUTLINE_MARKER.length)
            val start = after.indexOf('{')
            val end = if (start >= 0) after.indexOf('}', startIndex = start + 1) else -1
            val title = if (start >= 0 && end > start) {
                runCatching {
                    val obj = json.parseToJsonElement(after.substring(start, end + 1)).jsonObject
                    (obj["title"] as? JsonPrimitive)?.content?.trim()
                }.getOrNull()
            } else {
                null
            }
            val clean = reply.substring(0, index)
                .trimEnd()
                .removeSuffix("```")
                .trimEnd()
            return clean to title?.takeIf { it.isNotBlank() }
        }

        fun factory(
            preferencesRepository: PreferencesRepository,
            novelRepository: NovelRepository,
            skillRepository: SkillRepository,
            chatRepository: ChatRepository,
            pipelineRepository: PipelineRepository,
            memoRepository: MemoRepository,
            aiClient: AiClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AiChatViewModel(
                    preferencesRepository,
                    novelRepository,
                    skillRepository,
                    chatRepository,
                    pipelineRepository,
                    memoRepository,
                    aiClient
                ) as T
        }
    }
}
