package com.wenshu.writing.data.repository

import android.content.Context
import com.wenshu.writing.domain.model.Setting
import com.wenshu.writing.ui.util.ExternalStorage
import java.io.File

/**
 * wenshu 外部目录与数据库的双向同步：
 * 新建书稿/设定时写入对应文件夹，删除（含重命名/换分类）时同步清理，杜绝残留文件。
 * 全部操作容错：无存储权限或写入失败时静默跳过，数据库始终是唯一数据源。
 */
class FileSyncManager(
    private val appContext: Context,
    private val bookTitleResolver: suspend (Long) -> String?
) {

    private fun root(): File? = ExternalStorage.ensureDirectories(appContext)

    private fun bookDir(root: File, title: String): File =
        File(File(root, ExternalStorage.BOOKS), ExternalStorage.sanitizeFileName(title))

    private fun settingBookDir(root: File, bookName: String): File =
        File(File(root, ExternalStorage.SETTING), ExternalStorage.sanitizeFileName(bookName))

    private fun settingFile(root: File, bookName: String, category: String, title: String): File =
        File(
            File(settingBookDir(root, bookName), ExternalStorage.sanitizeFileName(category)),
            ExternalStorage.sanitizeFileName(title) + ".md"
        )

    /** 新建书稿：在 wenshu/books 下建立同名文件夹。 */
    fun onNovelCreated(title: String) {
        runCatching {
            val r = root() ?: return
            bookDir(r, title).mkdirs()
        }
    }

    /** 彻底删除书稿：wenshu/books 同名文件夹与该书设定同步目录一并清除。 */
    fun onNovelDeleted(title: String) {
        runCatching {
            val r = root() ?: return
            bookDir(r, title).deleteRecursively()
            settingBookDir(r, title).deleteRecursively()
        }
    }

    /** 新增/更新设定：写入 wenshu/setting/<书名>/<分类>/<标题>.md；改名或换分类时清除旧文件。 */
    suspend fun onSettingSaved(
        bookId: Long?,
        category: String,
        title: String,
        subtitle: String?,
        content: String,
        oldCategory: String? = null,
        oldTitle: String? = null
    ) {
        runCatching {
            val r = root() ?: return
            val bookName = bookId?.let { bookTitleResolver(it) } ?: GLOBAL_BOOK
            val categoryChanged = oldCategory != null && oldCategory != category
            val titleChanged = oldTitle != null && oldTitle != title
            if (categoryChanged || titleChanged) {
                settingFile(r, bookName, oldCategory ?: category, oldTitle ?: title).delete()
            }
            val markdown = buildString {
                append("# ").appendLine(title)
                if (!subtitle.isNullOrBlank()) {
                    appendLine()
                    append("> ").appendLine(subtitle)
                }
                if (content.isNotBlank()) {
                    appendLine()
                    appendLine(content.trim())
                }
            }
            val file = settingFile(r, bookName, category, title)
            file.parentFile?.mkdirs()
            file.writeText(markdown, Charsets.UTF_8)
        }
    }

    /** 删除设定：同步删除对应 md 文件。 */
    suspend fun onSettingDeleted(bookId: Long?, category: String, title: String) {
        runCatching {
            val r = root() ?: return
            val bookName = bookId?.let { bookTitleResolver(it) } ?: GLOBAL_BOOK
            settingFile(r, bookName, category, title).delete()
        }
    }

    /** 删除整个分类：删除对应文件夹及内部全部文件。 */
    suspend fun onCategoryDeleted(bookId: Long?, category: String) {
        runCatching {
            val r = root() ?: return
            val bookName = bookId?.let { bookTitleResolver(it) } ?: GLOBAL_BOOK
            File(settingBookDir(r, bookName), ExternalStorage.sanitizeFileName(category))
                .deleteRecursively()
        }
    }

    /** 分类重命名：同步重命名文件夹。 */
    suspend fun onCategoryRenamed(bookId: Long?, oldCategory: String, newCategory: String) {
        runCatching {
            val r = root() ?: return
            val bookName = bookId?.let { bookTitleResolver(it) } ?: GLOBAL_BOOK
            val dir = File(settingBookDir(r, bookName), ExternalStorage.sanitizeFileName(oldCategory))
            if (dir.isDirectory) {
                dir.renameTo(
                    File(settingBookDir(r, bookName), ExternalStorage.sanitizeFileName(newCategory))
                )
            }
        }
    }

    /** 整批替换分类内容：清空旧文件夹后重写全部条目。 */
    suspend fun onCategoryReplaced(bookId: Long?, category: String, items: List<Setting>) {
        runCatching {
            val r = root() ?: return
            val bookName = bookId?.let { bookTitleResolver(it) } ?: GLOBAL_BOOK
            val dir = File(settingBookDir(r, bookName), ExternalStorage.sanitizeFileName(category))
            dir.deleteRecursively()
            items.forEach { setting ->
                val file = settingFile(r, bookName, category, setting.title)
                file.parentFile?.mkdirs()
                file.writeText(
                    buildString {
                        append("# ").appendLine(setting.title)
                        if (!setting.subtitle.isNullOrBlank()) {
                            appendLine()
                            append("> ").appendLine(setting.subtitle)
                        }
                        if (setting.content.isNotBlank()) {
                            appendLine()
                            appendLine(setting.content.trim())
                        }
                    },
                    Charsets.UTF_8
                )
            }
        }
    }

    companion object {
        const val GLOBAL_BOOK = "全局"
    }
}
