package com.wenshu.writing.data.remote

import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.ApiConnStatus
import com.wenshu.writing.domain.model.ApiProbeOutcome
import com.wenshu.writing.domain.model.ApiProviderType
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Agent 循环中的一条模型 tool_call。 */
data class AgentToolCall(
    val id: String,
    val name: String,
    val arguments: String = ""
)

/**
 * 发送给模型的统一消息结构。
 * role = user / assistant / tool；assistant 可携带 toolCalls，tool 通过 toolCallId 关联结果。
 */
data class AgentMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<AgentToolCall> = emptyList(),
    val toolCallId: String? = null
)

/** 提供给模型的本地工具声明（JSON Schema 参数）。 */
data class AgentToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

/** 一轮模型调用的结果：文本回复 + 待执行的工具调用。 */
data class AgentTurnResult(
    val text: String,
    val toolCalls: List<AgentToolCall> = emptyList()
)

/**
 * OpenAI 兼容的 Chat Completions 客户端。
 * 负责网络请求与响应解析，支持普通请求与流式（SSE）tool_call 增量聚合。
 */
class AiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // 连接失败时自动重试，减少偶发握手失败带来的额外等待
        .retryOnConnectionFailure(true)
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
     * 流式对话：逐块回调文本增量，并在流结束后返回聚合的 tool_call。
     * 支持 OpenAI 兼容 SSE 与 Anthropic Messages SSE；协程取消时会立即 cancel 网络请求。
     */
    suspend fun chatStream(
        config: AiConfig,
        messages: List<AgentMessage>,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        tools: List<AgentToolSpec> = emptyList(),
        onReasoningDelta: (String) -> Unit = {},
        onTextDelta: (String) -> Unit = {},
        /** 工具调用参数仍在流式到达时回调（name, 目前已累积的参数 JSON 片段），用于提前渲染提问卡片。 */
        onToolCallDelta: (String, String) -> Unit = { _, _ -> }
    ): Result<AgentTurnResult> = withContext(Dispatchers.IO) {
        require(config.baseUrl.isNotBlank()) { "请先在 API 配置中填写 API 地址" }
        require(config.apiKey.isNotBlank()) { "请先在 API 配置中填写 API Key" }
        require(config.model.isNotBlank()) { "请先选择模型" }

        val isAnthropic = config.apiType == ApiProviderType.ANTHROPIC
        val payload = if (isAnthropic) {
            buildJsonObject {
                put("model", config.model)
                put("max_tokens", 8192)
                put("stream", true)
                if (systemPrompt.isNotBlank()) put("system", systemPrompt)
                putJsonArray("messages") {
                    anthropicMessages(messages).forEach { add(it) }
                }
                if (tools.isNotEmpty()) {
                    putJsonArray("tools") {
                        tools.forEach { tool ->
                            add(
                                buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("input_schema", tool.parameters)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            buildJsonObject {
                put("model", config.model)
                put("stream", true)
                put("temperature", 0.8)
                putJsonArray("messages") {
                    if (systemPrompt.isNotBlank()) {
                        add(buildJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                    }
                    messages.forEach { add(openAiMessage(it)) }
                }
                if (tools.isNotEmpty()) {
                    putJsonArray("tools") {
                        tools.forEach { tool ->
                            add(
                                buildJsonObject {
                                    put("type", "function")
                                    putJsonObject("function") {
                                        put("name", tool.name)
                                        put("description", tool.description)
                                        put("parameters", tool.parameters)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(chatEndpointOf(config))
            .apply {
                if (isAnthropic) {
                    header("x-api-key", config.apiKey.trim())
                    header("anthropic-version", ANTHROPIC_VERSION)
                } else {
                    header("Authorization", "Bearer ${config.apiKey.trim()}")
                }
                header("Content-Type", "application/json")
                header("Accept", "text/event-stream")
            }
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = client.newCall(request)
        // 协程被取消（用户点停止）时立即中断阻塞的 SSE 读取。
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val rawBody = response.body?.string().orEmpty()
                    throw IOException("请求失败（${response.code}）：${extractErrorMessage(rawBody)}")
                }
                val source = response.body?.source() ?: throw IOException("服务未返回响应内容")
                val result = if (isAnthropic) {
                    parseAnthropicStream(source, onReasoningDelta, onTextDelta, onToolCallDelta)
                } else {
                    parseOpenAiStream(source, onReasoningDelta, onTextDelta, onToolCallDelta)
                }
                if (result.text.isBlank() && result.toolCalls.isEmpty()) {
                    throw IOException("模型未返回有效内容")
                }
                Result.success(result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("已停止生成")
            }
            Result.failure(e)
        } finally {
            cancelHandle?.dispose()
        }
    }

    /** OpenAI 兼容 SSE：聚合 delta.content 与 delta.tool_calls 分片；reasoning_content 作为思考流回调。 */
    private fun parseOpenAiStream(
        source: BufferedSource,
        onReasoningDelta: (String) -> Unit,
        onTextDelta: (String) -> Unit,
        onToolCallDelta: (String, String) -> Unit = { _, _ -> }
    ): AgentTurnResult {
        val text = StringBuilder()
        val toolCalls = sortedMapOf<Int, ToolCallAccumulator>()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data.isEmpty()) continue
            if (data == "[DONE]") break
            val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                ?: continue
            obj["error"]?.let { error ->
                val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                throw IOException(message ?: "模型返回错误")
            }
            val choices = runCatching { obj["choices"]?.jsonArray }.getOrNull() ?: continue
            val delta = choices.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: continue
            // 思考流（如 DeepSeek/GLM 的 reasoning_content），仅用于界面展示，不计入正文。
            val reasoningChunk = delta["reasoning_content"]?.jsonPrimitive?.contentOrNull
                ?: delta["reasoning"]?.jsonPrimitive?.contentOrNull
            if (!reasoningChunk.isNullOrEmpty()) {
                onReasoningDelta(reasoningChunk)
            }
            delta["content"]?.jsonPrimitive?.contentOrNull?.let { chunk ->
                if (chunk.isNotEmpty()) {
                    text.append(chunk)
                    onTextDelta(chunk)
                }
            }
            runCatching { delta["tool_calls"]?.jsonArray }.getOrNull()?.forEach { element ->
                val call = element.jsonObject
                val index = call["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val accumulator = toolCalls.getOrPut(index) { ToolCallAccumulator() }
                call["id"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let { accumulator.id = it }
                val function = call["function"]?.jsonObject
                function?.get("name")?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let { accumulator.name = it }
                function?.get("arguments")?.jsonPrimitive?.contentOrNull
                    ?.let {
                        accumulator.arguments.append(it)
                        onToolCallDelta(accumulator.name, accumulator.arguments.toString())
                    }
            }
        }
        val calls = toolCalls.entries
            .filter { it.value.name.isNotBlank() }
            .map { (index, acc) ->
                AgentToolCall(
                    id = acc.id.ifBlank { "call_${index}_${System.currentTimeMillis()}" },
                    name = acc.name,
                    arguments = acc.arguments.toString()
                )
            }
        return AgentTurnResult(text.toString(), calls)
    }

    /** Anthropic Messages SSE：text_delta 与 input_json_delta 分片；thinking_delta 作为思考流回调。 */
    private fun parseAnthropicStream(
        source: BufferedSource,
        onReasoningDelta: (String) -> Unit,
        onTextDelta: (String) -> Unit,
        onToolCallDelta: (String, String) -> Unit = { _, _ -> }
    ): AgentTurnResult {
        val textBlocks = sortedMapOf<Int, StringBuilder>()
        val toolBlocks = sortedMapOf<Int, ToolCallAccumulator>()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data.isEmpty()) continue
            val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                ?: continue
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "content_block_start" -> {
                    val index = obj["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val block = obj["content_block"]?.jsonObject ?: continue
                    val type = block["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (type == "tool_use") {
                        toolBlocks[index] = ToolCallAccumulator(
                            id = block["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            name = block["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        )
                    } else {
                        textBlocks.getOrPut(index) { StringBuilder() }
                    }
                }
                "content_block_delta" -> {
                    val index = obj["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val delta = obj["delta"]?.jsonObject ?: continue
                    when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                        "text_delta" -> {
                            val chunk = delta["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (chunk.isNotEmpty()) {
                                textBlocks.getOrPut(index) { StringBuilder() }.append(chunk)
                                onTextDelta(chunk)
                            }
                        }
                        "thinking_delta" -> {
                            // 思考流：仅界面展示，不计入正文。
                            delta["thinking"]?.jsonPrimitive?.contentOrNull
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { onReasoningDelta(it) }
                        }
                        "input_json_delta" -> {
                            delta["partial_json"]?.jsonPrimitive?.contentOrNull
                                ?.let {
                                    val acc = toolBlocks.getOrPut(index) { ToolCallAccumulator() }
                                    acc.arguments.append(it)
                                    onToolCallDelta(acc.name, acc.arguments.toString())
                                }
                        }
                    }
                }
                "error" -> {
                    val message = obj["error"]?.jsonObject?.get("message")
                        ?.jsonPrimitive?.contentOrNull
                    throw IOException(message ?: "模型返回错误")
                }
                "message_stop" -> break
            }
        }
        val text = textBlocks.values.joinToString("") { it.toString() }
        val calls = toolBlocks.entries
            .filter { it.value.name.isNotBlank() }
            .map { (index, acc) ->
                AgentToolCall(
                    id = acc.id.ifBlank { "call_${index}_${System.currentTimeMillis()}" },
                    name = acc.name,
                    arguments = acc.arguments.toString()
                )
            }
        return AgentTurnResult(text, calls)
    }

    private class ToolCallAccumulator(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    )

    /** OpenAI 消息 JSON：assistant 的 tool_calls 需要原样回传，tool 消息携带 tool_call_id。 */
    private fun openAiMessage(message: AgentMessage): JsonObject = buildJsonObject {
        put("role", message.role)
        when {
            message.role == "assistant" && message.toolCalls.isNotEmpty() -> {
                if (message.content.isNullOrBlank()) put("content", JsonNull)
                else put("content", message.content)
                putJsonArray("tool_calls") {
                    message.toolCalls.forEach { call ->
                        add(buildJsonObject {
                            put("id", call.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", call.name)
                                put("arguments", call.arguments)
                            }
                        })
                    }
                }
            }
            message.role == "tool" -> {
                put("tool_call_id", message.toolCallId ?: "")
                put("content", message.content.orEmpty())
            }
            else -> put("content", message.content.orEmpty())
        }
    }

    /**
     * Anthropic 消息数组：tool_result 必须合并进上一条 user 消息，
     * 否则连续多个工具结果会违反 user/assistant 交替规则。
     */
    private fun anthropicMessages(messages: List<AgentMessage>): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        messages.forEach { message ->
            when (message.role) {
                "tool" -> {
                    val block = buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", message.toolCallId ?: "")
                        put("content", message.content.orEmpty())
                    }
                    val last = result.lastOrNull()
                    if (last != null &&
                        last["role"]?.jsonPrimitive?.contentOrNull == "user" &&
                        last["_toolResults"]?.jsonPrimitive?.contentOrNull == "1"
                    ) {
                        val existing = last["content"]?.jsonArray.orEmpty().toMutableList()
                        existing.add(block)
                        result[result.lastIndex] = buildJsonObject {
                            put("role", "user")
                            put("_toolResults", "1")
                            putJsonArray("content") { existing.forEach { add(it) } }
                        }
                    } else {
                        result.add(
                            buildJsonObject {
                                put("role", "user")
                                put("_toolResults", "1")
                                putJsonArray("content") { add(block) }
                            }
                        )
                    }
                }
                "assistant" -> {
                    if (message.toolCalls.isEmpty()) {
                        result.add(
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", message.content.orEmpty())
                            }
                        )
                    } else {
                        result.add(
                            buildJsonObject {
                                put("role", "assistant")
                                putJsonArray("content") {
                                    if (!message.content.isNullOrBlank()) {
                                        add(buildJsonObject {
                                            put("type", "text")
                                            put("text", message.content)
                                        })
                                    }
                                    message.toolCalls.forEach { call ->
                                        add(buildJsonObject {
                                            put("type", "tool_use")
                                            put("id", call.id)
                                            put("name", call.name)
                                            put(
                                                "input",
                                                runCatching {
                                                    json.parseToJsonElement(
                                                        call.arguments.ifBlank { "{}" }
                                                    )
                                                }.getOrNull() ?: JsonObject(emptyMap<String, JsonElement>())
                                            )
                                        })
                                    }
                                }
                            }
                        )
                    }
                }
                else -> result.add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", message.content.orEmpty())
                    }
                )
            }
        }
        // 去掉内部标记字段，避免被 Anthropic 拒绝。
        return result.map { element ->
            buildJsonObject {
                element.forEach { (key, value) ->
                    if (key != "_toolResults") put(key, value)
                }
            }
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
