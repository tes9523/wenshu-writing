package com.wenshu.writing.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wenshu.writing.data.local.dao.ChapterDao
import com.wenshu.writing.data.local.dao.ChatDao
import com.wenshu.writing.data.local.dao.SettingDao
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
import com.wenshu.writing.data.local.entity.ChatMessageEntity
import com.wenshu.writing.data.local.entity.ChatSessionEntity
import com.wenshu.writing.data.local.entity.MemoNoteEntity
import com.wenshu.writing.data.local.entity.NovelEntity
import com.wenshu.writing.data.local.entity.PipelineRunEntity
import com.wenshu.writing.data.local.entity.SettingEntity
import com.wenshu.writing.data.local.entity.SkillEntity

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        SettingEntity::class,
        AiConfigEntity::class,
        AiProfileEntity::class,
        AppSettingsEntity::class,
        MemoNoteEntity::class,
        SkillEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ChapterTaskEntity::class,
        PipelineRunEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun settingDao(): SettingDao
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

        /**
         * 1.0.5 -> 1.0.6：设定库统一为 `settings` 表（人物 / 世界观 / 大纲 / 自定义分类）。
         * 历史 characters、world_settings、outlines 数据无损迁入新表后删除旧表。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `settings` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookId` INTEGER, " +
                        "`category` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`subtitle` TEXT, " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settings_bookId` ON `settings` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settings_category` ON `settings` (`category`)")

                db.execSQL(
                    "INSERT INTO `settings` (`bookId`, `category`, `title`, `subtitle`, `content`, `createdAt`, `updatedAt`) " +
                        "SELECT `bookId`, '人物', `name`, `role`, `description`, `createdAt`, `updatedAt` FROM `characters`"
                )
                db.execSQL(
                    "INSERT INTO `settings` (`bookId`, `category`, `title`, `subtitle`, `content`, `createdAt`, `updatedAt`) " +
                        "SELECT `bookId`, '世界观', " +
                        "CASE WHEN `category` IS NOT NULL AND `category` <> '' " +
                        "THEN '[' || `category` || '] ' || `title` ELSE `title` END, " +
                        "NULL, `content`, `createdAt`, `updatedAt` FROM `world_settings`"
                )
                db.execSQL(
                    "INSERT INTO `settings` (`bookId`, `category`, `title`, `subtitle`, `content`, `createdAt`, `updatedAt`) " +
                        "SELECT `novelId`, '大纲', `title`, NULL, `content`, `createdAt`, `updatedAt` FROM `outlines`"
                )

                db.execSQL("DROP TABLE IF EXISTS `characters`")
                db.execSQL("DROP TABLE IF EXISTS `world_settings`")
                db.execSQL("DROP TABLE IF EXISTS `outlines`")
            }
        }

        /** 1.0.6 -> 1.0.9：书稿新增自定义封面路径，既有书稿不受影响（默认封面）。 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `novels` ADD COLUMN `coverPath` TEXT")
            }
        }

        /** 1.0.9 -> 1.1.0：新增自定义外观（主题色/背景色/背景图/提示音/字体/聊天字号）配置项。 */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `themeColorName` TEXT NOT NULL DEFAULT 'INK'")
                db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `backgroundColorName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `backgroundImagePath` TEXT")
                db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `soundEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `soundType` TEXT NOT NULL DEFAULT 'notification'")
                db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `fontName` TEXT NOT NULL DEFAULT 'system'")
                db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `chatFontSize` INTEGER NOT NULL DEFAULT 14")
            }
        }

        /** 1.1.3 -> 1.1.4：书稿回收站，deletedAt 非空表示已移入回收站。 */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `novels` ADD COLUMN `deletedAt` INTEGER")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        /** 1.1.6 -> 1.1.7：技能工具调用轮次上限可配置，默认 10。 */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `app_settings` ADD COLUMN `maxAgentTurns` " +
                        "INTEGER NOT NULL DEFAULT 10"
                )
            }
        }

        /**
         * 1.1.7 -> 1.1.8：技能工具调用轮次默认值调整为 15，仅升级仍为默认值 10 的用户。
         * SQLite 不支持修改列默认值，需重建 app_settings 表让 schema 与新实体一致，
         * 否则 Room 校验失败会在启动时崩溃。
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_settings_new` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`themeMode` TEXT NOT NULL, " +
                        "`activeAiProfileId` INTEGER NOT NULL DEFAULT -1, " +
                        "`activeNovelId` INTEGER NOT NULL DEFAULT -1, " +
                        "`themeColorName` TEXT NOT NULL DEFAULT 'INK', " +
                        "`backgroundColorName` TEXT NOT NULL DEFAULT '', " +
                        "`backgroundImagePath` TEXT, " +
                        "`soundEnabled` INTEGER NOT NULL DEFAULT 1, " +
                        "`soundType` TEXT NOT NULL DEFAULT 'notification', " +
                        "`fontName` TEXT NOT NULL DEFAULT 'system', " +
                        "`chatFontSize` INTEGER NOT NULL DEFAULT 14, " +
                        "`maxAgentTurns` INTEGER NOT NULL DEFAULT 15, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `app_settings_new` (`id`, `themeMode`, `activeAiProfileId`, " +
                        "`activeNovelId`, `themeColorName`, `backgroundColorName`, " +
                        "`backgroundImagePath`, `soundEnabled`, `soundType`, `fontName`, " +
                        "`chatFontSize`, `maxAgentTurns`) " +
                        "SELECT `id`, `themeMode`, `activeAiProfileId`, `activeNovelId`, " +
                        "`themeColorName`, `backgroundColorName`, `backgroundImagePath`, " +
                        "`soundEnabled`, `soundType`, `fontName`, `chatFontSize`, `maxAgentTurns` " +
                        "FROM `app_settings`"
                )
                db.execSQL("DROP TABLE `app_settings`")
                db.execSQL("ALTER TABLE `app_settings_new` RENAME TO `app_settings`")
                db.execSQL(
                    "UPDATE `app_settings` SET `maxAgentTurns` = 15 WHERE `maxAgentTurns` = 10"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
