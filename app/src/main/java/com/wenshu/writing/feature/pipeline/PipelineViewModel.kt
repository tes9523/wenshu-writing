package com.wenshu.writing.feature.pipeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.ChatRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PipelineRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.data.repository.SettingRepository
import com.wenshu.writing.data.repository.SkillRepository
import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.ChapterTask
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.NewBookInfo
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.PipelineRun
import com.wenshu.writing.domain.model.PipelineStatus
import com.wenshu.writing.domain.model.Setting
import com.wenshu.writing.domain.model.SettingCategories
import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.domain.model.stripFrontMatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** 流水线六个阶段的固定顺序与展示名。 */
val pipelineStageNames = listOf(
    "信息采集",
    "基础设定",
    "分级大纲",
    "章节任务卡",
    "约束正文",
    "一致性质检"
)

data class PipelineUiState(
    val novel: Novel? = null,
    val skill: Skill? = null,
    val stage: Int = 1,
    val status: PipelineStatus = PipelineStatus.PAUSED,
    val info: NewBookInfo? = null,
    val output: String = "",
    val characters: List<Setting> = emptyList(),
    val worldSettings: List<Setting> = emptyList(),
    val outlines: List<Setting> = emptyList(),
    val tasks: List<ChapterTask> = emptyList(),
    val isRunning: Boolean = false,
    val message: String? = null
)

