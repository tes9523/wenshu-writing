package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wenshu.writing.data.local.entity.AiConfigEntity
import com.wenshu.writing.data.local.entity.AiProfileEntity
import com.wenshu.writing.data.local.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM ai_config WHERE id = 0")
    fun observeAiConfig(): Flow<AiConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAiConfig(entity: AiConfigEntity)

    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun observeAppSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 0")
    suspend fun getAppSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppSettings(entity: AppSettingsEntity)

    @Query("SELECT * FROM ai_profiles ORDER BY updatedAt DESC, id DESC")
    fun observeAiProfiles(): Flow<List<AiProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiProfile(entity: AiProfileEntity): Long

    @Query("DELETE FROM ai_profiles WHERE id = :profileId")
    suspend fun deleteAiProfile(profileId: Long)
}
