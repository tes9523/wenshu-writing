package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.LibraryDao
import com.wenshu.writing.data.local.entity.CharacterEntity
import com.wenshu.writing.data.local.entity.WorldSettingEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.CharacterCard
import com.wenshu.writing.domain.model.WorldSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(
    private val libraryDao: LibraryDao
) {

    val characters: Flow<List<CharacterCard>> =
        libraryDao.observeCharacters().map { list -> list.map { it.toModel() } }

    val worldSettings: Flow<List<WorldSetting>> =
        libraryDao.observeWorldSettings().map { list -> list.map { it.toModel() } }

    suspend fun saveCharacter(card: CharacterCard) {
        val now = System.currentTimeMillis()
        if (card.id == 0L) {
            libraryDao.insertCharacter(
                CharacterEntity(
                    name = card.name.trim(),
                    role = card.role.trim(),
                    description = card.description.trim(),
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
                    updatedAt = now
                )
            )
        }
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
                    updatedAt = now
                )
            )
        }
    }

    suspend fun deleteWorldSetting(id: Long) {
        libraryDao.deleteWorldSetting(id)
    }
}
