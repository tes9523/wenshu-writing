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
            activeNovelId = entity?.activeNovelId ?: -1L,
            themeColorName = entity?.themeColorName ?: "INK",
            backgroundColorName = entity?.backgroundColorName ?: "",
            backgroundImagePath = entity?.backgroundImagePath,
            soundEnabled = entity?.soundEnabled ?: true,
            soundType = entity?.soundType ?: "notification",
            fontName = entity?.fontName ?: "system",
            chatFontSize = entity?.chatFontSize ?: 14
        )
    }

    val aiConfig: Flow<AiConfig> = settingsDao.observeAiConfig().map { entity ->
        entity?.toModel() ?: AiConfig()
    }

    val aiProfiles: Flow<List<AiProfile>> = settingsDao.observeAiProfiles().map { entities ->
        entities.map { it.toModel() }
    }

    /** 读取-修改-写回，保证各设置项互不覆盖。 */
    private suspend fun update(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        val current = settingsDao.getAppSettings() ?: AppSettingsEntity()
        settingsDao.saveAppSettings(transform(current))
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        update { it.copy(themeMode = mode.name) }
    }

    suspend fun setThemeColor(name: String) {
        update { it.copy(themeColorName = name) }
    }

    /** 空串表示背景色跟随主题。 */
    suspend fun setBackgroundColor(name: String) {
        update { it.copy(backgroundColorName = name) }
    }

    suspend fun setBackgroundImage(path: String?) {
        update { it.copy(backgroundImagePath = path) }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        update { it.copy(soundEnabled = enabled) }
    }

    suspend fun setSoundType(type: String) {
        update { it.copy(soundType = type) }
    }

    suspend fun setFontName(name: String) {
        update { it.copy(fontName = name) }
    }

    suspend fun setChatFontSize(size: Int) {
        update { it.copy(chatFontSize = size.coerceIn(10, 22)) }
    }

    /** 切换当前激活书稿；-1 表示回落到全局公共素材。 */
    suspend fun setActiveNovel(novelId: Long) {
        update { it.copy(activeNovelId = novelId) }
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
        update { it.copy(activeAiProfileId = profileId) }
    }
}
