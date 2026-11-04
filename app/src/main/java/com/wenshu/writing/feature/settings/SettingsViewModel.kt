package com.wenshu.writing.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.domain.model.AppSettings
import com.wenshu.writing.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: PreferencesRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setThemeColor(name: String) {
        viewModelScope.launch { repository.setThemeColor(name) }
    }

    fun setBackgroundColor(name: String) {
        viewModelScope.launch { repository.setBackgroundColor(name) }
    }

    fun setBackgroundImage(path: String?) {
        viewModelScope.launch { repository.setBackgroundImage(path) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundEnabled(enabled) }
    }

    fun setSoundType(type: String) {
        viewModelScope.launch { repository.setSoundType(type) }
    }

    fun setFontName(name: String) {
        viewModelScope.launch { repository.setFontName(name) }
    }

    fun setChatFontSize(size: Int) {
        viewModelScope.launch { repository.setChatFontSize(size) }
    }

    companion object {
        fun factory(repository: PreferencesRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(repository) as T
            }
    }
}
