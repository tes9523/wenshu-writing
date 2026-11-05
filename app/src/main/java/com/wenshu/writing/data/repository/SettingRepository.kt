package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.SettingDao
import com.wenshu.writing.data.local.entity.SettingEntity
import com.wenshu.writing.data.local.entity.toEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.Setting
import com.wenshu.writing.domain.model.SettingCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingRepository(
    private val settingDao: SettingDao
) {

    fun observeCategories(bookId: Long?): Flow<List<String>> = settingDao.observeCategories(bookId)

    fun observeByCategory(bookId: Long?, category: String): Flow<List<Setting>> =
        settingDao.observeByCategory(bookId, category).map { list -> list.map { it.toModel() } }

    fun observeAll(bookId: Long?): Flow<List<Setting>> =
        settingDao.observeAll(bookId).map { list -> list.map { it.toModel() } }

    suspend fun getByCategory(bookId: Long?, category: String): List<Setting> = withContext(Dispatchers.IO) {
        settingDao.getByCategory(bookId, category).map { it.toModel() }
    }

    suspend fun getAll(bookId: Long?): List<Setting> = withContext(Dispatchers.IO) {
        settingDao.getAll(bookId).map { it.toModel() }
    }

    suspend fun addSetting(
        category: String,
        title: String,
        content: String,
        bookId: Long?,
        subtitle: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        settingDao.insert(
            SettingEntity(
                bookId = bookId,
                category = category,
                title = title,
                subtitle = subtitle?.takeIf { it.isNotBlank() },
                content = content,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun addCharacter(name: String, role: String, description: String, bookId: Long?): Long =
        addSetting(
            category = SettingCategories.CHARACTER,
            title = name,
            subtitle = role,
            content = description,
            bookId = bookId
        )

    suspend fun addWorldSetting(category: String, title: String, content: String, bookId: Long?): Long {
        val displayTitle = if (category.isNotBlank()) "[$category] $title" else title
        return addSetting(
            category = SettingCategories.WORLD,
            title = displayTitle,
            content = content,
            bookId = bookId
        )
    }

    suspend fun addOutline(title: String, content: String, bookId: Long?): Long =
        addSetting(
            category = SettingCategories.OUTLINE,
            title = title.ifBlank { "大纲" },
            content = content,
            bookId = bookId
        )

    suspend fun update(setting: Setting) = withContext(Dispatchers.IO) {
        settingDao.update(setting.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun delete(setting: Setting) = withContext(Dispatchers.IO) {
        settingDao.delete(setting.toEntity())
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        settingDao.deleteById(id)
    }

    /** 删除本书某个分类下全部条目（自定义分类删除时使用）。 */
    suspend fun deleteByCategory(bookId: Long?, category: String) = withContext(Dispatchers.IO) {
        settingDao.deleteByCategory(bookId, category)
    }

    /** 重命名分类：把本书该分类下所有条目的 category 改为新名称。 */
    suspend fun renameCategory(bookId: Long?, oldCategory: String, newCategory: String) =
        withContext(Dispatchers.IO) {
            val trimmed = newCategory.trim()
            if (trimmed.isEmpty() || trimmed == oldCategory) return@withContext
            val now = System.currentTimeMillis()
            // 注意必须原地 update：先 insert 同 id 再 deleteById 会把刚写入的新行一并删掉。
            settingDao.getByCategory(bookId, oldCategory).forEach { entity ->
                settingDao.update(entity.copy(category = trimmed, updatedAt = now))
            }
        }

    /** 整批替换某分类下的内容（流水线写大纲/人物/世界观时使用）。 */
    suspend fun replaceByCategory(bookId: Long?, category: String, items: List<Setting>) =
        withContext(Dispatchers.IO) {
            settingDao.deleteByCategory(bookId, category)
            if (items.isEmpty()) return@withContext
            val now = System.currentTimeMillis()
            settingDao.insertAll(
                items.map { setting ->
                    SettingEntity(
                        bookId = bookId,
                        category = category,
                        title = setting.title,
                        subtitle = setting.subtitle,
                        content = setting.content,
                        createdAt = now,
                        updatedAt = now
                    )
                }
            )
        }
}
