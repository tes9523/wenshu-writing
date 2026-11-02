package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wenshu.writing.data.local.entity.CharacterEntity
import com.wenshu.writing.data.local.entity.WorldSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT * FROM characters ORDER BY updatedAt DESC")
    fun observeCharacters(): Flow<List<CharacterEntity>>

    @Insert
    suspend fun insertCharacter(entity: CharacterEntity): Long

    @Update
    suspend fun updateCharacter(entity: CharacterEntity)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteCharacter(id: Long)

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getCharacter(id: Long): CharacterEntity?

    @Query("SELECT * FROM world_settings ORDER BY updatedAt DESC")
    fun observeWorldSettings(): Flow<List<WorldSettingEntity>>

    @Insert
    suspend fun insertWorldSetting(entity: WorldSettingEntity): Long

    @Update
    suspend fun updateWorldSetting(entity: WorldSettingEntity)

    @Query("DELETE FROM world_settings WHERE id = :id")
    suspend fun deleteWorldSetting(id: Long)

    @Query("SELECT * FROM world_settings WHERE id = :id")
    suspend fun getWorldSetting(id: Long): WorldSettingEntity?
}
