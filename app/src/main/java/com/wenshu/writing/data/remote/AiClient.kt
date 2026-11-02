package com.wenshu.writing.data.remote

import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.ApiConnStatus
import com.wenshu.writing.domain.model.ApiProbeOutcome
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val request = Request.Builder()
            .url(modelsEndpointOf(config.baseUrl))
            .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
            .addHeader("Accept", "application/json")
            .get()
            .build()

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

    private fun endpointOf(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun modelsEndpointOf(baseUrl: String): String {
        var trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.endsWith("/chat/completions")) {
            trimmed = trimmed.removeSuffix("/chat/completions")
        }
        return if (trimmed.endsWith("/models")) trimmed else "$trimmed/models"
    }

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
