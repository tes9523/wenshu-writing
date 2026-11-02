package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.SettingsDao
import com.wenshu.writing.data.local.entity.AppSettingsEntity
import com.wenshu.writing.data.local.entity.toEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.AiConfig
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
            }.getOrDefault(ThemeMode.SYSTEM)
        )
    }

    val aiConfig: Flow<AiConfig> = settingsDao.observeAiConfig().map { entity ->
        entity?.toModel() ?: AiConfig()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        settingsDao.saveAppSettings(AppSettingsEntity(themeMode = mode.name))
    }

    suspend fun saveAiConfig(config: AiConfig) {
        settingsDao.saveAiConfig(config.toEntity())
    }
}
