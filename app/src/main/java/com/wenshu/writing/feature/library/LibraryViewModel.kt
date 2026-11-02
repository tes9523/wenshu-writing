package com.wenshu.writing.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.data.repository.SettingRepository
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.Setting
import com.wenshu.writing.domain.model.SettingCategories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val items: List<Setting> = emptyList(),
    val categories: List<String> = SettingCategories.BUILT_IN,
    val selectedCategory: String = SettingCategories.CHARACTER,
    val novels: List<Novel> = emptyList(),
    val activeBookId: Long? = null,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: SettingRepository,
    private val novelRepository: NovelRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val bookSelection = combine(
        preferencesRepository.settings,
        novelRepository.novels
    ) { settings, novels ->
        val bookId = settings.activeNovelId.takeIf { it > 0L }
        val selected = novels.firstOrNull { it.id == bookId }?.id ?: novels.firstOrNull()?.id
        selected to novels
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to emptyList())

    init {
        viewModelScope.launch {
            combine(
                bookSelection,
                _uiState.map { it.selectedCategory }.distinctUntilChanged()
            ) { (bookId, novels), category -> Triple(bookId, novels, category) }
                .flatMapLatest { (bookId, novels, category) ->
                    combine(
                        repository.observeCategories(bookId),
                        repository.observeByCategory(bookId, category)
                    ) { categories, items ->
                        val all = (SettingCategories.BUILT_IN +
                            categories.filterNot { SettingCategories.isBuiltIn(it) }).distinct()
                        Triple(all, items, bookId to novels)
                    }
                }
                .collect { (categories, items, meta) ->
                    val (bookId, novels) = meta
                    _uiState.update { current ->
                        val selected = if (categories.contains(current.selectedCategory)) {
                            current.selectedCategory
                        } else {
                            categories.firstOrNull() ?: SettingCategories.CHARACTER
                        }
                        current.copy(
                            activeBookId = bookId,
                            novels = novels,
                            categories = categories,
                            selectedCategory = selected,
                            items = items
                        )
                    }
                }
        }
    }

    fun selectBook(bookId: Long?) {
        viewModelScope.launch { preferencesRepository.setActiveNovel(bookId ?: -1L) }
    }

    fun selectCategory(category: String) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun createSetting(title: String, subtitle: String, content: String, onDone: () -> Unit) {
        if (title.isBlank()) {
            _uiState.update { it.copy(message = "名称不能为空") }
            return
        }
        val state = _uiState.value
        viewModelScope.launch {
            repository.addSetting(
                category = state.selectedCategory,
                title = title.trim(),
                subtitle = subtitle.trim().ifBlank { null },
                content = content,
                bookId = state.activeBookId
            )
            _uiState.update { it.copy(message = "已添加到「${state.selectedCategory}」") }
            onDone()
        }
    }

    fun saveSetting(setting: Setting, onDone: () -> Unit) {
        if (setting.title.isBlank()) {
            _uiState.update { it.copy(message = "名称不能为空") }
            return
        }
        viewModelScope.launch {
            repository.update(setting)
            onDone()
        }
    }

    fun deleteSetting(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
            _uiState.update { it.copy(message = "已删除") }
        }
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(message = "分类名称不能为空") }
            return
        }
        if (SettingCategories.isBuiltIn(trimmed)) {
            _uiState.update { it.copy(message = "「$trimmed」是系统内置分类，不能重复创建") }
            return
        }
        if (_uiState.value.categories.contains(trimmed)) {
            _uiState.update { it.copy(message = "该分类已存在") }
            return
        }
        viewModelScope.launch {
            // 创建一条占位设定，让分类出现在列表中；用户可继续编辑或删除。
            repository.addSetting(
                category = trimmed,
                title = "（空分类，点击 + 添加条目）",
                content = "",
                bookId = _uiState.value.activeBookId
            )
            _uiState.update { it.copy(selectedCategory = trimmed, message = "已创建分类「$trimmed」") }
        }
    }

    fun renameCategory(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(message = "分类名称不能为空") }
            return
        }
        if (SettingCategories.isBuiltIn(oldName)) {
            _uiState.update { it.copy(message = "内置分类不可改名") }
            return
        }
        if (SettingCategories.isBuiltIn(trimmed) || _uiState.value.categories.contains(trimmed)) {
            _uiState.update { it.copy(message = "分类名称重复，请换一个") }
            return
        }
        viewModelScope.launch {
            repository.renameCategory(_uiState.value.activeBookId, oldName, trimmed)
            _uiState.update { it.copy(selectedCategory = trimmed, message = "分类已改名为「$trimmed」") }
        }
    }

    fun deleteCategory(name: String) {
        if (SettingCategories.isBuiltIn(name)) {
            _uiState.update { it.copy(message = "内置分类不可删除") }
            return
        }
        viewModelScope.launch {
            repository.deleteByCategory(_uiState.value.activeBookId, name)
            _uiState.update {
                it.copy(
                    selectedCategory = SettingCategories.CHARACTER,
                    message = "已删除分类「$name」及其条目"
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(
            repository: SettingRepository,
            novelRepository: NovelRepository,
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(repository, novelRepository, preferencesRepository) as T
        }
    }
}
