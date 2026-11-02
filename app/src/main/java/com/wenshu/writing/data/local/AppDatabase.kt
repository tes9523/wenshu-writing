package com.wenshu.writing.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wenshu.writing.data.local.dao.ChapterDao
import com.wenshu.writing.data.local.dao.ChatDao
import com.wenshu.writing.data.local.dao.LibraryDao
import com.wenshu.writing.data.local.dao.PipelineDao
import com.wenshu.writing.data.local.dao.MemoDao
import com.wenshu.writing.data.local.dao.NovelDao
import com.wenshu.writing.data.local.dao.SettingsDao
import com.wenshu.writing.data.local.dao.SkillDao
import com.wenshu.writing.data.local.entity.AiConfigEntity
import com.wenshu.writing.data.local.entity.AiProfileEntity
import com.wenshu.writing.data.local.entity.AppSettingsEntity
import com.wenshu.writing.data.local.entity.ChapterEntity
import com.wenshu.writing.data.local.entity.ChapterTaskEntity
import com.wenshu.writing.data.local.entity.CharacterEntity
import com.wenshu.writing.data.local.entity.ChatMessageEntity
import com.wenshu.writing.data.local.entity.ChatSessionEntity
import com.wenshu.writing.data.local.entity.MemoNoteEntity
import com.wenshu.writing.data.local.entity.NovelEntity
import com.wenshu.writing.data.local.entity.OutlineEntity
import com.wenshu.writing.data.local.entity.PipelineRunEntity
import com.wenshu.writing.data.local.entity.SkillEntity
import com.wenshu.writing.data.local.entity.WorldSettingEntity

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        CharacterEntity::class,
        WorldSettingEntity::class,
        AiConfigEntity::class,
        AiProfileEntity::class,
        AppSettingsEntity::class,
        MemoNoteEntity::class,
        SkillEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        OutlineEntity::class,
        ChapterTaskEntity::class,
        PipelineRunEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun libraryDao(): LibraryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun memoDao(): MemoDao
    abstract fun skillDao(): SkillDao
    abstract fun chatDao(): ChatDao
    abstract fun pipelineDao(): PipelineDao

    companion object {
        private const val DATABASE_NAME = "wenshu_writing.db"

        /** 1.0.0 -> 1.0.1：新增多套 API 配置表，历史单条配置自动迁移为「默认配置」。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_profiles` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`baseUrl` TEXT NOT NULL, " +
                        "`apiKey` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "ALTER TABLE `app_settings` ADD COLUMN `activeAiProfileId` " +
                        "INTEGER NOT NULL DEFAULT -1"
                )
                db.execSQL(
                    "INSERT INTO `ai_profiles` (`name`, `baseUrl`, `apiKey`, `model`, `updatedAt`) " +
                        "SELECT '默认配置', `baseUrl`, `apiKey`, `model`, 0 FROM `ai_config` " +
                        "WHERE `id` = 0 AND `apiKey` <> ''"
                )
                db.execSQL(
                    "UPDATE `app_settings` SET `activeAiProfileId` = " +
                        "(SELECT `id` FROM `ai_profiles` ORDER BY `id` LIMIT 1) " +
                        "WHERE `id` = 0 AND `activeAiProfileId` = -1"
                )
            }
        }

        /** 1.0.1 -> 1.0.2：提供商新增接口类型与独立模型缓存，不影响书稿与设定库数据。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `ai_profiles` ADD COLUMN `apiType` " +
                        "TEXT NOT NULL DEFAULT 'OPENAI'"
                )
                db.execSQL(
                    "ALTER TABLE `ai_profiles` ADD COLUMN `models` " +
                        "TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** 1.0.2 -> 1.0.3：新增备忘录与写作技能表，不影响既有书稿、设定库与 AI 配置。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memo_notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skills` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`mainMarkdown` TEXT NOT NULL, " +
                        "`referencesJson` TEXT NOT NULL, " +
                        "`isBuiltIn` INTEGER NOT NULL DEFAULT 0, " +
                        "`enabled` INTEGER NOT NULL DEFAULT 1, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * 1.0.3 -> 1.0.4：书稿数据隔离（bookId）、AI 多会话、大纲/任务卡与流水线断点。
         * 既有数据保留，历史人物卡/世界观/备忘录归入全局公共素材（bookId = NULL）。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `characters` ADD COLUMN `bookId` INTEGER")
                db.execSQL("ALTER TABLE `world_settings` ADD COLUMN `bookId` INTEGER")
                db.execSQL("ALTER TABLE `memo_notes` ADD COLUMN `bookId` INTEGER")
                db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `activeNovelId` INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE `skills` ADD COLUMN `isPipeline` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_characters_bookId` ON `characters` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_settings_bookId` ON `world_settings` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memo_notes_bookId` ON `memo_notes` (`bookId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`bookId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_sessions_bookId` ON `chat_sessions` (`bookId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`isError` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outlines` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`novelId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outlines_novelId` ON `outlines` (`novelId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapter_tasks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`novelId` INTEGER NOT NULL, " +
                        "`orderIndex` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`goal` TEXT NOT NULL, " +
                        "`conflict` TEXT NOT NULL, " +
                        "`infoIncrement` TEXT NOT NULL, " +
                        "`payoff` TEXT NOT NULL, " +
                        "`hook` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_tasks_novelId` ON `chapter_tasks` (`novelId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pipeline_runs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`novelId` INTEGER NOT NULL, " +
                        "`skillId` INTEGER NOT NULL, " +
                        "`stage` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`inputJson` TEXT NOT NULL, " +
                        "`outputJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_runs_novelId` ON `pipeline_runs` (`novelId`)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
