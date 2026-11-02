package com.wenshu.writing.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.domain.model.AiConfig
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
            val config = preferencesRepository.aiConfig.first()
            _uiState.update {
                it.copy(
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    model = config.model,
                    configLoaded = true,
                    isConfigExpanded = config.apiKey.isBlank()
                )
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

    fun saveConfig() {
        val state = _uiState.value
        viewModelScope.launch {
            preferencesRepository.saveAiConfig(
                AiConfig(
                    baseUrl = state.baseUrl.trim(),
                    apiKey = state.apiKey.trim(),
                    model = state.model.trim()
                )
            )
            _uiState.update { it.copy(message = "API 配置已保存") }
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
