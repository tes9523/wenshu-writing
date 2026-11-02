package com.wenshu.writing.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("novelId")]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val novelId: Long,
    val title: String,
    val content: String = "",
    val orderIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "settings", indices = [Index("bookId"), Index("category")])
data class SettingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long? = null,
    val category: String,
    val title: String,
    val subtitle: String? = null,
    val content: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "ai_config")
data class AiConfigEntity(
    @PrimaryKey val id: Int = 0,
    val baseUrl: String,
    val apiKey: String,
    val model: String
)

@Entity(tableName = "ai_profiles")
data class AiProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    @ColumnInfo(defaultValue = "OPENAI") val apiType: String = "OPENAI",
    @ColumnInfo(defaultValue = "") val models: String = "",
    val updatedAt: Long = 0L
)

/** 灵感备忘录。bookId 为 null 表示全局公共备忘录。 */
@Entity(tableName = "memo_notes", indices = [Index("bookId")])
data class MemoNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String = "",
    val bookId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/** 写作技能，referencesJson 以 JSON 数组保存参考素材列表。 */
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val mainMarkdown: String = "",
    val referencesJson: String = "",
    @ColumnInfo(defaultValue = "0") val isBuiltIn: Boolean = false,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isPipeline: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val themeMode: String = "SYSTEM",
    @ColumnInfo(defaultValue = "-1") val activeAiProfileId: Long = -1L,
    @ColumnInfo(defaultValue = "-1") val activeNovelId: Long = -1L
)

/** AI 对话会话。bookId 为 null 表示全局通用会话。 */
@Entity(tableName = "chat_sessions", indices = [Index("bookId")])
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val bookId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "chat_messages", indices = [Index("sessionId")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val role: String,
    val content: String,
    val isError: Boolean = false,
    val createdAt: Long
)

/** 章节任务卡。 */
@Entity(tableName = "chapter_tasks", indices = [Index("novelId")])
data class ChapterTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val novelId: Long,
    val orderIndex: Int = 0,
    val title: String,
    val goal: String = "",
    val conflict: String = "",
    val infoIncrement: String = "",
    val payoff: String = "",
    val hook: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

/** 「开启新书」流水线断点。 */
@Entity(tableName = "pipeline_runs", indices = [Index("novelId")])
data class PipelineRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val novelId: Long,
    val skillId: Long = 0L,
    val stage: Int = 1,
    val status: String = "PAUSED",
    val inputJson: String = "",
    val outputJson: String = "",
    val createdAt: Long,
    val updatedAt: Long
)
