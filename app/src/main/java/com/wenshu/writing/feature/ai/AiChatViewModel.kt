package com.wenshu.writing.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.remote.AgentMessage
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.ChatRepository
import com.wenshu.writing.data.repository.MemoRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PipelineRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.data.repository.SettingRepository
import com.wenshu.writing.data.repository.SkillRepository
import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.ApiProviderType
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.ChatSession
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.SettingCategories
import com.wenshu.writing.domain.model.Skill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 保存 AI 消息的目标类型。 */
enum class SaveTarget {
    CHAPTER, OUTLINE, MEMO
}

/** AI 解析混合文档后得到的结构化结果。 */
data class ParsedDocItem(val title: String, val content: String)
data class ParsedCharacter(val name: String, val role: String, val description: String)
data class ParsedWorldSetting(val category: String, val title: String, val content: String)
data class ParsedDocResult(
    val title: String?,
    val outline: String?,
    val characters: List<ParsedCharacter>,
    val worldSettings: List<ParsedWorldSetting>,
    val custom: Map<String, List<ParsedDocItem>>
) {
    val hasAnyContent: Boolean
        get() = !outline.isNullOrBlank() ||
            characters.isNotEmpty() ||
            worldSettings.isNotEmpty() ||
            custom.values.any { it.isNotEmpty() }
}

