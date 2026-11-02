package com.wenshu.writing.feature.works

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.BookArchive
import com.wenshu.writing.data.repository.LibraryRepository
import com.wenshu.writing.data.repository.MemoRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class WorksUiState(
    val novels: List<Novel> = emptyList(),
    val isLoading: Boolean = true
)

class WorksViewModel(
    private val repository: NovelRepository,
    private val libraryRepository: LibraryRepository,
    private val memoRepository: MemoRepository,
    private val preferencesRepository: PreferencesRepository
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
            preferencesRepository.setActiveNovel(id)
            onCreated(id)
        }
    }

    fun deleteNovel(novelId: Long) {
        viewModelScope.launch { repository.deleteNovel(novelId) }
    }

    /** 进入书稿时切换激活书稿，保证设定库/备忘录按本书隔离。 */
    fun openNovel(novelId: Long, onOpen: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setActiveNovel(novelId)
            onOpen()
        }
    }

    /** 导出整书数据包：本书全部章节、人物卡、世界观与备忘录。 */
    fun exportBook(
        novelId: Long,
        output: OutputStream,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val novel = repository.getNovel(novelId)
                        ?: throw IllegalArgumentException("书稿不存在")
                    val chapters = repository.observeChapters(novelId).first()
                    val characters = libraryRepository.getCharacters(novelId)
                    val worlds = libraryRepository.getWorldSettings(novelId)
                    val memos = memoRepository.getNotes(novelId)
                    BookArchive.write(output, novel, chapters, characters, worlds, memos)
                }
            }
            onResult(result)
        }
    }

    /** 导入整书数据包：所有数据自动归属新书稿 bookId。 */
    fun importBook(
        input: InputStream,
        onResult: (Result<Long>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val data = BookArchive.read(input)
                    val novelId = repository.createNovel(data.novel.title, data.novel.description)
                    data.chapters.sortedBy { it.orderIndex }.forEach { chapter ->
                        val id = repository.createChapter(novelId, chapter.title)
                        if (chapter.content.isNotBlank()) {
                            repository.saveChapter(id, chapter.title, chapter.content)
                        }
                    }
                    data.characters.forEach {
                        libraryRepository.addCharacter(it.name, it.role, it.description, novelId)
                    }
                    data.worldSettings.forEach {
                        libraryRepository.addWorldSetting(it.category, it.title, it.content, novelId)
                    }
                    memoRepository.addNotes(data.memos, novelId)
                    preferencesRepository.setActiveNovel(novelId)
                    novelId
                }
            }
            onResult(result)
        }
    }

    companion object {
        fun factory(
            repository: NovelRepository,
            libraryRepository: LibraryRepository,
            memoRepository: MemoRepository,
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WorksViewModel(
                    repository,
                    libraryRepository,
                    memoRepository,
                    preferencesRepository
                ) as T
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
