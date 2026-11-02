package com.wenshu.writing.di

import android.content.Context
import com.wenshu.writing.data.local.AppDatabase
import com.wenshu.writing.data.remote.AiClient
import com.wenshu.writing.data.repository.LibraryRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.PreferencesRepository

/**
 * 轻量依赖容器。后续接入 Hilt 或拆分模块时，只需替换此处的装配逻辑。
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.getInstance(context)

    val novelRepository = NovelRepository(
        novelDao = database.novelDao(),
        chapterDao = database.chapterDao()
    )

    val libraryRepository = LibraryRepository(
        libraryDao = database.libraryDao()
    )

    val preferencesRepository = PreferencesRepository(
        settingsDao = database.settingsDao()
    )

    val aiClient = AiClient()
}
