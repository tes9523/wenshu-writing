package com.wenshu.writing.data.local.entity

import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.AiProfile
import com.wenshu.writing.domain.model.CharacterCard
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.WorldSetting

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
    updatedAt = updatedAt
)

fun AiProfile.toEntity() = AiProfileEntity(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    updatedAt = updatedAt
)
