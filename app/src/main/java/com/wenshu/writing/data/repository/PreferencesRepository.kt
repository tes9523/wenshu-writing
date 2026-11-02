package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.SettingsDao
import com.wenshu.writing.data.local.entity.AppSettingsEntity
import com.wenshu.writing.data.local.entity.toEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.AiProfile
import com.wenshu.writing.domain.model.AppSettings
import com.wenshu.writing.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepository(
    private val settingsDao: SettingsDao
) {

    val settings: Flow<AppSettings> = settingsDao.observeAppSettings().map { entity ->
        AppSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(entity?.themeMode ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            activeAiProfileId = entity?.activeAiProfileId ?: -1L,
            activeNovelId = entity?.activeNovelId ?: -1L
        )
    }

    val aiConfig: Flow<AiConfig> = settingsDao.observeAiConfig().map { entity ->
        entity?.toModel() ?: AiConfig()
    }

    val aiProfiles: Flow<List<AiProfile>> = settingsDao.observeAiProfiles().map { entities ->
        entities.map { it.toModel() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        val current = settingsDao.getAppSettings()
        settingsDao.saveAppSettings(
            AppSettingsEntity(
                themeMode = mode.name,
                activeAiProfileId = current?.activeAiProfileId ?: -1L,
                activeNovelId = current?.activeNovelId ?: -1L
            )
        )
    }

    /** 切换当前激活书稿；-1 表示回落到全局公共素材。 */
    suspend fun setActiveNovel(novelId: Long) {
        val current = settingsDao.getAppSettings()
        settingsDao.saveAppSettings(
            AppSettingsEntity(
                themeMode = current?.themeMode ?: ThemeMode.SYSTEM.name,
                activeAiProfileId = current?.activeAiProfileId ?: -1L,
                activeNovelId = novelId
            )
        )
    }

    suspend fun saveAiConfig(config: AiConfig) {
        settingsDao.saveAiConfig(config.toEntity())
    }

    /** 新增或更新一套配置，返回其主键。 */
    suspend fun saveAiProfile(profile: AiProfile): Long {
        val id = settingsDao.upsertAiProfile(profile.toEntity())
        return if (profile.id == 0L) id else profile.id
    }

    suspend fun deleteAiProfile(profileId: Long) {
        settingsDao.deleteAiProfile(profileId)
    }

    /** 记录当前启用的配置集；传入 -1 表示没有启用任何配置。 */
    suspend fun setActiveAiProfile(profileId: Long) {
        val current = settingsDao.getAppSettings()
        settingsDao.saveAppSettings(
            AppSettingsEntity(
                themeMode = current?.themeMode ?: ThemeMode.SYSTEM.name,
                activeAiProfileId = profileId,
                activeNovelId = current?.activeNovelId ?: -1L
            )
        )
    }
}