class PipelineViewModel(
    private val novelId: Long,
    private val skillId: Long,
    private val novelRepository: NovelRepository,
    private val skillRepository: SkillRepository,
    private val pipelineRepository: PipelineRepository,
    private val settingRepository: SettingRepository,
    private val preferencesRepository: PreferencesRepository,
    private val chatRepository: ChatRepository,
    private val aiClient: AiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(PipelineUiState())
    val uiState: StateFlow<PipelineUiState> = _uiState.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    init {
        viewModelScope.launch {
            val novel = novelRepository.observeNovel(novelId).first()
            val skill = if (skillId > 0L) {
                skillRepository.getById(skillId)
            } else {
                skillRepository.findByPipeline()
            }
            val run = pipelineRepository.getRun(novelId)
            val info = run?.inputJson?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { json.decodeFromString(NewBookInfo.serializer(), raw) }.getOrNull()
            }
            _uiState.update {
                it.copy(
                    novel = novel,
                    skill = skill,
                    stage = run?.stage ?: 1,
                    status = run?.status ?: PipelineStatus.PAUSED,
                    info = info,
                    output = run?.outputJson.orEmpty()
                )
            }
        }

        viewModelScope.launch {
            settingRepository.observeByCategory(novelId, SettingCategories.CHARACTER).collect { list ->
                _uiState.update { it.copy(characters = list) }
            }
        }
        viewModelScope.launch {
            settingRepository.observeByCategory(novelId, SettingCategories.WORLD).collect { list ->
                _uiState.update { it.copy(worldSettings = list) }
            }
        }
        viewModelScope.launch {
            pipelineRepository.observeOutlines(novelId).collect { list ->
                _uiState.update { it.copy(outlines = list) }
            }
        }
        viewModelScope.launch {
            pipelineRepository.observeTasks(novelId).collect { list ->
                _uiState.update { it.copy(tasks = list) }
            }
        }
    }

    /** 阶段 1：保存采集信息并自动进入阶段 2 的待执行状态。 */
    fun submitInfo(info: NewBookInfo) {
        if (!info.isValid()) {
            _uiState.update { it.copy(message = "请完整填写书名、题材、卖点、目标赛道、核心创意与风格偏好") }
            return
        }
        viewModelScope.launch {
            pipelineRepository.saveRun(
                PipelineRun(
                    novelId = novelId,
                    skillId = _uiState.value.skill?.id ?: 0L,
                    stage = 2,
                    status = PipelineStatus.PAUSED,
                    inputJson = json.encodeToString(NewBookInfo.serializer(), info),
                    outputJson = "阶段 1 采集完成，基础信息已保存。请执行阶段 2 生成新书基础设定。"
                )
            )
            _uiState.update {
                it.copy(
                    stage = 2,
                    status = PipelineStatus.PAUSED,
                    info = info,
                    output = "阶段 1 采集完成，基础信息已保存。请执行阶段 2 生成新书基础设定。"
                )
            }
        }
    }

    /** 执行当前待执行阶段；每阶段完成后自动暂停，等待人工确认。 */
    fun runCurrentStage() {
        val state = _uiState.value
        if (state.isRunning) return
        if (state.stage !in 2..6) {
            _uiState.update { it.copy(message = "请先完成阶段 1 信息采集") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true, message = null) }
            try {
                when (state.stage) {
                    2 -> runStage2()
                    3 -> runStage3()
                    4 -> runStage4()
                    5 -> runStage5()
                    6 -> runStage6()
                }
            } catch (e: Exception) {
                markFailed(e.message ?: "阶段执行失败，请检查 AI 配置后重试")
            } finally {
                _uiState.update { it.copy(isRunning = false) }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** 重新开始：只清除断点，已落库的设定、大纲与正文保留。 */
    fun restart() {
        viewModelScope.launch {
            pipelineRepository.deleteRun(novelId)
            _uiState.update {
                it.copy(
                    stage = 1,
                    status = PipelineStatus.PAUSED,
                    info = null,
                    output = ""
                )
            }
        }
    }

    private suspend fun runStage2() {
        val state = _uiState.value
        val info = state.info ?: run {
            markFailed("缺少阶段 1 的新书信息，请重新填写")
            return
        }
        val config = requireConfig() ?: return
        val system = stageSystemPrompt(state.skill, "stage-2")
        val reply = chat(config, system, info.toPromptText())
            ?: return

        var parsed = runCatching { parseJson(reply) }.getOrNull()
        if (parsed != null) {
            parsed.arrayObjects("worldSettings").forEach { item ->
                val title = item.string("title")
                if (title.isNotBlank()) {
                    settingRepository.addWorldSetting(
                        category = item.string("category").ifBlank { "世界观" },
                        title = title,
                        content = item.string("content"),
                        bookId = novelId
                    )
                }
            }
            parsed.arrayObjects("characters").forEach { item ->
                val name = item.string("name")
                if (name.isNotBlank()) {
                    settingRepository.addCharacter(
                        name = name,
                        role = item.string("role"),
                        description = item.string("description"),
                        bookId = novelId
                    )
                }
            }
            (parsed["goldenFinger"] as? JsonObject)?.let { finger ->
                val title = finger.string("title").ifBlank { "金手指体系" }
                settingRepository.addWorldSetting(
                    category = "金手指",
                    title = title,
                    content = finger.string("content"),
                    bookId = novelId
                )
            }
        } else {
            settingRepository.addWorldSetting(
                category = "AI生成",
                title = "新书基础设定（待整理）",
                content = reply,
                bookId = novelId
            )
        }

        completeStage(
            state,
            nextStage = 3,
            output = buildString {
                appendLine("阶段 2 基础设定已写入本书：")
                appendLine("· 世界观设定 ${parsed?.arrayObjects("worldSettings")?.size ?: 0} 条")
                appendLine("· 人物卡 ${parsed?.arrayObjects("characters")?.size ?: 0} 张")
                appendLine("· 金手指体系 1 套")
                if (parsed == null) appendLine("（AI 输出解析失败，已原样保存为一条设定）")
                appendLine()
                appendLine(reply)
            }
        )
    }

    private suspend fun runStage3() {
        val state = _uiState.value
        val config = requireConfig() ?: return
        val system = stageSystemPrompt(state.skill, "stage-3")
        val user = buildString {
            appendLine("【本书已入库设定】")
            append(bookSettingsText())
        }
        val reply = chat(config, system, user) ?: return

        val parsed = runCatching { parseJson(reply) }.getOrNull()
        val outlines = mutableListOf<Setting>()
        if (parsed != null) {
            val overall = parsed.string("overall")
            if (overall.isNotBlank()) {
                outlines += Setting(category = SettingCategories.OUTLINE, title = "全书总纲", content = overall)
            }
            parsed.arrayObjects("details").forEachIndexed { index, item ->
                val title = item.string("title").ifBlank { "细纲 ${index + 1}" }
                outlines += Setting(
                    category = SettingCategories.OUTLINE,
                    title = title,
                    content = item.string("content")
                )
            }
        }
        if (outlines.isEmpty()) {
            outlines += Setting(category = SettingCategories.OUTLINE, title = "全书总纲（待整理）", content = reply)
        }
        pipelineRepository.replaceOutlines(novelId, outlines)
        completeStage(
            state,
            nextStage = 4,
            output = buildString {
                appendLine("阶段 3 大纲已归入本书：总纲 + ${outlines.count { it.title.startsWith("细纲") }} 条细纲。")
                appendLine()
                appendLine(reply)
            }
        )
    }

    private suspend fun runStage4() {
        val state = _uiState.value
        val config = requireConfig() ?: return
        val system = stageSystemPrompt(state.skill, "stage-4")
        val user = buildString {
            appendLine("【本书已入库设定】")
            append(bookSettingsText())
            appendLine()
            appendLine("【本书大纲】")
            append(outlineText())
        }
        val reply = chat(config, system, user) ?: return

        val parsed = runCatching { parseJson(reply) }.getOrNull()
        val tasks = parsed?.arrayObjects("tasks").orEmpty().mapIndexed { index, item ->
            ChapterTask(
                novelId = novelId,
                orderIndex = index,
                title = item.string("title").ifBlank { "第 ${index + 1} 章" },
                goal = item.string("goal"),
                conflict = item.string("conflict"),
                infoIncrement = item.string("infoIncrement"),
                payoff = item.string("payoff"),
                hook = item.string("hook")
            )
        }
        if (tasks.isEmpty()) {
            markFailed("未能解析章节任务卡，请确认 AI 输出为 JSON 格式后重试")
            return
        }
        pipelineRepository.replaceTasks(novelId, tasks)
        completeStage(
            state,
            nextStage = 5,
            output = buildString {
                appendLine("阶段 4 已生成 ${tasks.size} 张章节任务卡。")
                appendLine()
                tasks.take(3).forEach { appendLine(it.toPromptCard()) }
                if (tasks.size > 3) appendLine("…其余 ${tasks.size - 3} 张已落库，可在书稿中查看。")
            }
        )
    }

    private suspend fun runStage5() {
        val state = _uiState.value
        val tasks = pipelineRepository.getTasks(novelId)
        if (tasks.isEmpty()) {
            markFailed("缺少章节任务卡，请先完成阶段 4")
            return
        }
        val config = requireConfig() ?: return
        val system = stageSystemPrompt(state.skill, "stage-5")
        val existingChapters = novelRepository.observeChapters(novelId).first()

        for ((index, task) in tasks.withIndex()) {
            _uiState.update {
                it.copy(output = "正在生成第 ${index + 1}/${tasks.size} 章：${task.title}")
            }
            val user = buildString {
                appendLine("【本书全套设定（强约束）】")
                append(bookSettingsText())
                appendLine()
                appendLine("【本章任务卡（强约束）】")
                append(task.toPromptCard())
                appendLine()
                appendLine("请严格按以上双重约束生成本章正文。")
            }
            val reply = chat(config, system, user) ?: return
            val chapter = existingChapters.firstOrNull { it.title == task.title }
            if (chapter == null) {
                val id = novelRepository.createChapter(novelId, task.title)
                novelRepository.saveChapter(id, task.title, reply)
            } else {
                novelRepository.saveChapter(chapter.id, chapter.title, reply)
            }
        }

        completeStage(
            state,
            nextStage = 6,
            output = "阶段 5 已生成 ${tasks.size} 章正文初稿并直接存入本书章节，未留在对话窗口。"
        )
    }

    private suspend fun runStage6() {
        val state = _uiState.value
        val config = requireConfig() ?: return
        val checker = skillRepository.getAll().firstOrNull {
            it.name.contains("质检") || it.name.contains("一致性")
        }
        val checkerPrompt = checker?.let { "${stripFrontMatter(it.mainMarkdown)}\n${it.references.joinToString("\n") { ref -> ref.content }}" }
            ?: "请作为质检编辑，检查人设OOC、设定冲突、剧情逻辑BUG与节奏问题，只输出报告不自动改文。"
        val chapters = novelRepository.observeChapters(novelId).first()
        val user = buildString {
            appendLine("【本书设定】")
            append(bookSettingsText())
            appendLine()
            appendLine("【章节任务卡】")
            pipelineRepository.getTasks(novelId).take(10).forEach { appendLine(it.toPromptCard()) }
            appendLine()
            appendLine("【正文节选】")
            chapters.take(5).forEach { chapter ->
                appendLine("## ${chapter.title}")
                appendLine(chapter.content.take(1200))
            }
        }
        val reply = chat(config, checkerPrompt, user) ?: return

        val sessionId = chatRepository.getSessionsByBook(novelId).firstOrNull()?.id
            ?: chatRepository.createSession("《${state.novel?.title ?: "新书"}》质检报告", novelId)
        chatRepository.appendAssistantMessage(sessionId, "【开启新书 · 阶段 6 质检报告】\n\n$reply")

        completeStage(
            state,
            nextStage = 7,
            status = PipelineStatus.COMPLETED,
            output = buildString {
                appendLine("阶段 6 质检完成，报告已输出至本书 AI 会话，未自动改文。")
                appendLine()
                append(reply)
            }
        )
    }

    private suspend fun completeStage(
        state: PipelineUiState,
        nextStage: Int,
        output: String,
        status: PipelineStatus = PipelineStatus.PAUSED
    ) {
        pipelineRepository.saveRun(
            PipelineRun(
                novelId = novelId,
                skillId = state.skill?.id ?: 0L,
                stage = nextStage,
                status = status,
                inputJson = state.info?.let { json.encodeToString(NewBookInfo.serializer(), it) }.orEmpty(),
                outputJson = output
            )
        )
        _uiState.update {
            it.copy(stage = nextStage, status = status, output = output)
        }
    }

    private suspend fun markFailed(message: String) {
        val state = _uiState.value
        pipelineRepository.saveRun(
            PipelineRun(
                novelId = novelId,
                skillId = state.skill?.id ?: 0L,
                stage = state.stage,
                status = PipelineStatus.FAILED,
                inputJson = state.info?.let { json.encodeToString(NewBookInfo.serializer(), it) }.orEmpty(),
                outputJson = state.output
            )
        )
        _uiState.update { it.copy(status = PipelineStatus.FAILED, message = message) }
    }

    private suspend fun chat(config: AiConfig, systemPrompt: String, userPrompt: String): String? {
        val result = aiClient.chat(
            config = config,
            history = listOf(ChatMessage(id = 0L, role = ChatRole.USER, content = userPrompt)),
            systemPrompt = systemPrompt
        )
        return result.getOrElse {
            markFailed(it.message ?: "AI 请求失败，请检查配置与网络")
            null
        }
    }

    private suspend fun requireConfig(): AiConfig? {
        val profiles = preferencesRepository.aiProfiles.first()
        val settings = preferencesRepository.settings.first()
        val active = profiles.firstOrNull { it.id == settings.activeAiProfileId }
            ?: profiles.firstOrNull()
        if (active == null || active.baseUrl.isBlank() || active.apiKey.isBlank() || active.model.isBlank()) {
            markFailed("请先在「设置 → AI 提供商」中配置可用的提供商")
            return null
        }
        return AiConfig(
            baseUrl = active.baseUrl,
            apiKey = active.apiKey,
            model = active.model,
            apiType = active.apiType
        )
    }

    private fun stageSystemPrompt(skill: Skill?, stageKey: String): String {
        val main = skill?.mainMarkdown?.let { stripFrontMatter(it) }.orEmpty()
        val reference = skill?.references
            ?.firstOrNull { it.name.contains(stageKey, ignoreCase = true) }
            ?.content.orEmpty()
        return buildString {
            if (main.isNotBlank()) appendLine(main)
            if (reference.isNotBlank()) {
                appendLine()
                appendLine("## 当前阶段作业规范")
                appendLine(reference)
            }
        }.ifBlank { "你是资深网文主编，请严格按阶段要求输出。" }
    }

    private suspend fun bookSettingsText(): String = buildString {
        val characters = settingRepository.getByCategory(novelId, SettingCategories.CHARACTER)
        val worlds = settingRepository.getByCategory(novelId, SettingCategories.WORLD)
        appendLine("### 世界观设定")
        if (worlds.isEmpty()) appendLine("（暂无）")
        worlds.forEach { appendLine("- ${it.title}：${it.content}") }
        appendLine()
        appendLine("### 人物卡")
        if (characters.isEmpty()) appendLine("（暂无）")
        characters.forEach {
            val role = it.subtitle?.takeIf { s -> s.isNotBlank() }?.let { s -> "（$s）" }.orEmpty()
            appendLine("- ${it.title}$role：${it.content}")
        }
    }

    private suspend fun outlineText(): String = buildString {
        pipelineRepository.getOutlines(novelId).forEach { outline ->
            appendLine("## ${outline.title}")
            appendLine(outline.content)
        }
    }

    private fun parseJson(raw: String): JsonObject {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        val block = if (start >= 0 && end > start) text.substring(start, end + 1) else text
        return json.parseToJsonElement(block).jsonObject
    }

    private fun JsonObject.arrayObjects(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    companion object {
        fun factory(
            novelId: Long,
            skillId: Long,
            novelRepository: NovelRepository,
            skillRepository: SkillRepository,
            pipelineRepository: PipelineRepository,
            settingRepository: SettingRepository,
            preferencesRepository: PreferencesRepository,
            chatRepository: ChatRepository,
            aiClient: AiClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PipelineViewModel(
                    novelId,
                    skillId,
                    novelRepository,
                    skillRepository,
                    pipelineRepository,
                    settingRepository,
                    preferencesRepository,
                    chatRepository,
                    aiClient
                ) as T
        }
    }
}
