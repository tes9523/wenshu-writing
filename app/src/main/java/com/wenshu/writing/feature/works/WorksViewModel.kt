package com.wenshu.writing.feature.works

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.Novel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorksUiState(
    val novels: List<Novel> = emptyList(),
    val isLoading: Boolean = true
)

class WorksViewModel(
    private val repository: NovelRepository
) : ViewModel() {

    val uiState: StateFlow<WorksUiState> = repository.novels
        .map { novels -> WorksUiState(novels = novels, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WorksUiState()
        )

    fun createNovel(title: String, description: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createNovel(title, description)
            onCreated(id)
        }
    }

    fun deleteNovel(novelId: Long) {
        viewModelScope.launch { repository.deleteNovel(novelId) }
    }

    companion object {
        fun factory(repository: NovelRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorksViewModel(repository) as T
            }
    }
}

data class ChapterListUiState(
    val novel: Novel? = null,
    val chapters: List<Chapter> = emptyList(),
    val isLoading: Boolean = true
)

class ChapterListViewModel(
    private val novelId: Long,
    private val repository: NovelRepository
) : ViewModel() {

    val uiState: StateFlow<ChapterListUiState> =
        combine(
            repository.observeNovel(novelId),
            repository.observeChapters(novelId)
        ) { novel, chapters ->
            ChapterListUiState(novel = novel, chapters = chapters, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChapterListUiState()
        )

    fun createChapter(title: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createChapter(novelId, title)
            onCreated(id)
        }
    }

    fun deleteChapter(chapterId: Long) {
        viewModelScope.launch { repository.deleteChapter(chapterId) }
    }

    companion object {
        fun factory(novelId: Long, repository: NovelRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChapterListViewModel(novelId, repository) as T
            }
    }
}