/** 书名重复时等待用户选择「新建另一本 / 打开已有书稿」。 */
data class PendingDocImport(
    val title: String,
    val doc: ParsedDocResult,
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
    /** 联网检索阶段的 loading，和模型请求区分显示。 */
    val isSearching: Boolean = false,
    /** 用户手动打开联网搜索；默认关闭。 */
    val webSearchEnabled: Boolean = false,
    /** 上一轮搜索命中的来源（标题 + URL 片段），用于引用标注。 */
    val searchSources: List<String> = emptyList(),
    val novels: List<Novel> = emptyList(),
    val selectedNovelId: Long? = null,
    val skills: List<Skill> = emptyList(),
    val mentionQuery: String? = null,
    val mentionResults: List<Skill> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: Long? = null,
    /** 当前会话绑定的书稿 ID；null = 全局会话。该值由 activeSessionId 自动推导。 */
    val activeSessionBookId: Long? = null,
    /** 选中内置流水线 Skill 后由界面跳转到流水线页面。 */
    val pipelineSkill: Skill? = null,
    val message: String? = null,
    val toast: String? = null,
    val pendingDoc: PendingDocImport? = null,
    /** AI 未识别到书名时，让用户手动输入书名后再落库。 */
    val pendingTitleInput: ParsedDocResult? = null,
    /** 流式生成中的实时文本。 */
    val streamingText: String = "",
    /** 模型思考阶段的实时思考文本，仅展示用、不写入最终回复。 */
    val streamingReasoning: String = "",
    /** 当前正在执行的本地工具名，用于气泡提示。 */
    val activeToolName: String? = null,
    /** 正在编辑的用户消息 ID；非空时输入框处于编辑态。 */
    val editingMessageId: Long? = null,
    /** 进入编辑态之前的输入框内容，用于「撤回修改」。 */
    val editingPreviousInput: String = "",
    /** 技能通过 question 工具发起的待确认项。 */
    val pendingQuestion: PendingQuestion? = null,
    /** Skill 执行调试日志开关（沿用 1.0.7 的调试能力）。 */
    val skillLogEnabled: Boolean = false,
    val skillLogs: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val novelRepository: NovelRepository,
    private val skillRepository: SkillRepository,
    private val chatRepository: ChatRepository,
    private val pipelineRepository: PipelineRepository,
    private val settingRepository: SettingRepository,
    private val memoRepository: MemoRepository,
    private val aiClient: AiClient
) : ViewModel() {

    /** 当前 AI 请求的 Job，用于"停止生成"按钮立即中断。 */
    private var requestJob: Job? = null

    /** 技能 question 工具等待用户回答的挂起点。 */
    private var pendingQuestionDeferred: CompletableDeferred<String>? = null

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        // 从会话列表自动派生活跃会话绑定的书稿 ID，供 UI 展示"《书名》/全局"标签。
        viewModelScope.launch {
            _uiState.collect { state ->
                val current = state.sessions
                    .firstOrNull { it.id == state.activeSessionId }
                    ?.bookId
                if (current != state.activeSessionBookId) {
                    _uiState.update { it.copy(activeSessionBookId = current) }
                }
            }
        }
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

    /** 进入编辑态：把用户消息原文回填输入框，并记住进入前的输入内容。 */
    fun startEditingMessage(message: ChatMessage) {
        if (message.role != ChatRole.USER) return
        // 仅允许修改当前会话中用户最新发出的一条消息，历史消息禁止编辑。
        val latestUser = _uiState.value.messages.lastOrNull { it.role == ChatRole.USER }
        if (latestUser == null || latestUser.id != message.id) {
            _uiState.update { it.copy(toast = "仅可修改当前会话中最新一条消息") }
            return
        }
        _uiState.update {
            it.copy(
                editingMessageId = message.id,
                editingPreviousInput = it.input,
                input = message.content
            )
        }
    }

    /** 撤回修改：退出编辑态并恢复输入框原样。 */
    fun cancelEditingMessage() {
        _uiState.update {
            it.copy(
                editingMessageId = null,
                input = it.editingPreviousInput,
                editingPreviousInput = ""
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

        // 选中后回填 @技能名（带尾随空格，光标置于其后），同时激活技能并关闭面板。
        _uiState.update { current ->
            current.copy(
                input = "@${skill.name} ",
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

    /**
     * 新建空白会话：不绑定书稿（全局会话），临时标题「新会话」，
     * 第一条用户消息发送成功后自动提炼标题。
     */
    fun createSession() {
        viewModelScope.launch {
            val id = chatRepository.createSession("新会话", null)
            _uiState.update { it.copy(activeSessionId = id) }
        }
    }

    /** 会话内随时给当前会话绑定 / 更换 / 解绑书稿。bookId=null 表示解绑。 */
    fun bindCurrentSessionBook(bookId: Long?) {
        val sessionId = _uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            chatRepository.bindSessionBook(sessionId, bookId)
            val novelTitle = bookId?.let { bid ->
                _uiState.value.novels.firstOrNull { it.id == bid }?.title
            }
            val toast = if (bookId == null) "已解绑书稿" else novelTitle?.let { "已绑定书稿《$it》" } ?: "已绑定书稿"
            _uiState.update { it.copy(toast = toast) }
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

        requestJob?.cancel()
        _uiState.update {
            it.copy(
                input = "",
                isSending = true,
                isSearching = state.webSearchEnabled,
                mentionQuery = null,
                mentionResults = emptyList(),
                streamingText = "",
                streamingReasoning = "",
                activeToolName = null,
                editingMessageId = null,
                editingPreviousInput = "",
                skillLogs = if (it.skillLogEnabled) emptyList() else it.skillLogs
            )
        }

        val config = state.config()
        requestJob = viewModelScope.launch(Dispatchers.IO) {
            var sessionId: Long? = null
            try {
                val sid = ensureSession(firstMessage = text)
                sessionId = sid
                chatRepository.appendMessage(sid, ChatRole.USER, text)

                // 首次发送 → 自动提炼会话标题
                val session = chatRepository.getSession(sid)
                if (session != null && (session.title.isBlank() || session.title == "新会话" || session.title.endsWith("新对话"))) {
                    val autoTitle = deriveSessionTitle(text)
                    chatRepository.renameSession(sid, autoTitle)
                }

                var searchPayload: String? = null
                if (_uiState.value.webSearchEnabled) {
                    runCatching {
                        searchWeb(text)
                    }.onSuccess { (summaries, sources) ->
                        searchPayload = summaries
                        _uiState.update { it.copy(isSearching = false, searchSources = sources) }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                searchSources = emptyList(),
                                toast = "联网搜索异常：${err.message ?: "请检查网络"}"
                            )
                        }
                    }
                }
                runAgentLoop(sid, config, text, searchPayload)
            } catch (_: CancellationException) {
                // 手动停止：保留已经流式输出的文字
                val partial = _uiState.value.streamingText.trim()
                if (partial.isNotBlank() && sessionId != null) {
                    chatRepository.appendMessage(sessionId, ChatRole.ASSISTANT, partial)
                }
            } finally {
                finishGeneration()
            }
        }
    }

    /** 立即终止当前 AI 请求（含联网搜索、工具执行与 Agent 循环）。 */
    fun stopGeneration() {
        pendingQuestionDeferred?.complete("用户已停止当前技能流程")
        pendingQuestionDeferred = null
        _uiState.update { it.copy(pendingQuestion = null) }
        requestJob?.cancel()
        requestJob = null
        _uiState.update {
            it.copy(
                isSending = false,
                isSearching = false,
                streamingText = "",
                streamingReasoning = "",
                activeToolName = null,
                toast = "已停止生成"
            )
        }
    }

    /** 切换联网搜索开关。 */
    fun setWebSearchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(webSearchEnabled = enabled) }
    }

    // ---- Agent 循环 ----

    /**
     * 多轮工具循环：模型返回 tool_call → 本地执行 → 回传结果 → 继续请求，
     * 直到模型不再请求工具或达到轮数上限。每轮文本都流式回传到气泡。
     */
    private suspend fun runAgentLoop(
        sessionId: Long,
        config: AiConfig,
        userText: String,
        searchPayload: String? = null
    ) {
        val activeSkills = _uiState.value.skills.filter { it.enabled && !it.isPipeline }
        // 书稿创建隔离：只有激活的「创建新书」类技能（其 skill.md 声明使用 create_project）才允许新建书稿，
        // 制作/调试 Skill 等场景禁止自动生成书稿条目。
        val allowCreateProject = activeSkills.any { it.mainMarkdown.contains("create_project") }
        val tools = if (activeSkills.isNotEmpty()) AgentToolExecutor.toolSpecs(allowCreateProject) else emptyList()
        val executor = AgentToolExecutor(
            novelRepository = novelRepository,
            settingRepository = settingRepository,
            skillRepository = skillRepository,
            chatRepository = chatRepository,
            bookIdProvider = { _uiState.value.activeSessionBookId },
            sessionIdProvider = { _uiState.value.activeSessionId },
            activeSkillsProvider = { _uiState.value.skills.filter { it.enabled && !it.isPipeline } },
            askQuestion = ::requestQuestion,
            log = ::appendAgentLog,
            allowCreateProject = allowCreateProject
        )
        val messages = mutableListOf<AgentMessage>()
        chatRepository.getMessages(sessionId).filterNot { it.isError }.forEach { message ->
            messages.add(
                AgentMessage(
                    role = if (message.role == ChatRole.USER) "user" else "assistant",
                    content = message.content
                )
            )
        }
        appendAgentLog("开始 Agent 循环：绑定书稿=${_uiState.value.activeSessionBookId}，工具=${tools.joinToString { it.name }}")

        repeat(MAX_AGENT_TURNS) { turnIndex ->
            currentCoroutineContext().ensureActive()
            _uiState.update { it.copy(streamingText = "", streamingReasoning = "", activeToolName = null) }
            appendAgentLog("第 ${turnIndex + 1} 轮请求模型")

            // 每轮重建系统提示：create_project 绑定书稿后，后续轮次能立即拿到书稿上下文。
            val basePrompt = buildSystemPrompt(_uiState.value.skills) + buildBookContext(sessionId)
            val systemPrompt = if (!searchPayload.isNullOrBlank()) {
                basePrompt + "\n\n【本轮联网检索参考资料】请综合以下实时资料回答用户问题，回答末尾标注来源条目：\n" +
                    searchPayload
            } else {
                basePrompt
            }

            val turn = aiClient.chatStream(
                config = config,
                messages = messages,
                systemPrompt = systemPrompt,
                tools = tools,
                onReasoningDelta = { delta ->
                    _uiState.update { current ->
                        current.copy(streamingReasoning = current.streamingReasoning + delta)
                    }
                }
            ) { delta ->
                _uiState.update { current ->
                    // 正文开始输出后清除思考文本，思考内容不进入最终回复。
                    current.copy(
                        streamingText = current.streamingText + delta,
                        streamingReasoning = ""
                    )
                }
            }.getOrElse { error ->
                val partial = _uiState.value.streamingText.trim()
                if (partial.isNotBlank()) {
                    chatRepository.appendMessage(sessionId, ChatRole.ASSISTANT, partial)
                } else {
                    chatRepository.appendMessage(
                        sessionId = sessionId,
                        role = ChatRole.ASSISTANT,
                        content = friendlyError(error),
                        isError = true
                    )
                }
                return
            }

            messages.add(AgentMessage("assistant", turn.text, turn.toolCalls))
            if (turn.toolCalls.isEmpty()) {
                appendAgentLog("模型完成回复，结束 Agent 循环")
                handleAssistantReply(sessionId, turn.text, userText)
                return
            }

            turn.toolCalls.forEach { call ->
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(activeToolName = call.name) }
                val result = executor.execute(call)
                appendAgentLog("工具返回 ${call.name}：${result.take(300)}")
                messages.add(AgentMessage("tool", result, toolCallId = call.id))
            }
            _uiState.update { it.copy(streamingText = "", activeToolName = null) }
        }

        chatRepository.appendMessage(
            sessionId = sessionId,
            role = ChatRole.ASSISTANT,
            content = "本次技能执行已达到工具调用轮数上限，已暂停。可继续发送消息让模型接着完成剩余步骤。",
            isError = true
        )
    }

    /** 提问工具：展示确认弹窗并等待用户选择，最多等待 5 分钟。 */
    private suspend fun requestQuestion(question: PendingQuestion): String {
        val deferred = CompletableDeferred<String>()
        pendingQuestionDeferred = deferred
        _uiState.update { it.copy(pendingQuestion = question) }
        appendAgentLog("等待用户确认：${question.question}")
        val answer = try {
            withTimeout(QUESTION_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            "用户未在时限内回答"
        } finally {
            pendingQuestionDeferred = null
            _uiState.update { it.copy(pendingQuestion = null) }
        }
        appendAgentLog("用户回答：$answer")
        return "用户选择：$answer"
    }

    /** 用户在弹窗中选择选项。 */
    fun answerQuestion(label: String) {
        val deferred = pendingQuestionDeferred ?: return
        _uiState.update { it.copy(pendingQuestion = null) }
        deferred.complete(label)
        pendingQuestionDeferred = null
    }

    /** 调试日志开关。 */
    fun setSkillLogEnabled(enabled: Boolean) {
        _uiState.update { it.copy(skillLogEnabled = enabled, skillLogs = if (enabled) it.skillLogs else emptyList()) }
    }

    fun clearSkillLogs() {
        _uiState.update { it.copy(skillLogs = emptyList()) }
    }

    private fun appendAgentLog(line: String) {
        if (!_uiState.value.skillLogEnabled) return
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _uiState.update { current ->
            current.copy(skillLogs = (current.skillLogs + "[$stamp] $line").takeLast(300))
        }
    }

    private fun friendlyError(error: Throwable): String = when (error) {
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException ->
            "请求超时（timeout），请检查网络或 API 服务后重试"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "请求失败，请检查网络或 API 配置"
    }

    private fun finishGeneration() {
        _uiState.update {
            it.copy(
                isSending = false,
                isSearching = false,
                streamingText = "",
                streamingReasoning = "",
                activeToolName = null,
                pendingQuestion = null
            )
        }
        requestJob = null
    }

    /** 绑定书稿时把设定、人物、世界观、大纲与最近章节摘要注入系统提示。 */
    private suspend fun buildBookContext(sessionId: Long): String {
        val bookId = _uiState.value.sessions
            .firstOrNull { it.id == sessionId }
            ?.bookId
            ?: chatRepository.getSession(sessionId)?.bookId
            ?: return ""
        val novel = novelRepository.getNovel(bookId) ?: return ""
        val settings = settingRepository.getAll(bookId)
        val chapters = novelRepository.getChapters(bookId)

        return buildString {
            appendLine()
            appendLine("【当前绑定书稿】《${novel.title}》")
            val characters = settings.filter { it.category == SettingCategories.CHARACTER }
            if (characters.isNotEmpty()) {
                appendLine()
                appendLine("【人物设定】")
                characters.take(40).forEach { item ->
                    val role = item.subtitle?.let { "（$it）" } ?: ""
                    appendLine("- ${item.displayTitle}$role：${item.content.take(500)}")
                }
            }
            val world = settings.filter { it.category == SettingCategories.WORLD }
            if (world.isNotEmpty()) {
                appendLine()
                appendLine("【世界观设定】")
                world.take(30).forEach { item ->
                    appendLine("- ${item.displayTitle}：${item.content.take(500)}")
                }
            }
            val outline = settings.filter { it.category == SettingCategories.OUTLINE }
            if (outline.isNotEmpty()) {
                appendLine()
                appendLine("【大纲】")
                outline.take(5).forEach { item ->
                    appendLine(item.content.take(2000))
                }
            }
            val custom = settings.filterNot {
                SettingCategories.isBuiltIn(it.category) ||
                    it.category == AgentToolExecutor.CATEGORY_SUMMARY ||
                    it.category == AgentToolExecutor.CATEGORY_DOC
            }
            if (custom.isNotEmpty()) {
                appendLine()
                appendLine("【其他设定】")
                custom.take(15).forEach { item ->
                    appendLine("- [${item.category}] ${item.displayTitle}：${item.content.take(300)}")
                }
                if (custom.size > 15) appendLine("（其余设定已省略）")
            }
            if (chapters.isNotEmpty()) {
                appendLine()
                appendLine("【最近章节】")
                chapters.takeLast(5).forEach { chapter ->
                    appendLine("- ${chapter.title}（${chapter.wordCount} 字）：${chapter.content.take(150)}")
                }
                appendLine("（如需完整正文请使用 file_read 读取 章节内容/第X章.md）")
            }
        }
    }

    /** 首次发送时创建会话；自动读取当前活跃会话绑定的书稿。 */
    private suspend fun ensureSession(firstMessage: String): Long {
        val existing = _uiState.value.activeSessionId
        if (existing != null) return existing
        val bookId = _uiState.value.activeSessionBookId
        val id = chatRepository.createSession(firstMessage.take(20), bookId)
        _uiState.update { it.copy(activeSessionId = id) }
        return id
    }

    /** 根据首条用户消息提炼简短会话标题。 */
    private fun deriveSessionTitle(firstMessage: String): String {
        val trimmed = firstMessage.trim()
        // 优先取第一句（到 。 ？ ！ 换行 标点）
        val end = trimmed.indexOfAny(charArrayOf('。', '？', '！', '\n', '.'))
        val head = if (end > 0) trimmed.substring(0, end) else trimmed
        val condensed = head.trim()
            .removePrefix("@")
            .replace(Regex("""\s+"""), " ")
        return condensed.takeIf { it.isNotBlank() }
            ?.take(30)
            ?: "新会话"
    }

    /**
     * 轻量联网搜索：用公共 API（SerpAPI-free DuckDuckGo instant answer fallback：Brave Search 未配置时降级为 DuckDuckGo HTML 抓取 + 百度公开 web 搜索）。
     * 这里选择最通用的方案：调用 WebSearch 封装（若有），否则直接拼接"请根据实时资料回答"到 prompt。
     * 为保持 1.0.6 零外部依赖，本实现：
     * - 优先走 OkHttp 直连 DuckDuckGo 的 lite 页面抓前 3 条标题 + 摘要；
     * - 失败则返回空（让模型继续纯本地回答）。
     */
    private suspend fun searchWeb(query: String): Pair<String, List<String>> {
        val result = runCatching {
            val url = "https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = okhttp3.Request.Builder().url(url).get()
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()
            val body = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request).execute().body?.string().orEmpty()

            // 解析前 3 条结果
            val snippetRegex = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            val titleRegex = Regex("""class="result__a"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            val titles = titleRegex.findAll(body).map { it.groupValues[1].replace(Regex("<[^>]+>"), "") }.toList()
            val snippets = snippetRegex.findAll(body).map { it.groupValues[1].replace(Regex("<[^>]+>"), "") }.toList()

            val items = titles.take(3).mapIndexed { idx, title ->
                val snippet = snippets.getOrNull(idx).orEmpty()
                "[${idx + 1}] $title — $snippet"
            }
            if (items.isEmpty()) {
                throw IllegalStateException("未搜索到结果")
            }
            val sources = titles.take(3).mapIndexed { idx, t -> "${idx + 1}. $t" }
            items.joinToString("\n") to sources
        }
        return result.getOrElse { throw it }
    }

    /**
     * 保存 AI 回复，并解析文档建档标记。
     * 1.0.6 起支持完整文档解析：书名 + 大纲 + 人物 + 世界观 + 自定义分类全部落库。
     */
    private suspend fun handleAssistantReply(
        sessionId: Long,
        rawReply: String,
        userText: String
    ) {
        val (cleanReply, doc) = extractDocMarker(rawReply)
        chatRepository.appendMessage(
            sessionId = sessionId,
            role = ChatRole.ASSISTANT,
            content = cleanReply.ifBlank { rawReply }
        )
        if (doc == null || userText.isBlank()) return

        // 未识别到书名：询问用户手动输入，不强制创建
        val title = doc.title?.takeIf { it.isNotBlank() }
        if (title == null) {
            if (doc.hasAnyContent) {
                _uiState.update { it.copy(pendingTitleInput = doc) }
            }
            return
        }

        val existing = novelRepository.findNovelByTitle(title)
        if (existing == null) {
            createNovelFromDoc(sessionId, title, doc)
        } else {
            _uiState.update {
                it.copy(
                    pendingDoc = PendingDocImport(
                        title = title,
                        doc = doc,
                        existingNovelId = existing.id
                    )
                )
            }
        }
    }

    /** 把解析出的全部内容按分类写入指定书稿的设定库。 */
    private suspend fun persistDoc(bookId: Long, doc: ParsedDocResult) {
        if (!doc.outline.isNullOrBlank()) {
            settingRepository.addOutline("AI导入大纲", doc.outline, bookId)
        }
        doc.characters.forEach { ch ->
            settingRepository.addCharacter(
                name = ch.name.ifBlank { "未命名角色" },
                role = ch.role,
                description = ch.description,
                bookId = bookId
            )
        }
        doc.worldSettings.forEach { ws ->
            settingRepository.addWorldSetting(
                category = ws.category,
                title = ws.title.ifBlank { "世界观设定" },
                content = ws.content,
                bookId = bookId
            )
        }
        doc.custom.forEach { (category, items) ->
            items.forEach { item ->
                settingRepository.addSetting(
                    category = category,
                    title = item.title.ifBlank { category },
                    content = item.content,
                    bookId = bookId
                )
            }
        }
    }

    private suspend fun createNovelFromDoc(sessionId: Long, title: String, doc: ParsedDocResult) {
        val novelId = novelRepository.createNovel(title, "由 AI 文档导入")
        persistDoc(novelId, doc)
        preferencesRepository.setActiveNovel(novelId)
        chatRepository.bindSessionBook(sessionId, novelId)
        _uiState.update { it.copy(selectedNovelId = novelId) }
        chatRepository.appendMessage(
            sessionId = sessionId,
            role = ChatRole.ASSISTANT,
            content = buildCompleteNotice(title, doc)
        )
    }

    private fun buildCompleteNotice(title: String, doc: ParsedDocResult): String {
        val parts = mutableListOf<String>()
        if (!doc.outline.isNullOrBlank()) parts.add("大纲")
        if (doc.characters.isNotEmpty()) parts.add("人物设定")
        if (doc.worldSettings.isNotEmpty()) parts.add("世界观")
        if (doc.custom.isNotEmpty()) parts.add("自定义分类")
        val detail = if (parts.isEmpty()) "" else "，已自动入库：${parts.joinToString("、")}"
        return "已解析完整文档并创建新书《$title》$detail"
    }

    /** 书名重复时：新建另一本，或打开已有书稿并追加全部解析内容。 */
    fun confirmPendingDoc(createNew: Boolean) {
        val pending = _uiState.value.pendingDoc ?: return
        val sessionId = _uiState.value.activeSessionId ?: return
        _uiState.update { it.copy(pendingDoc = null) }
        viewModelScope.launch {
            if (createNew) {
                createNovelFromDoc(sessionId, pending.title, pending.doc)
            } else {
                persistDoc(pending.existingNovelId, pending.doc)
                preferencesRepository.setActiveNovel(pending.existingNovelId)
                chatRepository.bindSessionBook(sessionId, pending.existingNovelId)
                _uiState.update { it.copy(selectedNovelId = pending.existingNovelId) }
                chatRepository.appendMessage(
                    sessionId = sessionId,
                    role = ChatRole.ASSISTANT,
                    content = "已打开书稿《${pending.title}》，解析内容已写入该书稿设定库"
                )
            }
        }
    }

    fun dismissPendingDoc() {
        _uiState.update { it.copy(pendingDoc = null) }
    }

    /** 用户手动输入书名后，继续落库流程。 */
    fun confirmTitleInput(title: String) {
        val doc = _uiState.value.pendingTitleInput ?: return
        val sessionId = _uiState.value.activeSessionId ?: return
        val trimmed = title.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(message = "书名不能为空") }
            return
        }
        _uiState.update { it.copy(pendingTitleInput = null) }
        viewModelScope.launch {
            val existing = novelRepository.findNovelByTitle(trimmed)
            if (existing == null) {
                createNovelFromDoc(sessionId, trimmed, doc)
            } else {
                _uiState.update {
                    it.copy(
                        pendingDoc = PendingDocImport(
                            title = trimmed,
                            doc = doc,
                            existingNovelId = existing.id
                        )
                    )
                }
            }
        }
    }

    fun dismissTitleInput() {
        _uiState.update { it.copy(pendingTitleInput = null) }
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
        requestJob?.cancel()
        _uiState.update {
            it.copy(
                isSending = true,
                isSearching = false,
                streamingText = "",
                streamingReasoning = "",
                activeToolName = null,
                skillLogs = if (it.skillLogEnabled) emptyList() else it.skillLogs
            )
        }

        requestJob = viewModelScope.launch(Dispatchers.IO) {
            val all = chatRepository.getMessages(sessionId)
            val index = all.indexOfFirst { it.id == messageId }
            if (index < 0) {
                finishGeneration()
                return@launch
            }
            val userText = all.take(index).lastOrNull { it.role == ChatRole.USER }?.content.orEmpty()
            chatRepository.deleteMessage(messageId)
            try {
                runAgentLoop(sessionId, config, userText)
            } catch (_: CancellationException) {
                val partial = _uiState.value.streamingText.trim()
                if (partial.isNotBlank()) {
                    chatRepository.appendMessage(sessionId, ChatRole.ASSISTANT, partial)
                }
            } finally {
                finishGeneration()
            }
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
                        settingRepository.addOutline(
                            title = "AI导入大纲_$stamp",
                            content = content,
                            bookId = novelId
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
        /** 单次请求最多允许的 Agent 工具轮数，防止模型死循环。 */
        private const val MAX_AGENT_TURNS = 10

        /** 等待用户回答 question 工具的最长时间。 */
        private const val QUESTION_TIMEOUT_MS = 300_000L

        /** 模型输出的文档建档标记；由 App 解析并创建书稿与分类设定。 */
        private const val DOC_MARKER = "<<WENSHU_NEW_BOOK>>"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        private val DOC_PROTOCOL = """
【全文建档协议】当用户粘贴或输入一份混合长文（可能包含书名、大纲、人物、世界观、势力、道具、功法、伏笔、设定杂项等）时：
1. 正常给出简短的确认或建议，不要复述原文；
2. 在回复的最后单独一行输出标记和 JSON：
<<WENSHU_NEW_BOOK>>{"title":"书名","outline":"大纲正文","characters":[{"name":"角色名","role":"身份","description":"描述"}],"worldSettings":[{"category":"分类","title":"标题","content":"内容"}],"custom":{"势力":[{"title":"标题","content":"内容"}],"道具":[{"title":"标题","content":"内容"}]}}
3. title：提取正式书名，去掉《》和多余空格；识别不到书名时填 null 或留空字符串，App 会让用户手动输入；
4. outline：大纲正文，无则省略或填空字符串；
5. characters：人物列表，每项含 name（姓名）、role（身份/定位）、description（外貌性格背景等描述）；无则省略；
6. worldSettings：世界观/世界规则/背景设定，每项含 category（子分类，可空）、title、content；无则省略；
7. custom：其他自定义设定（如势力、道具、功法、伏笔、种族等），键为分类名，值为条目数组（含 title、content）；无则省略；
8. 只输出一个 JSON 对象，不要输出多个，不要虚构 bookId，全部数据落库由 App 完成；
9. 用户输入不是有效文档时，不要输出该标记。
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
            appendLine(DOC_PROTOCOL)
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

        /** 剥离建档标记，返回给用户展示的文本与解析出的文档结构。 */
        private fun extractDocMarker(reply: String): Pair<String, ParsedDocResult?> {
            val index = reply.indexOf(DOC_MARKER)
            if (index < 0) return reply to null
            val after = reply.substring(index + DOC_MARKER.length).trim()
            val start = after.indexOf('{')
            val end = if (start >= 0) findMatchingBrace(after, start) else -1
            val doc = if (start >= 0 && end > start) {
                runCatching { parseDocJson(after.substring(start, end + 1)) }.getOrNull()
            } else {
                null
            }
            val clean = reply.substring(0, index)
                .trimEnd()
                .removeSuffix("```")
                .trimEnd()
            return clean to doc
        }

        private fun findMatchingBrace(text: String, openIndex: Int): Int {
            var depth = 0
            var inString = false
            var escaped = false
            for (i in openIndex until text.length) {
                val ch = text[i]
                if (inString) {
                    if (escaped) escaped = false
                    else if (ch == '\\') escaped = true
                    else if (ch == '"') inString = false
                } else {
                    when (ch) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) return i
                        }
                    }
                }
            }
            return -1
        }

        private fun parseDocJson(raw: String): ParsedDocResult {
            val obj = json.parseToJsonElement(raw).jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            val outline = obj["outline"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val characters = obj["characters"]?.jsonArray?.mapNotNull { el ->
                val c = el.jsonObject
                val name = c["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val role = c["role"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val description = c["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (name.isBlank() && description.isBlank()) null
                else ParsedCharacter(name = name, role = role, description = description)
            }.orEmpty()
            val worldSettings = obj["worldSettings"]?.jsonArray?.mapNotNull { el ->
                val w = el.jsonObject
                val category = w["category"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val title2 = w["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val content = w["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (title2.isBlank() && content.isBlank()) null
                else ParsedWorldSetting(category = category, title = title2, content = content)
            }.orEmpty()
            val custom = obj["custom"]?.jsonObject?.entries?.associate { (category, value) ->
                val items = value.jsonArray.mapNotNull { el ->
                    val it = el.jsonObject
                    val t = it["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val c = it["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (t.isBlank() && c.isBlank()) null
                    else ParsedDocItem(title = t, content = c)
                }
                category to items
            }?.filterValues { it.isNotEmpty() }.orEmpty()

            return ParsedDocResult(
                title = title,
                outline = outline,
                characters = characters,
                worldSettings = worldSettings,
                custom = custom
            )
        }

        fun factory(
            preferencesRepository: PreferencesRepository,
            novelRepository: NovelRepository,
            skillRepository: SkillRepository,
            chatRepository: ChatRepository,
            pipelineRepository: PipelineRepository,
            settingRepository: SettingRepository,
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
                    settingRepository,
                    memoRepository,
                    aiClient
                ) as T
        }
    }
}
