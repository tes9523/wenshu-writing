package com.wenshu.writing.data.remote

import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容的 Chat Completions 客户端。
 * 仅负责网络请求与响应解析，便于后续替换为流式输出或多模型适配。
 */
class AiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {

    suspend fun chat(
        config: AiConfig,
        history: List<ChatMessage>,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            require(config.baseUrl.isNotBlank()) { "请先在 API 配置中填写 API 地址" }
            require(config.apiKey.isNotBlank()) { "请先在 API 配置中填写 API Key" }
            require(config.model.isNotBlank()) { "请先选择模型" }

            val messages = buildList {
                if (systemPrompt.isNotBlank()) {
                    add(MessageDto(role = "system", content = systemPrompt))
                }
                history.filterNot { it.isError }.forEach { message ->
                    add(
                        MessageDto(
                            role = if (message.role == ChatRole.USER) "user" else "assistant",
                            content = message.content
                        )
                    )
                }
            }

            val payload = ChatRequestDto(model = config.model, messages = messages)
            val request = Request.Builder()
                .url(endpointOf(config.baseUrl))
                .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(json.encodeToString(ChatRequestDto.serializer(), payload).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("请求失败（${response.code}）：${extractErrorMessage(rawBody)}")
                }
                val parsed = json.decodeFromString(ChatResponseDto.serializer(), rawBody)
                val content = parsed.choices.firstOrNull()?.message?.content
                if (content.isNullOrBlank()) {
                    throw IOException("模型未返回有效内容")
                }
                Result.success(content.trim())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun endpointOf(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun extractErrorMessage(rawBody: String): String {
        return try {
            json.decodeFromString(ApiErrorEnvelopeDto.serializer(), rawBody).error?.message
                ?: rawBody.take(200)
        } catch (e: Exception) {
            rawBody.take(200)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val DEFAULT_SYSTEM_PROMPT =
            "你是一位专业的中文小说写作助手。请根据用户的要求直接输出可以用于小说正文的内容，" +
                "语言自然、有画面感，避免解释创作过程，也不要输出多余的说明。"
    }
}

@Serializable
private data class ChatRequestDto(
    val model: String,
    val messages: List<MessageDto>,
    val stream: Boolean = false,
    val temperature: Double = 0.8
)

@Serializable
private data class MessageDto(
    val role: String,
    val content: String
)

@Serializable
private data class ChatResponseDto(
    val choices: List<ChoiceDto> = emptyList()
)

@Serializable
private data class ChoiceDto(
    val message: MessageDto? = null
)

@Serializable
private data class ApiErrorEnvelopeDto(
    val error: ApiErrorDto? = null
)

@Serializable
private data class ApiErrorDto(
    val message: String? = null
)
