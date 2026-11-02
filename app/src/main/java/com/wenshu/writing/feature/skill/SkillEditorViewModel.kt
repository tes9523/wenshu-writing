package com.wenshu.writing.feature.skill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.SkillRepository
import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.domain.model.SkillReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillEditorState(
    val id: Long = 0L,
    val name: String = "",
    val description: String = "",
    val mainMarkdown: String = "",
    val references: List<SkillReference> = emptyList(),
    val isBuiltIn: Boolean = false,
    val enabled: Boolean = true,
    val loaded: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null
) {
    val isNew: Boolean get() = id == 0L
}

class SkillEditorViewModel(
    private val repository: SkillRepository,
    private val skillId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SkillEditorState(
            id = skillId,
            loaded = skillId == 0L,
            mainMarkdown = if (skillId == 0L) DEFAULT_MAIN_MARKDOWN else ""
        )
    )
    val uiState: StateFlow<SkillEditorState> = _uiState.asStateFlow()

    init {
        if (skillId != 0L) {
            viewModelScope.launch {
                val skill = repository.getById(skillId)
                _uiState.update { state ->
                    if (skill == null) state
                    else state.copy(
                        id = skill.id,
                        name = skill.name,
                        description = skill.description,
                        mainMarkdown = skill.mainMarkdown,
                        references = skill.references,
                        isBuiltIn = skill.isBuiltIn,
                        enabled = skill.enabled,
                        loaded = true
                    )
                }
            }
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value) }

    fun updateDescription(value: String) = _uiState.update { it.copy(description = value) }

    fun updateMainMarkdown(value: String) = _uiState.update { it.copy(mainMarkdown = value) }

    fun addReference() {
        _uiState.update {
            it.copy(references = it.references + SkillReference(name = "notes.md", content = ""))
        }
    }

    fun updateReferenceName(index: Int, value: String) {
        _uiState.update { state ->
            val list = state.references.toMutableList()
            list.getOrNull(index) ?: return@update state
            list[index] = list[index].copy(name = value)
            state.copy(references = list)
        }
    }

    fun updateReferenceContent(index: Int, value: String) {
        _uiState.update { state ->
            val list = state.references.toMutableList()
            list.getOrNull(index) ?: return@update state
            list[index] = list[index].copy(content = value)
            state.copy(references = list)
        }
    }

    fun removeReference(index: Int) {
        _uiState.update { state ->
            if (index !in state.references.indices) return@update state
            state.copy(references = state.references.filterIndexed { i, _ -> i != index })
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.name.isBlank()) {
            _uiState.update { it.copy(message = "请填写技能名称") }
            return
        }
        if (state.mainMarkdown.isBlank()) {
            _uiState.update { it.copy(message = "请填写主提示词（main.md）") }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val references = state.references
                .filter { it.content.isNotBlank() || it.name.isNotBlank() }
                .map { it.copy(name = it.name.ifBlank { "reference.md" }) }
            if (state.isNew) {
                repository.create(
                    name = state.name,
                    description = state.description,
                    mainMarkdown = state.mainMarkdown,
                    references = references
                )
            } else {
                repository.update(
                    Skill(
                        id = state.id,
                        name = state.name,
                        description = state.description,
                        mainMarkdown = state.mainMarkdown,
                        references = references,
                        isBuiltIn = state.isBuiltIn,
                        enabled = state.enabled
                    )
                )
            }
            _uiState.update { it.copy(isSaving = false) }
            onSaved()
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        private const val DEFAULT_MAIN_MARKDOWN = """# 技能名称

## 适用场景
描述这个技能在什么情况下被激活。

## 工作流程
1. 第一步
2. 第二步

## 输出规范
描述 AI 输出内容的结构、语气与格式要求。
"""

        fun factory(repository: SkillRepository, skillId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SkillEditorViewModel(repository, skillId) as T
            }
    }
}
