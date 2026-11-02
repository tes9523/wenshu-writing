package com.wenshu.writing.data.local.entity

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

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val role: String = "",
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "world_settings")
data class WorldSettingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String = "",
    val title: String,
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

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val themeMode: String = "SYSTEM"
)
