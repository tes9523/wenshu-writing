package com.wenshu.writing.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.domain.model.countCharacters
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val title: String = "",
    val content: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val exists: Boolean = true,
    val lastSavedAt: Long? = null
) {
    val wordCount: Int get() = countCharacters(content)
}

class ChapterEditorViewModel(
    private val chapterId: Long,
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null
    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch {
            val chapter = repository.getChapter(chapterId)
            _uiState.value = if (chapter == null) {
                EditorUiState(isLoading = false, exists = false)
            } else {
                EditorUiState(
                    title = chapter.title,
                    content = chapter.content,
                    isLoading = false,
                    exists = true,
                    lastSavedAt = chapter.updatedAt
                )
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value, isDirty = true) }
        scheduleAutosave()
    }

    fun updateContent(value: String) {
        _uiState.update { it.copy(content = value, isDirty = true) }
        scheduleAutosave()
    }

    /** 保存当前章节，保存完成后回调（用于返回上一页） */
    fun save(onDone: (() -> Unit)? = null) {
        val snapshot = _uiState.value
        if (snapshot.isLoading || !snapshot.exists) {
            onDone?.invoke()
            return
        }
        if (!snapshot.isDirty) {
            onDone?.invoke()
            return
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            repository.saveChapter(chapterId, snapshot.title, snapshot.content)
            val savedAt = System.currentTimeMillis()
            _uiState.update { current ->
                val unchanged = current.title == snapshot.title && current.content == snapshot.content
                current.copy(
                    isSaving = false,
                    isDirty = if (unchanged) false else current.isDirty,
                    lastSavedAt = if (unchanged) savedAt else current.lastSavedAt
                )
            }
            onDone?.invoke()
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            if (_uiState.value.isDirty) save()
        }
    }

    companion object {
        private const val AUTOSAVE_DELAY_MS = 1_500L

        fun factory(chapterId: Long, repository: NovelRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChapterEditorViewModel(chapterId, repository) as T
            }
    }
}
