package com.wenshu.writing.feature.skill

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.SkillArchive
import com.wenshu.writing.data.repository.SkillRepository
import com.wenshu.writing.domain.model.Skill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SkillUiState(
    val search: String = "",
    val enabledOnly: Boolean = false,
    val builtInExpanded: Boolean = true,
    val customExpanded: Boolean = true,
    val isBusy: Boolean = false,
    val message: String? = null,
    val conflictNames: List<String> = emptyList(),
    val showConflictDialog: Boolean = false
)

class SkillViewModel(
    private val repository: SkillRepository,
    private val context: Context
) : ViewModel() {

    val skills: StateFlow<List<Skill>> = repository.skills.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _uiState = MutableStateFlow(SkillUiState())
    val uiState: StateFlow<SkillUiState> = _uiState.asStateFlow()

    private var pendingImport: List<SkillArchive.ImportedSkill> = emptyList()

    fun updateSearch(value: String) = _uiState.update { it.copy(search = value) }

    fun toggleEnabledOnly() = _uiState.update { it.copy(enabledOnly = !it.enabledOnly) }

    fun toggleBuiltInExpanded() =
        _uiState.update { it.copy(builtInExpanded = !it.builtInExpanded) }

    fun toggleCustomExpanded() =
        _uiState.update { it.copy(customExpanded = !it.customExpanded) }

    fun setEnabled(skill: Skill, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(skill.id, enabled)
        }
    }

    fun rename(skill: Skill, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(message = "技能名称不能为空") }
            return
        }
        if (trimmed == skill.name) return
        viewModelScope.launch {
            repository.update(skill.copy(name = trimmed))
            _uiState.update { it.copy(message = "已重命名为「$trimmed」") }
        }
    }

    fun delete(skill: Skill) {
        if (skill.isBuiltIn) {
            _uiState.update { it.copy(message = "内置技能不可删除") }
            return
        }
        viewModelScope.launch {
            repository.delete(skill.id)
            _uiState.update { it.copy(message = "已删除「${skill.name}」") }
        }
    }

    /** 解析 → 检测重名 → 无冲突直接导入，有冲突弹窗让用户选择覆盖或取消。 */
    fun importZip(uri: Uri) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.use { repository.parseImport(it) }
                        ?: throw IllegalArgumentException("无法读取所选文件")
                }
                val conflicts = repository.findConflicts(items)
                if (conflicts.isNotEmpty()) {
                    pendingImport = items
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            conflictNames = conflicts,
                            showConflictDialog = true
                        )
                    }
                } else {
                    repository.importSkills(items, overwrite = false)
                    _uiState.update { it.copy(isBusy = false, message = "Skill导入成功") }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = e.message ?: "导入失败，请检查压缩包"
                    )
                }
            }
        }
    }

    fun confirmOverwriteImport() {
        val items = pendingImport
        pendingImport = emptyList()
        _uiState.update { it.copy(showConflictDialog = false, isBusy = true) }
        viewModelScope.launch {
            try {
                repository.importSkills(items, overwrite = true)
                _uiState.update {
                    it.copy(isBusy = false, message = "Skill导入成功", conflictNames = emptyList())
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = e.message ?: "导入失败，请检查压缩包",
                        conflictNames = emptyList()
                    )
                }
            }
        }
    }

    fun cancelImport() {
        pendingImport = emptyList()
        _uiState.update {
            it.copy(showConflictDialog = false, conflictNames = emptyList(), message = "已取消导入")
        }
    }

    fun exportTo(uri: Uri, skillIds: List<Long>) {
        if (_uiState.value.isBusy || skillIds.isEmpty()) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val selected = skills.value.filter { it.id in skillIds }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        if (selected.size == 1) {
                            repository.exportSingle(stream, selected.first())
                        } else {
                            repository.exportBatch(stream, selected)
                        }
                    } ?: throw IllegalStateException("无法写入所选位置")
                }
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    message = result.fold(
                        onSuccess = {
                            if (selected.size == 1) {
                                "已导出「${selected.first().name}」"
                            } else {
                                "已导出 ${selected.size} 个技能的合集包"
                            }
                        },
                        onFailure = { error -> "导出失败：${error.message ?: "未知错误"}" }
                    )
                )
            }
        }
    }

    fun showSquarePlaceholder() =
        _uiState.update { it.copy(message = "技能广场即将上线，敬请期待") }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(repository: SkillRepository, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SkillViewModel(repository, context) as T
            }
    }
}
