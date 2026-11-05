package com.wenshu.writing.data.local.entity

import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.AiProfile
import com.wenshu.writing.domain.model.ApiProviderType
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.ChapterTask
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.ChatSession
import com.wenshu.writing.domain.model.MemoNote
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.PipelineRun
import com.wenshu.writing.domain.model.PipelineStatus
import com.wenshu.writing.domain.model.Setting
import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.domain.model.SkillReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun NovelEntity.toModel() = Novel(
    id = id,
    title = title,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    coverPath = coverPath,
    deletedAt = deletedAt
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

fun SettingEntity.toModel() = Setting(
    id = id,
    bookId = bookId,
    category = category,
    title = title,
    subtitle = subtitle,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Setting.toEntity() = SettingEntity(
    id = id,
    bookId = bookId,
    category = category,
    title = title,
    subtitle = subtitle?.takeIf { it.isNotBlank() },
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
    bookId = bookId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MemoNote.toEntity() = MemoNoteEntity(
    id = id,
    title = title,
    content = content,
    bookId = bookId,
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
    isPipeline = isPipeline,
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
    isPipeline = isPipeline,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ChatSessionEntity.toModel() = ChatSession(
    id = id,
    title = title,
    bookId = bookId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ChatSession.toEntity() = ChatSessionEntity(
    id = id,
    title = title,
    bookId = bookId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ChatMessageEntity.toModel() = ChatMessage(
    id = id,
    role = runCatching { ChatRole.valueOf(role) }.getOrDefault(ChatRole.ASSISTANT),
    content = content,
    isError = isError,
    createdAt = createdAt
)

fun ChatMessage.toEntity(sessionId: Long) = ChatMessageEntity(
    id = id,
    sessionId = sessionId,
    role = role.name,
    content = content,
    isError = isError,
    createdAt = createdAt
)

fun ChapterTaskEntity.toModel() = ChapterTask(
    id = id,
    novelId = novelId,
    orderIndex = orderIndex,
    title = title,
    goal = goal,
    conflict = conflict,
    infoIncrement = infoIncrement,
    payoff = payoff,
    hook = hook,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ChapterTask.toEntity() = ChapterTaskEntity(
    id = id,
    novelId = novelId,
    orderIndex = orderIndex,
    title = title,
    goal = goal,
    conflict = conflict,
    infoIncrement = infoIncrement,
    payoff = payoff,
    hook = hook,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PipelineRunEntity.toModel() = PipelineRun(
    id = id,
    novelId = novelId,
    skillId = skillId,
    stage = stage,
    status = runCatching { PipelineStatus.valueOf(status) }
        .getOrDefault(PipelineStatus.PAUSED),
    inputJson = inputJson,
    outputJson = outputJson,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PipelineRun.toEntity() = PipelineRunEntity(
    id = id,
    novelId = novelId,
    skillId = skillId,
    stage = stage,
    status = status.name,
    inputJson = inputJson,
    outputJson = outputJson,
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
