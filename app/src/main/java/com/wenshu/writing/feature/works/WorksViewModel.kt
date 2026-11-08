package com.wenshu.writing.feature.works

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wenshu.writing.data.repository.BookArchive
import com.wenshu.writing.data.repository.MemoRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PreferencesRepository
import com.wenshu.writing.data.repository.SettingRepository
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.ui.util.ExternalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

data class WorksUiState(
    val novels: List<Novel> = emptyList(),
    val isLoading: Boolean = true
)

class WorksViewModel(
    private val repository: NovelRepository,
    private val settingRepository: SettingRepository,
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

    /** 删除书稿改为移入回收站，数据保留，可随时恢复。 */
    fun deleteNovel(novelId: Long) {
        viewModelScope.launch { repository.moveToTrash(novelId) }
    }

    /** 保存或清除书稿自定义封面（null 表示恢复默认封面）。 */
    fun setCover(novelId: Long, coverPath: String?) {
        viewModelScope.launch { repository.setCover(novelId, coverPath) }
    }

    /** 进入书稿时切换激活书稿，保证设定库/备忘录按本书隔离。 */
    fun openNovel(novelId: Long, onOpen: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setActiveNovel(novelId)
            onOpen()
        }
    }

    /** 导出整书数据包：本书全部章节、设定与备忘录。 */
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
                    val settings = settingRepository.getAll(novelId)
                    val memos = memoRepository.getNotes(novelId)
                    BookArchive.write(output, novel, chapters, settings, memos)
                }
            }
            onResult(result)
        }
    }

    /** 导出到外部存储 wenshu/books，返回最终文件路径。 */
    fun exportBookToExternal(
        context: Context,
        novelId: Long,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val novel = repository.getNovel(novelId)
                        ?: throw IllegalArgumentException("书稿不存在")
                    val chapters = repository.observeChapters(novelId).first()
                    val settings = settingRepository.getAll(novelId)
                    val memos = memoRepository.getNotes(novelId)
                    val buffer = ByteArrayOutputStream()
                    BookArchive.write(buffer, novel, chapters, settings, memos)
                    val file = ExternalStorage.saveBytes(
                        context = context,
                        category = ExternalStorage.Category.BOOKS,
                        fileName = "${ExternalStorage.sanitizeFileName(novel.title)}.wenshu.zip",
                        bytes = buffer.toByteArray()
                    ) ?: throw IOException("写入 wenshu/books 失败，请检查存储权限")
                    file.absolutePath
                }
            }
            onResult(result)
        }
    }

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    /** 非空 = 正在导入，驱动进度弹窗。 */
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    private val _importResult = MutableStateFlow<Result<ImportSummary>?>(null)
    /** 一次性导入结果：成功（书名/章节数/编码）或失败（精准原因）。 */
    val importResult: StateFlow<Result<ImportSummary>?> = _importResult.asStateFlow()

    fun clearImportResult() {
        _importResult.value = null
    }

    /**
     * 导入本地小说文档（TXT / MD / DOCX）：
     * 读取文件 → 编码解析 → 智能分章 → 写入书稿，全程上报进度。
     */
    fun importNovelDocument(context: Context, uri: Uri) {
        if (_importProgress.value != null) return
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val fileName = displayName(context, uri) ?: "未命名.txt"
                    _importProgress.value = ImportProgress(fileName, ImportStage.READING, 0.05f)
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull() ?: throw ImportException("无法读取文件，请重新选择")
                    if (bytes.isEmpty()) throw ImportException("文件为空，未读取到任何内容")

                    _importProgress.value = ImportProgress(fileName, ImportStage.DECODING, 0.35f)
                    val parsed = NovelImporter.parse(fileName, bytes)

                    _importProgress.value = ImportProgress(fileName, ImportStage.SPLITTING, 0.55f)
                    val novelId = repository.createNovel(
                        parsed.title,
                        "导入自 ${parsed.formatName} 文档（${parsed.encoding}）"
                    )
                    parsed.chapters.forEachIndexed { index, chapter ->
                        val id = repository.createChapter(novelId, chapter.title)
                        if (chapter.content.isNotBlank()) {
                            repository.saveChapter(id, chapter.title, chapter.content)
                        }
                        _importProgress.value = ImportProgress(
                            fileName,
                            ImportStage.SAVING,
                            0.55f + 0.45f * (index + 1) / parsed.chapters.size
                        )
                    }
                    preferencesRepository.setActiveNovel(novelId)
                    _importProgress.value = ImportProgress(fileName, ImportStage.DONE, 1f)
                    ImportSummary(
                        novelId = novelId,
                        title = parsed.title,
                        chapterCount = parsed.chapters.size,
                        encoding = parsed.encoding,
                        formatName = parsed.formatName,
                        singleChapter = parsed.singleChapter
                    )
                }
            }
            _importProgress.value = null
            _importResult.value = result
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    companion object {
        fun factory(
            repository: NovelRepository,
            settingRepository: SettingRepository,
            memoRepository: MemoRepository,
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WorksViewModel(
                    repository,
                    settingRepository,
                    memoRepository,
                    preferencesRepository
                ) as T
        }
    }
}

/** 导入进度阶段。 */
enum class ImportStage(val label: String) {
    READING("读取文件"),
    DECODING("编码解析"),
    SPLITTING("文本分章"),
    SAVING("写入书稿"),
    DONE("导入完成")
}

data class ImportProgress(
    val fileName: String,
    val stage: ImportStage,
    val percent: Float
)

data class ImportSummary(
    val novelId: Long,
    val title: String,
    val chapterCount: Int,
    val encoding: String,
    val formatName: String,
    /** 未识别到章节标题时整本归入单章「全文」。 */
    val singleChapter: Boolean
)

data class ChapterListUiState(
    val novel: Novel? = null,
    val chapters: List<Chapter> = emptyList(),
    val isLoading: Boolean = true
)

data class TrashUiState(
    val novels: List<Novel> = emptyList(),
    val isLoading: Boolean = true
)

/** 回收站：已被软删除的书稿，可恢复或永久删除。 */
class TrashViewModel(
    private val repository: NovelRepository
) : ViewModel() {

    val uiState: StateFlow<TrashUiState> = repository.trash
        .map { novels -> TrashUiState(novels = novels, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TrashUiState()
        )

    fun restore(novelId: Long) {
        viewModelScope.launch { repository.restoreFromTrash(novelId) }
    }

    fun deletePermanently(novelId: Long) {
        viewModelScope.launch { repository.deleteNovelPermanently(novelId) }
    }

    companion object {
        fun factory(repository: NovelRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TrashViewModel(repository) as T
            }
    }
}

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

    /** 保存或清除本书封面（null 表示恢复默认封面）。 */
    fun setCover(coverPath: String?) {
        viewModelScope.launch { repository.setCover(novelId, coverPath) }
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
