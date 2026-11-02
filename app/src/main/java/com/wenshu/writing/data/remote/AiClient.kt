package com.wenshu.writing.data.remote

import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.ApiConnStatus
import com.wenshu.writing.domain.model.ApiProbeOutcome
import com.wenshu.writing.domain.model.ApiProviderType
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

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
        explicitNulls = false
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

            val chatMessages = history.filterNot { it.isError }.map { message ->
                MessageDto(
                    role = if (message.role == ChatRole.USER) "user" else "assistant",
                    content = message.content
                )
            }
            val isAnthropic = config.apiType == ApiProviderType.ANTHROPIC

            val request = if (isAnthropic) {
                val payload = AnthropicRequestDto(
                    model = config.model,
                    maxTokens = 4096,
                    system = systemPrompt.takeIf { it.isNotBlank() },
                    messages = chatMessages
                )
                Request.Builder()
                    .url(chatEndpointOf(config))
                    .header("x-api-key", config.apiKey.trim())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("Content-Type", "application/json")
                    .post(
                        json.encodeToString(AnthropicRequestDto.serializer(), payload)
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build()
            } else {
                val messages = buildList {
                    if (systemPrompt.isNotBlank()) {
                        add(MessageDto(role = "system", content = systemPrompt))
                    }
                    addAll(chatMessages)
                }
                val payload = ChatRequestDto(model = config.model, messages = messages)
                Request.Builder()
                    .url(chatEndpointOf(config))
                    .header("Authorization", "Bearer ${config.apiKey.trim()}")
                    .header("Content-Type", "application/json")
                    .post(
                        json.encodeToString(ChatRequestDto.serializer(), payload)
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build()
            }

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("请求失败（${response.code}）：${extractErrorMessage(rawBody)}")
                }
                val content = if (isAnthropic) {
                    json.decodeFromString(AnthropicResponseDto.serializer(), rawBody)
                        .content.joinToString("") { it.text.orEmpty() }
                } else {
                    json.decodeFromString(ChatResponseDto.serializer(), rawBody)
                        .choices.firstOrNull()?.message?.content
                }
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

    /**
     * 探测连通性并拉取可用模型列表（GET {baseUrl}/models，OpenAI 兼容规范）。
     * 会区分超时、鉴权失败、地址错误与网络错误，供 UI 展示彩色状态提示。
     */
    suspend fun probe(config: AiConfig): ApiProbeOutcome = withContext(Dispatchers.IO) {
        if (config.baseUrl.isBlank()) {
            return@withContext ApiProbeOutcome(ApiConnStatus.UNVERIFIED, "请先填写 API 地址")
        }
        if (config.apiKey.isBlank()) {
            return@withContext ApiProbeOutcome(ApiConnStatus.UNVERIFIED, "请先填写 API Key")
        }

        val builder = Request.Builder()
            .url(modelsEndpointOf(config))
            .header("Accept", "application/json")
        if (config.apiType == ApiProviderType.ANTHROPIC) {
            builder.header("x-api-key", config.apiKey.trim())
            builder.header("anthropic-version", ANTHROPIC_VERSION)
        } else {
            builder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        }
        val request = builder.get().build()

        try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val models = parseModelIds(rawBody)
                        if (models.isEmpty()) {
                            ApiProbeOutcome(
                                status = ApiConnStatus.SUCCESS,
                                message = "连接正常，但该服务未返回模型列表",
                                models = emptyList()
                            )
                        } else {
                            ApiProbeOutcome(
                                status = ApiConnStatus.SUCCESS,
                                message = "连接成功，已获取 ${models.size} 个可用模型",
                                models = models
                            )
                        }
                    }
                    response.code == 401 -> ApiProbeOutcome(
                        ApiConnStatus.FAILURE,
                        "密钥无效或未授权（401），请检查 API Key"
                    )
                    response.code == 403 -> ApiProbeOutcome(
                        ApiConnStatus.FAILURE,
                        "权限不足（403），当前 Key 无权访问该接口"
                    )
                    response.code == 404 -> ApiProbeOutcome(
                        ApiConnStatus.FAILURE,
                        "接口地址错误（404），请确认 Base URL 形如 …/v1"
                    )
                    else -> ApiProbeOutcome(
                        ApiConnStatus.FAILURE,
                        "请求失败（${response.code}）：${extractErrorMessage(rawBody)}"
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            ApiProbeOutcome(ApiConnStatus.TIMEOUT, "请求超时，请检查网络或服务地址")
        } catch (e: UnknownHostException) {
            ApiProbeOutcome(ApiConnStatus.FAILURE, "网络不通，无法解析服务域名")
        } catch (e: ConnectException) {
            ApiProbeOutcome(ApiConnStatus.FAILURE, "网络不通，无法连接服务器")
        } catch (e: SSLException) {
            ApiProbeOutcome(ApiConnStatus.FAILURE, "安全连接失败，证书校验未通过")
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ApiProbeOutcome(ApiConnStatus.FAILURE, "网络异常：${e.message ?: "请检查网络连接"}")
        } catch (e: Exception) {
            ApiProbeOutcome(ApiConnStatus.FAILURE, e.message ?: "连接失败，请检查配置")
        }
    }

    /** 去掉用户可能已经填写的具体接口后缀，得到可拼接的 API 根地址。 */
    private fun rootOf(config: AiConfig): String {
        val trimmed = config.baseUrl.trim().trimEnd('/')
            .removeSuffix("/chat/completions")
            .removeSuffix("/messages")
            .removeSuffix("/models")
        return when (config.apiType) {
            ApiProviderType.OPENAI -> trimmed
            ApiProviderType.ANTHROPIC -> {
                val versionSegment = trimmed.substringAfterLast('/')
                if (versionSegment.matches(Regex("v\\d+"))) trimmed else "$trimmed/v1"
            }
        }
    }

    private fun chatEndpointOf(config: AiConfig): String = when (config.apiType) {
        ApiProviderType.OPENAI -> "${rootOf(config)}/chat/completions"
        ApiProviderType.ANTHROPIC -> "${rootOf(config)}/messages"
    }

    private fun modelsEndpointOf(config: AiConfig): String = "${rootOf(config)}/models"

    private fun parseModelIds(rawBody: String): List<String> = try {
        val root = json.parseToJsonElement(rawBody).jsonObject
        val array = (root["data"] ?: root["models"])?.jsonArray
        array?.mapNotNull { item ->
            item.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                ?: item.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        }?.distinct()?.sorted() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
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
        private const val ANTHROPIC_VERSION = "2023-06-01"

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
private data class AnthropicRequestDto(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    val system: String? = null,
    val messages: List<MessageDto>
)

@Serializable
private data class AnthropicResponseDto(
    val content: List<AnthropicContentDto> = emptyList()
)

@Serializable
private data class AnthropicContentDto(
    val type: String = "",
    val text: String? = null
)

@Serializable
private data class ApiErrorEnvelopeDto(
    val error: ApiErrorDto? = null
)

@Serializable
private data class ApiErrorDto(
    val message: String? = null
)
