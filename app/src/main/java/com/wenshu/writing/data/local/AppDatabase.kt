package com.wenshu.writing.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wenshu.writing.data.local.dao.ChapterDao
import com.wenshu.writing.data.local.dao.LibraryDao
import com.wenshu.writing.data.local.dao.NovelDao
import com.wenshu.writing.data.local.dao.SettingsDao
import com.wenshu.writing.data.local.entity.AiConfigEntity
import com.wenshu.writing.data.local.entity.AppSettingsEntity
import com.wenshu.writing.data.local.entity.ChapterEntity
import com.wenshu.writing.data.local.entity.CharacterEntity
import com.wenshu.writing.data.local.entity.NovelEntity
import com.wenshu.writing.data.local.entity.WorldSettingEntity

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        CharacterEntity::class,
        WorldSettingEntity::class,
        AiConfigEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun libraryDao(): LibraryDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        private const val DATABASE_NAME = "wenshu_writing.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
    }
}
