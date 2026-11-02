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
    val isPipeline: Boolean = false,
    val enabled: Boolean = true,
    val loaded: Boolean = true,
    val isEditing: Boolean = false,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val showSaveConfirm: Boolean = false,
    val showExitConfirm: Boolean = false,
    val closeRequested: Boolean = false,
    val message: String? = null
) {
    val isNew: Boolean get() = id == 0L
    /** 内置 Skill 可以进入编辑查看，但保存永远不可用。 */
    val canSave: Boolean get() = !isBuiltIn && !isSaving
}

class SkillEditorViewModel(
    private val repository: SkillRepository,
    private val skillId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SkillEditorState(
            id = skillId,
            loaded = skillId == 0L,
            isEditing = skillId == 0L,
            mainMarkdown = if (skillId == 0L) DEFAULT_MAIN_MARKDOWN else ""
        )
    )
    val uiState: StateFlow<SkillEditorState> = _uiState.asStateFlow()

    private var baseline: Skill? = null

    init {
        if (skillId != 0L) {
            viewModelScope.launch {
                val skill = repository.getById(skillId)
                if (skill == null) {
                    _uiState.update { it.copy(loaded = true, message = "技能不存在") }
                    return@launch
                }
                baseline = skill
                _uiState.update {
                    it.copy(
                        id = skill.id,
                        name = skill.name,
                        description = skill.description,
                        mainMarkdown = skill.mainMarkdown,
                        references = skill.references,
                        isBuiltIn = skill.isBuiltIn,
                        isPipeline = skill.isPipeline,
                        enabled = skill.enabled,
                        loaded = true,
                        isEditing = false,
                        isDirty = false
                    )
                }
            }
        }
    }

    fun startEditing() {
        if (_uiState.value.isEditing) return
        _uiState.update { it.copy(isEditing = true, isDirty = false) }
    }

    fun cancelEditing() {
        val base = baseline
        if (base == null) {
            _uiState.update { it.copy(isEditing = false, isDirty = false) }
        } else {
            _uiState.update {
                it.copy(
                    name = base.name,
                    description = base.description,
                    mainMarkdown = base.mainMarkdown,
                    references = base.references,
                    isEditing = false,
                    isDirty = false
                )
            }
        }
    }

    fun updateName(value: String) = edit { it.copy(name = value) }

    fun updateDescription(value: String) = edit { it.copy(description = value) }

    fun updateMainMarkdown(value: String) = edit { it.copy(mainMarkdown = value) }

    fun addReference() = edit {
        it.copy(references = it.references + SkillReference(name = "notes.md", content = ""))
    }

    fun updateReferenceName(index: Int, value: String) = edit { state ->
        val list = state.references.toMutableList()
        list.getOrNull(index) ?: return@edit state
        list[index] = list[index].copy(name = value)
        state.copy(references = list)
    }

    fun updateReferenceContent(index: Int, value: String) = edit { state ->
        val list = state.references.toMutableList()
        list.getOrNull(index) ?: return@edit state
        list[index] = list[index].copy(content = value)
        state.copy(references = list)
    }

    fun removeReference(index: Int) = edit { state ->
        if (index !in state.references.indices) return@edit state
        state.copy(references = state.references.filterIndexed { i, _ -> i != index })
    }

    /** 点击【保存】：先校验，再弹出二次确认。 */
    fun requestSave() {
        val state = _uiState.value
        if (state.isBuiltIn) {
            _uiState.update { it.copy(message = "内置Skill不支持修改，可导出后新建自定义Skill修改") }
            return
        }
        if (!state.isEditing || state.isSaving) return
        if (state.name.isBlank()) {
            _uiState.update { it.copy(message = "请填写技能名称") }
            return
        }
        if (state.mainMarkdown.isBlank()) {
            _uiState.update { it.copy(message = "请填写主提示词（main.md）") }
            return
        }
        _uiState.update { it.copy(showSaveConfirm = true) }
    }

    fun dismissSaveConfirm() = _uiState.update { it.copy(showSaveConfirm = false) }

    /** 二次确认后入库；新建技能保存后返回列表，已有技能保存后回到只读。 */
    fun confirmSave(onCreated: () -> Unit) {
        val state = _uiState.value
        if (state.isBuiltIn || state.isSaving) return
        _uiState.update { it.copy(showSaveConfirm = false, isSaving = true) }
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
                onCreated()
            } else {
                repository.update(
                    Skill(
                        id = state.id,
                        name = state.name,
                        description = state.description,
                        mainMarkdown = state.mainMarkdown,
                        references = references,
                        isBuiltIn = state.isBuiltIn,
                        enabled = state.enabled,
                        isPipeline = state.isPipeline
                    )
                )
                baseline = Skill(
                    id = state.id,
                    name = state.name,
                    description = state.description,
                    mainMarkdown = state.mainMarkdown,
                    references = references,
                    isBuiltIn = state.isBuiltIn,
                    enabled = state.enabled,
                    isPipeline = state.isPipeline
                )
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isEditing = false,
                        isDirty = false,
                        message = "已保存技能修改"
                    )
                }
            }
        }
    }

    /** 返回键/左上角返回：编辑中有未保存修改时拦截。 */
    fun requestExit() {
        val state = _uiState.value
        if (state.isEditing && state.isDirty) {
            _uiState.update { it.copy(showExitConfirm = true) }
        } else {
            _uiState.update { it.copy(closeRequested = true) }
        }
    }

    fun stayOnPage() = _uiState.update { it.copy(showExitConfirm = false) }

    fun discardAndExit() {
        _uiState.update { it.copy(showExitConfirm = false, closeRequested = true) }
    }

    fun consumeCloseRequest() = _uiState.update { it.copy(closeRequested = false) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun edit(transform: (SkillEditorState) -> SkillEditorState) {
        _uiState.update { current ->
            if (!current.isEditing) return@update current
            val next = transform(current)
            next.copy(isDirty = computeDirty(next))
        }
    }

    private fun computeDirty(state: SkillEditorState): Boolean {
        val base = baseline ?: return state.name.isNotBlank() ||
            state.description.isNotBlank() ||
            state.references.isNotEmpty() ||
            state.mainMarkdown != DEFAULT_MAIN_MARKDOWN
        return state.name != base.name ||
            state.description != base.description ||
            state.mainMarkdown != base.mainMarkdown ||
            state.references != base.references
    }

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
