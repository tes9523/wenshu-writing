package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.LibraryDao
import com.wenshu.writing.data.local.entity.CharacterEntity
import com.wenshu.writing.data.local.entity.WorldSettingEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.CharacterCard
import com.wenshu.writing.domain.model.WorldSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val libraryDao: LibraryDao
) {

    fun observeCharacters(bookId: Long?): Flow<List<CharacterCard>> =
        libraryDao.observeCharacters(bookId).map { list -> list.map { it.toModel() } }

    fun observeWorldSettings(bookId: Long?): Flow<List<WorldSetting>> =
        libraryDao.observeWorldSettings(bookId).map { list -> list.map { it.toModel() } }

    suspend fun getCharacters(bookId: Long?): List<CharacterCard> = withContext(Dispatchers.IO) {
        libraryDao.getCharacters(bookId).map { it.toModel() }
    }

    suspend fun getWorldSettings(bookId: Long?): List<WorldSetting> = withContext(Dispatchers.IO) {
        libraryDao.getWorldSettings(bookId).map { it.toModel() }
    }

    suspend fun saveCharacter(card: CharacterCard) {
        val now = System.currentTimeMillis()
        if (card.id == 0L) {
            libraryDao.insertCharacter(
                CharacterEntity(
                    name = card.name.trim(),
                    role = card.role.trim(),
                    description = card.description.trim(),
                    bookId = card.bookId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            val existing = libraryDao.getCharacter(card.id) ?: return
            libraryDao.updateCharacter(
                existing.copy(
                    name = card.name.trim(),
                    role = card.role.trim(),
                    description = card.description.trim(),
                    bookId = card.bookId,
                    updatedAt = now
                )
            )
        }
    }

    /** 供流水线与整书导入使用：批量写入指定书稿。 */
    suspend fun addCharacter(name: String, role: String, description: String, bookId: Long?) {
        val now = System.currentTimeMillis()
        libraryDao.insertCharacter(
            CharacterEntity(
                name = name.trim(),
                role = role.trim(),
                description = description.trim(),
                bookId = bookId,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteCharacter(id: Long) {
        libraryDao.deleteCharacter(id)
    }

    suspend fun saveWorldSetting(setting: WorldSetting) {
        val now = System.currentTimeMillis()
        if (setting.id == 0L) {
            libraryDao.insertWorldSetting(
                WorldSettingEntity(
                    category = setting.category.trim(),
                    title = setting.title.trim(),
                    content = setting.content.trim(),
                    bookId = setting.bookId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            val existing = libraryDao.getWorldSetting(setting.id) ?: return
            libraryDao.updateWorldSetting(
                existing.copy(
                    category = setting.category.trim(),
                    title = setting.title.trim(),
                    content = setting.content.trim(),
                    bookId = setting.bookId,
                    updatedAt = now
                )
            )
        }
    }

    /** 供流水线与整书导入使用：批量写入指定书稿。 */
    suspend fun addWorldSetting(category: String, title: String, content: String, bookId: Long?) {
        val now = System.currentTimeMillis()
        libraryDao.insertWorldSetting(
            WorldSettingEntity(
                category = category.trim(),
                title = title.trim(),
                content = content.trim(),
                bookId = bookId,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteWorldSetting(id: Long) {
        libraryDao.deleteWorldSetting(id)
    }
}
