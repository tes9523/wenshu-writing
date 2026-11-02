package com.wenshu.writing.data.local.entity

import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.AiProfile
import com.wenshu.writing.domain.model.ApiProviderType
import com.wenshu.writing.domain.model.CharacterCard
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.MemoNote
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.domain.model.SkillReference
import com.wenshu.writing.domain.model.WorldSetting
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun NovelEntity.toModel() = Novel(
    id = id,
    title = title,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ChapterEntity.toModel() = Chapter(
    id = id,
    novelId = novelId,
    title = title,
    content = content,
    orderIndex = orderIndex,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CharacterEntity.toModel() = CharacterCard(
    id = id,
    name = name,
    role = role,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun WorldSettingEntity.toModel() = WorldSetting(
    id = id,
    category = category,
    title = title,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun AiConfigEntity.toModel() = AiConfig(
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model
)

fun AiConfig.toEntity() = AiConfigEntity(
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model
)

fun AiProfileEntity.toModel() = AiProfile(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    apiType = runCatching { ApiProviderType.valueOf(apiType) }.getOrDefault(ApiProviderType.OPENAI),
    models = decodeModels(models),
    updatedAt = updatedAt
)

fun AiProfile.toEntity() = AiProfileEntity(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    apiType = apiType.name,
    models = encodeModels(models),
    updatedAt = updatedAt
)

fun MemoNoteEntity.toModel() = MemoNote(
    id = id,
    title = title,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MemoNote.toEntity() = MemoNoteEntity(
    id = id,
    title = title,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt
)

@Serializable
private data class SkillReferenceDto(val name: String, val content: String)

private val skillJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun SkillEntity.toModel() = Skill(
    id = id,
    name = name,
    description = description,
    mainMarkdown = mainMarkdown,
    references = decodeReferences(referencesJson),
    isBuiltIn = isBuiltIn,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Skill.toEntity() = SkillEntity(
    id = id,
    name = name,
    description = description,
    mainMarkdown = mainMarkdown,
    referencesJson = encodeReferences(references),
    isBuiltIn = isBuiltIn,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun decodeReferences(raw: String): List<SkillReference> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        skillJson.decodeFromString(
            ListSerializer(SkillReferenceDto.serializer()),
            raw
        ).map { SkillReference(it.name, it.content) }
    }.getOrDefault(emptyList())
}

private fun encodeReferences(references: List<SkillReference>): String {
    if (references.isEmpty()) return ""
    return skillJson.encodeToString(
        ListSerializer(SkillReferenceDto.serializer()),
        references.map { SkillReferenceDto(it.name, it.content) }
    )
}

/** 模型缓存以换行分隔存储，避免额外的 JSON 依赖与转义问题。 */
private fun decodeModels(raw: String): List<String> =
    raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

private fun encodeModels(models: List<String>): String =
    models.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
