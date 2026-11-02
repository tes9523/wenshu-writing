package com.wenshu.writing.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.AiProfile
import com.wenshu.writing.domain.model.ApiConnStatus
import com.wenshu.writing.domain.model.ApiProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 单个提供商的连接状态。 */
data class ProviderConnectionState(
    val status: ApiConnStatus = ApiConnStatus.UNVERIFIED,
    val message: String = ""
)

/** 正在新增或编辑的表单草稿。 */
data class ProviderEditorState(
    val id: Long = 0L,
    val name: String = "",
    val baseUrl: String = AiConfig.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val apiType: ApiProviderType = ApiProviderType.OPENAI,
    val model: String = "",
    val models: List<String> = emptyList(),
    val modelQuery: String = "",
    val status: ApiConnStatus = ApiConnStatus.UNVERIFIED,
    val statusMessage: String = "填写地址、密钥与模型后保存",
    val isFetchingModels: Boolean = false
) {
    val isExisting: Boolean get() = id != 0L
}

data class AiProviderUiState(
    val profiles: List<AiProfile> = emptyList(),
    val activeProfileId: Long? = null,
    val statuses: Map<Long, ProviderConnectionState> = emptyMap(),
    val editor: ProviderEditorState? = null,
    val isSaving: Boolean = false,
    val loaded: Boolean = false,
    val message: String? = null
)

class AiProviderViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val aiClient: AiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiProviderUiState())
    val uiState: StateFlow<AiProviderUiState> = _uiState.asStateFlow()

    /** 首次进入页面时自动校验过的提供商，避免重复探测。 */
    private val autoCheckedIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.aiProfiles,
                preferencesRepository.settings
            ) { profiles, settings -> profiles to settings }
                .collect { (profiles, settings) ->
                    val activeId = settings.activeAiProfileId
                        .takeIf { id -> profiles.any { it.id == id } }
                        ?: profiles.firstOrNull()?.id

                    _uiState.update {
                        it.copy(
                            profiles = profiles,
                            activeProfileId = activeId,
                            loaded = true
                        )
                    }

                    // 仅在没有任何有效激活项时兜底选中第一个；正常新增或编辑不会触发这里。
                    if (profiles.isNotEmpty() &&
                        profiles.none { it.id == settings.activeAiProfileId }
                    ) {
                        activeId?.let { preferencesRepository.setActiveAiProfile(it) }
                    }

                    checkStatuses(profiles)
                }
        }
    }

    private fun checkStatuses(profiles: List<AiProfile>) {
        profiles.filter { it.id !in autoCheckedIds }.forEach { profile ->
            autoCheckedIds.add(profile.id)
            _uiState.update {
                it.copy(
                    statuses = it.statuses + (
                        profile.id to ProviderConnectionState(
                            ApiConnStatus.CHECKING,
                            "正在校验连接…"
                        )
                        )
                )
            }
            viewModelScope.launch {
                val outcome = aiClient.probe(profile.toConfig())
                _uiState.update {
                    it.copy(
                        statuses = it.statuses + (
                            profile.id to ProviderConnectionState(
                                outcome.status,
                                outcome.message
                            )
                            )
                    )
                }
            }
        }
    }

    /** 点击卡片：这是唯一会改变激活提供商的入口。 */
    fun selectProfile(profileId: Long) {
        val state = _uiState.value
        if (state.activeProfileId == profileId) return
        val profile = state.profiles.firstOrNull { it.id == profileId } ?: return
        _uiState.update {
            it.copy(activeProfileId = profileId, message = "已切换到「${profile.name}」")
        }
        viewModelScope.launch { preferencesRepository.setActiveAiProfile(profileId) }
    }

    fun startAddProvider() {
        _uiState.update {
            it.copy(
                editor = ProviderEditorState(
                    baseUrl = AiConfig.DEFAULT_BASE_URL,
                    apiType = ApiProviderType.OPENAI,
                    statusMessage = "填写地址、密钥与模型后保存"
                )
            )
        }
    }

    fun startEditProvider(profileId: Long) {
        val state = _uiState.value
        val profile = state.profiles.firstOrNull { it.id == profileId } ?: return
        val connection = state.statuses[profile.id]
        _uiState.update {
            it.copy(
                editor = ProviderEditorState(
                    id = profile.id,
                    name = profile.name,
                    baseUrl = profile.baseUrl,
                    apiKey = profile.apiKey,
                    apiType = profile.apiType,
                    model = profile.model,
                    models = profile.models,
                    status = connection?.status ?: ApiConnStatus.UNVERIFIED,
                    statusMessage = connection?.message ?: "未校验"
                )
            )
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    private fun updateEditor(transform: (ProviderEditorState) -> ProviderEditorState) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = transform(editor))
        }
    }

    fun updateName(value: String) = updateEditor { it.copy(name = value) }

    fun updateBaseUrl(value: String) = updateEditor { it.copy(baseUrl = value) }

    fun updateApiKey(value: String) = updateEditor { it.copy(apiKey = value) }

    fun updateModel(value: String) = updateEditor { it.copy(model = value) }

    fun updateModelQuery(value: String) = updateEditor { it.copy(modelQuery = value) }

    fun updateApiType(type: ApiProviderType) = updateEditor { editor ->
        val openAiDefault = AiConfig.DEFAULT_BASE_URL
        val anthropicDefault = ANTHROPIC_DEFAULT_BASE_URL
        val currentDefault = when (editor.apiType) {
            ApiProviderType.OPENAI -> openAiDefault
            ApiProviderType.ANTHROPIC -> anthropicDefault
        }
        val baseUrl = if (editor.baseUrl.isBlank() || editor.baseUrl == currentDefault) {
            when (type) {
                ApiProviderType.OPENAI -> openAiDefault
                ApiProviderType.ANTHROPIC -> anthropicDefault
            }
        } else {
            editor.baseUrl
        }
        editor.copy(apiType = type, baseUrl = baseUrl)
    }

    /** 获取模型列表：只写回当前编辑的提供商，互不覆盖其他提供商缓存。 */
    fun refreshEditorModels() {
        val editor = _uiState.value.editor ?: return
        if (editor.isFetchingModels) return
        if (editor.baseUrl.isBlank() || editor.apiKey.isBlank()) {
            updateEditor {
                it.copy(
                    status = ApiConnStatus.UNVERIFIED,
                    statusMessage = "请先填写 API 地址与密钥"
                )
            }
            return
        }

        updateEditor {
            it.copy(
                isFetchingModels = true,
                status = ApiConnStatus.CHECKING,
                statusMessage = "正在获取模型列表…"
            )
        }
        viewModelScope.launch {
            val outcome = aiClient.probe(editor.toConfig())
            _uiState.update { state ->
                val current = state.editor ?: return@update state
                val models = if (outcome.status == ApiConnStatus.SUCCESS &&
                    outcome.models.isNotEmpty()
                ) {
                    outcome.models
                } else {
                    current.models
                }
                val model = current.model.ifBlank { outcome.models.firstOrNull().orEmpty() }
                state.copy(
                    editor = current.copy(
                        models = models,
                        model = model,
                        isFetchingModels = false,
                        status = outcome.status,
                        statusMessage = outcome.message
                    ),
                    statuses = if (editor.id != 0L) {
                        state.statuses + (
                            editor.id to ProviderConnectionState(
                                outcome.status,
                                outcome.message
                            )
                            )
                    } else {
                        state.statuses
                    }
                )
            }
            if (editor.id != 0L &&
                outcome.status == ApiConnStatus.SUCCESS &&
                outcome.models.isNotEmpty()
            ) {
                persistModels(editor.id, outcome.models)
            }
        }
    }

    /** 删除单个模型，仅影响当前提供商。 */
    fun removeModel(model: String) {
        val editor = _uiState.value.editor ?: return
        val nextModels = editor.models - model
        val nextSelected = if (editor.model == model) "" else editor.model
        updateEditor { it.copy(models = nextModels, model = nextSelected) }
        if (editor.id != 0L) {
            viewModelScope.launch { persistModels(editor.id, nextModels) }
        }
    }

    /**
     * 保存提供商。
     * 核心修复：新增或编辑都不会把激活项切走；只有当此前没有任何激活项时，才自动激活刚保存的提供商。
     */
    fun save(continueAdding: Boolean) {
        val editor = _uiState.value.editor ?: return
        val validationMessage = when {
            editor.name.isBlank() -> "请填写提供商名称"
            editor.baseUrl.isBlank() -> "请填写 API 地址"
            editor.apiKey.isBlank() -> "请填写 API Key"
            editor.model.isBlank() -> "请选择或填写模型"
            else -> null
        }
        if (validationMessage != null) {
            updateEditor {
                it.copy(status = ApiConnStatus.FAILURE, statusMessage = validationMessage)
            }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val profile = AiProfile(
                id = editor.id,
                name = editor.name.trim(),
                baseUrl = editor.baseUrl.trim(),
                apiKey = editor.apiKey.trim(),
                model = editor.model.trim(),
                apiType = editor.apiType,
                models = editor.models,
                updatedAt = System.currentTimeMillis()
            )
            val savedId = preferencesRepository.saveAiProfile(profile)
            autoCheckedIds.add(savedId)

            if (_uiState.value.activeProfileId == null) {
                preferencesRepository.setActiveAiProfile(savedId)
            }

            _uiState.update { state ->
                state.copy(
                    isSaving = false,
                    activeProfileId = state.activeProfileId ?: savedId,
                    editor = if (continueAdding) {
                        ProviderEditorState(
                            baseUrl = defaultBaseUrlOf(editor.apiType),
                            apiType = editor.apiType,
                            status = ApiConnStatus.UNVERIFIED,
                            statusMessage = "已保存「${profile.name}」，可继续添加"
                        )
                    } else {
                        null
                    },
                    message = "已保存「${profile.name}」"
                )
            }

            val outcome = aiClient.probe(profile.copy(id = savedId).toConfig())
            _uiState.update { state ->
                state.copy(
                    statuses = state.statuses + (
                        savedId to ProviderConnectionState(outcome.status, outcome.message)
                        )
                )
            }
        }
    }

    fun deleteProfile(profileId: Long) {
        val wasActive = _uiState.value.activeProfileId == profileId
        viewModelScope.launch {
            preferencesRepository.deleteAiProfile(profileId)
            autoCheckedIds.remove(profileId)
            val remaining = preferencesRepository.aiProfiles.first()
            val nextActive = if (wasActive) remaining.firstOrNull()?.id else _uiState.value.activeProfileId
            if (wasActive) {
                preferencesRepository.setActiveAiProfile(nextActive ?: -1L)
            }
            _uiState.update { state ->
                state.copy(
                    profiles = remaining,
                    activeProfileId = nextActive,
                    statuses = state.statuses - profileId,
                    editor = state.editor?.takeIf { it.id != profileId },
                    message = "已删除提供商"
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun persistModels(profileId: Long, models: List<String>) {
        val profile = _uiState.value.profiles.firstOrNull { it.id == profileId } ?: return
        preferencesRepository.saveAiProfile(
            profile.copy(models = models, updatedAt = System.currentTimeMillis())
        )
    }

    private fun defaultBaseUrlOf(type: ApiProviderType): String = when (type) {
        ApiProviderType.OPENAI -> AiConfig.DEFAULT_BASE_URL
        ApiProviderType.ANTHROPIC -> ANTHROPIC_DEFAULT_BASE_URL
    }

    private fun AiProfile.toConfig() = AiConfig(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        apiType = apiType
    )

    private fun ProviderEditorState.toConfig() = AiConfig(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        apiType = apiType
    )

    companion object {
        private const val ANTHROPIC_DEFAULT_BASE_URL = "https://api.anthropic.com/v1"

        fun factory(
            preferencesRepository: PreferencesRepository,
            aiClient: AiClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AiProviderViewModel(preferencesRepository, aiClient) as T
        }
    }
}
