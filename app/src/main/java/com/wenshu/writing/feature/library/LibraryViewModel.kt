package com.wenshu.writing.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.LibraryRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.domain.model.CharacterCard
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.WorldSetting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val characters: List<CharacterCard> = emptyList(),
    val worldSettings: List<WorldSetting> = emptyList(),
    val novels: List<Novel> = emptyList(),
    /** 当前书稿；null 表示全局公共素材。 */
    val activeBookId: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: LibraryRepository,
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

    val uiState: StateFlow<LibraryUiState> = bookSelection
        .flatMapLatest { (bookId, novels) ->
            combine(
                repository.observeCharacters(bookId),
                repository.observeWorldSettings(bookId)
            ) { characters, worlds ->
                LibraryUiState(
                    characters = characters,
                    worldSettings = worlds,
                    novels = novels,
                    activeBookId = bookId
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState()
        )

    /** 切换书稿；null 切换到全局公共素材。 */
    fun selectBook(bookId: Long?) {
        viewModelScope.launch {
            preferencesRepository.setActiveNovel(bookId ?: -1L)
        }
    }

    fun saveCharacter(card: CharacterCard, onDone: () -> Unit) {
        viewModelScope.launch {
            // 新建时强制归属当前书稿；编辑时保留原有归属，避免跨书搬移。
            val target = if (card.id == 0L) card.copy(bookId = uiState.value.activeBookId) else card
            repository.saveCharacter(target)
            onDone()
        }
    }

    fun deleteCharacter(id: Long) {
        viewModelScope.launch { repository.deleteCharacter(id) }
    }

    fun saveWorldSetting(setting: WorldSetting, onDone: () -> Unit) {
        viewModelScope.launch {
            val target = if (setting.id == 0L) {
                setting.copy(bookId = uiState.value.activeBookId)
            } else {
                setting
            }
            repository.saveWorldSetting(target)
            onDone()
        }
    }

    fun deleteWorldSetting(id: Long) {
        viewModelScope.launch { repository.deleteWorldSetting(id) }
    }

    companion object {
        fun factory(
            repository: LibraryRepository,
            preferencesRepository: PreferencesRepository,
            novelRepository: NovelRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(repository, preferencesRepository, novelRepository) as T
        }
    }
}
