package com.wenshu.writing.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.LibraryRepository
import com.wenshu.writing.domain.model.CharacterCard
import com.wenshu.writing.domain.model.WorldSetting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val characters: List<CharacterCard> = emptyList(),
    val worldSettings: List<WorldSetting> = emptyList()
)

class LibraryViewModel(
    private val repository: LibraryRepository
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> =
        combine(repository.characters, repository.worldSettings) { characters, worlds ->
            LibraryUiState(characters = characters, worldSettings = worlds)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState()
        )

    fun saveCharacter(card: CharacterCard, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveCharacter(card)
            onDone()
        }
    }

    fun deleteCharacter(id: Long) {
        viewModelScope.launch { repository.deleteCharacter(id) }
    }

    fun saveWorldSetting(setting: WorldSetting, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveWorldSetting(setting)
            onDone()
        }
    }

    fun deleteWorldSetting(id: Long) {
        viewModelScope.launch { repository.deleteWorldSetting(id) }
    }

    companion object {
        fun factory(repository: LibraryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(repository) as T
            }
    }
}
