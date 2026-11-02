package com.wenshu.writing.feature.memo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.MemoRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.domain.model.MemoNote
import com.wenshu.writing.domain.model.Novel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MemoUiState(
    val notes: List<MemoNote> = emptyList(),
    val novels: List<Novel> = emptyList(),
    /** 当前书稿；null 表示全局公共备忘录。 */
    val activeBookId: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MemoViewModel(
    private val repository: MemoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val novelRepository: NovelRepository
) : ViewModel() {

    private val bookSelection = combine(
        preferencesRepository.settings,
        novelRepository.novels
    ) { settings, novels ->
        val active = settings.activeNovelId.takeIf { id -> novels.any { it.id == id } }
        active to novels
    }

    val uiState: StateFlow<MemoUiState> = bookSelection
        .flatMapLatest { (bookId, novels) ->
            repository.observeNotes(bookId).map { notes ->
                MemoUiState(notes = notes, novels = novels, activeBookId = bookId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MemoUiState()
        )

    fun selectBook(bookId: Long?) {
        viewModelScope.launch { preferencesRepository.setActiveNovel(bookId ?: -1L) }
    }

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.create(title = "", content = "", bookId = uiState.value.activeBookId)
            onCreated(id)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        fun factory(
            repository: MemoRepository,
            preferencesRepository: PreferencesRepository,
            novelRepository: NovelRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MemoViewModel(repository, preferencesRepository, novelRepository) as T
        }
    }
}

data class MemoEditorUiState(
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val bookId: Long? = null,
    val createdAt: Long = 0L,
    val loaded: Boolean = false
)

class MemoEditorViewModel(
    private val repository: MemoRepository,
    private val noteId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoEditorUiState(id = noteId))
    val uiState: StateFlow<MemoEditorUiState> = _uiState.asStateFlow()

    private val saveMutex = Mutex()

    init {
        viewModelScope.launch {
            val note = repository.observeNote(noteId).first()
            if (note != null) {
                _uiState.value = MemoEditorUiState(
                    id = note.id,
                    title = note.title,
                    content = note.content,
                    bookId = note.bookId,
                    createdAt = note.createdAt,
                    loaded = true
                )
            } else {
                _uiState.update { it.copy(loaded = true) }
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value) }
        persist()
    }

    fun updateContent(value: String) {
        _uiState.update { it.copy(content = value) }
        persist()
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(noteId)
            onDeleted()
        }
    }

    /** 每次编辑后直接落库，保证返回上一页时不会丢失内容。 */
    private fun persist() {
        if (!_uiState.value.loaded) return
        viewModelScope.launch {
            saveMutex.withLock {
                // 在锁内读取最新状态，避免较早的写入覆盖较新的输入。
                val state = _uiState.value
                repository.update(
                    MemoNote(
                        id = state.id,
                        title = state.title,
                        content = state.content,
                        bookId = state.bookId,
                        createdAt = state.createdAt,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    companion object {
        fun factory(repository: MemoRepository, noteId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MemoEditorViewModel(repository, noteId) as T
            }
    }
}
