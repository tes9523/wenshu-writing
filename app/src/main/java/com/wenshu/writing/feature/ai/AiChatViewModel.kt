package com.wenshu.writing.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.AiProfile
import com.wenshu.writing.domain.model.ApiConnStatus
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.Novel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiChatUiState(
    val baseUrl: String = AiConfig.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = AiConfig.DEFAULT_MODEL,
    val configLoaded: Boolean = false,
    val isConfigExpanded: Boolean = false,
    val profileName: String = "",
    val profiles: List<AiProfile> = emptyList(),
    val activeProfileId: Long? = null,
    val availableModels: List<String> = emptyList(),
    val isFetchingModels: Boolean = false,
    val connectionStatus: ApiConnStatus = ApiConnStatus.UNVERIFIED,
    val connectionMessage: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val novels: List<Novel> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val selectedNovelId: Long? = null,
    val selectedChapterId: Long? = null,
    val message: String? = null
)

class AiChatViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val novelRepository: NovelRepository,
    private val aiClient: AiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var nextMessageId = 0L

    init {
        viewModelScope.launch {
            preferencesRepository.aiProfiles.collect { profiles ->
                _uiState.update { it.copy(profiles = profiles) }
            }
        }

        viewModelScope.launch { loadInitialConfig() }

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

    /** 首次进入：优先恢复上次启用的配置集，其次把 1.0.0 的单条配置迁移为「默认配置」。 */
    private suspend fun loadInitialConfig() {
        val profiles = preferencesRepository.aiProfiles.first()
        val settings = preferencesRepository.settings.first()
        val legacy = preferencesRepository.aiConfig.first()

        if (profiles.isEmpty()) {
            if (legacy.apiKey.isNotBlank()) {
                val profile = AiProfile(
                    name = "默认配置",
                    baseUrl = legacy.baseUrl,
                    apiKey = legacy.apiKey,
                    model = legacy.model,
                    updatedAt = System.currentTimeMillis()
                )
                val id = preferencesRepository.saveAiProfile(profile)
                preferencesRepository.setActiveAiProfile(id)
                _uiState.update {
                    it.copy(
                        profiles = listOf(profile.copy(id = id)),
                        activeProfileId = id,
                        profileName = profile.name,
                        baseUrl = profile.baseUrl,
                        apiKey = profile.apiKey,
                        model = profile.model,
                        configLoaded = true,
                        isConfigExpanded = false,
                        connectionStatus = ApiConnStatus.UNVERIFIED,
                        connectionMessage = "未校验，保存配置或刷新模型时自动测试"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        profiles = emptyList(),
                        activeProfileId = null,
                        profileName = "默认配置",
                        baseUrl = legacy.baseUrl,
                        apiKey = "",
                        model = legacy.model,
                        configLoaded = true,
                        isConfigExpanded = true,
                        connectionStatus = ApiConnStatus.UNVERIFIED,
                        connectionMessage = "尚未配置，填写地址与 Key 后可保存"
                    )
                }
            }
            return
        }

        val active = profiles.firstOrNull { it.id == settings.activeAiProfileId } ?: profiles.first()
        preferencesRepository.setActiveAiProfile(active.id)
        _uiState.update {
            it.copy(
                profiles = profiles,
                activeProfileId = active.id,
                profileName = active.name,
                baseUrl = active.baseUrl,
                apiKey = active.apiKey,
                model = active.model,
                configLoaded = true,
                isConfigExpanded = active.apiKey.isBlank(),
                connectionStatus = ApiConnStatus.UNVERIFIED,
                connectionMessage = "未校验，保存配置或刷新模型时自动测试"
            )
        }
    }

    fun toggleConfigPanel() {
        _uiState.update { it.copy(isConfigExpanded = !it.isConfigExpanded) }
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
    }

    fun updateApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value) }
    }

    fun updateModel(value: String) {
        _uiState.update { it.copy(model = value) }
    }

    fun updateProfileName(value: String) {
        _uiState.update { it.copy(profileName = value) }
    }

    /** 一键切换已保存的配置集，并立即把草稿填充为该配置。 */
    fun selectProfile(profileId: Long) {
        val profile = _uiState.value.profiles.firstOrNull { it.id == profileId } ?: return
        _uiState.update {
            it.copy(
                activeProfileId = profile.id,
                profileName = profile.name,
                baseUrl = profile.baseUrl,
                apiKey = profile.apiKey,
                model = profile.model,
                availableModels = emptyList(),
                connectionStatus = ApiConnStatus.UNVERIFIED,
                connectionMessage = "已切换到「${profile.name}」，未校验",
                isConfigExpanded = true
            )
        }
        viewModelScope.launch { preferencesRepository.setActiveAiProfile(profile.id) }
    }

    /** 进入新建草稿状态，不覆盖已有配置集。 */
    fun startNewProfile() {
        _uiState.update {
            it.copy(
                activeProfileId = null,
                profileName = "新配置",
                baseUrl = AiConfig.DEFAULT_BASE_URL,
                apiKey = "",
                model = AiConfig.DEFAULT_MODEL,
                availableModels = emptyList(),
                connectionStatus = ApiConnStatus.UNVERIFIED,
                connectionMessage = "新配置，填写地址与 Key 后保存",
                isConfigExpanded = true
            )
        }
    }

    fun deleteActiveProfile() {
        val state = _uiState.value
        val profileId = state.activeProfileId ?: run {
            _uiState.update {
                it.copy(
                    activeProfileId = null,
                    profileName = "新配置",
                    availableModels = emptyList(),
                    connectionStatus = ApiConnStatus.UNVERIFIED,
                    connectionMessage = "已清空未保存的草稿"
                )
            }
            return
        }
        viewModelScope.launch {
            preferencesRepository.deleteAiProfile(profileId)
            val remaining = preferencesRepository.aiProfiles.first()
            val next = remaining.firstOrNull()
            if (next == null) {
                preferencesRepository.setActiveAiProfile(-1L)
                _uiState.update {
                    it.copy(
                        profiles = emptyList(),
                        activeProfileId = null,
                        profileName = "默认配置",
                        baseUrl = AiConfig.DEFAULT_BASE_URL,
                        apiKey = "",
                        model = AiConfig.DEFAULT_MODEL,
                        availableModels = emptyList(),
                        connectionStatus = ApiConnStatus.UNVERIFIED,
                        connectionMessage = "暂无保存的配置",
                        message = "已删除配置"
                    )
                }
            } else {
                preferencesRepository.setActiveAiProfile(next.id)
                _uiState.update {
                    it.copy(
                        profiles = remaining,
                        activeProfileId = next.id,
                        profileName = next.name,
                        baseUrl = next.baseUrl,
                        apiKey = next.apiKey,
                        model = next.model,
                        availableModels = emptyList(),
                        connectionStatus = ApiConnStatus.UNVERIFIED,
                        connectionMessage = "已删除配置，切换到「${next.name}」",
                        message = "已删除配置"
                    )
                }
            }
        }
    }

    /** 保存当前配置集，并自动发起一次连通测试。 */
    fun saveConfig() {
        val state = _uiState.value
        if (state.baseUrl.isBlank() || state.apiKey.isBlank() || state.model.isBlank()) {
            _uiState.update {
                it.copy(
                    isConfigExpanded = true,
                    connectionStatus = ApiConnStatus.FAILURE,
                    connectionMessage = "请先填写 API 地址、Key 与模型",
                    message = "配置不完整，请补全后再保存"
                )
            }
            return
        }

        val name = state.profileName.trim().ifBlank { "配置 ${state.profiles.size + 1}" }
        val baseUrl = state.baseUrl.trim()
        val apiKey = state.apiKey.trim()
        val model = state.model.trim()

        viewModelScope.launch {
            val profile = AiProfile(
                id = state.activeProfileId ?: 0L,
                name = name,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                updatedAt = System.currentTimeMillis()
            )
            val id = preferencesRepository.saveAiProfile(profile)
            preferencesRepository.setActiveAiProfile(id)
            _uiState.update {
                it.copy(
                    activeProfileId = id,
                    profileName = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    connectionStatus = ApiConnStatus.CHECKING,
                    connectionMessage = "配置已保存，正在测试连接…",
                    message = "配置已保存"
                )
            }
            val outcome = aiClient.probe(AiConfig(baseUrl, apiKey, model))
            _uiState.update { current ->
                current.copy(
                    connectionStatus = outcome.status,
                    connectionMessage = outcome.message,
                    availableModels = if (outcome.models.isEmpty()) {
                        current.availableModels
                    } else {
                        outcome.models
                    }
                )
            }
        }
    }

    /** 调用 /v1/models 拉取可用模型，并同时刷新连通状态。 */
    fun refreshModels() {
        val state = _uiState.value
        if (state.baseUrl.isBlank() || state.apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    isConfigExpanded = true,
                    connectionStatus = ApiConnStatus.UNVERIFIED,
                    connectionMessage = "请先填写 API 地址与 Key"
                )
            }
            return
        }
        if (state.isFetchingModels) return

        _uiState.update {
            it.copy(
                isFetchingModels = true,
                connectionStatus = ApiConnStatus.CHECKING,
                connectionMessage = "正在拉取模型列表…"
            )
        }
        viewModelScope.launch {
            val outcome = aiClient.probe(
                AiConfig(state.baseUrl.trim(), state.apiKey.trim(), state.model.trim())
            )
            _uiState.update { current ->
                current.copy(
                    isFetchingModels = false,
                    connectionStatus = outcome.status,
                    connectionMessage = outcome.message,
                    availableModels = if (outcome.status == ApiConnStatus.SUCCESS) {
                        outcome.models
                    } else {
                        current.availableModels
                    },
                    model = if (outcome.status == ApiConnStatus.SUCCESS &&
                        current.model.isBlank() &&
                        outcome.models.isNotEmpty()
                    ) {
                        outcome.models.first()
                    } else {
                        current.model
                    }
                )
            }
        }
    }

    fun updateInput(value: String) {
        _uiState.update { it.copy(input = value) }
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
                it.copy(
                    isConfigExpanded = true,
                    message = "请先在 API 配置中填写地址、Key 与模型"
                )
            }
            return
        }

        val userMessage = ChatMessage(id = nextMessageId++, role = ChatRole.USER, content = text)
        _uiState.update {
            it.copy(messages = it.messages + userMessage, input = "", isSending = true)
        }

        val config = AiConfig(state.baseUrl.trim(), state.apiKey.trim(), state.model.trim())
        viewModelScope.launch {
            val result = aiClient.chat(config = config, history = _uiState.value.messages)
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
        fun factory(
            preferencesRepository: PreferencesRepository,
            novelRepository: NovelRepository,
            aiClient: AiClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AiChatViewModel(preferencesRepository, novelRepository, aiClient) as T
        }
    }
}
