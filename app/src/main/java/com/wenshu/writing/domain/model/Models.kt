package com.wenshu.writing.domain.model

/** 作品 */
data class Novel(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** 章节 */
data class Chapter(
    val id: Long = 0L,
    val novelId: Long = 0L,
    val title: String,
    val content: String = "",
    val orderIndex: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val wordCount: Int get() = countCharacters(content)
}

/** 人物卡 */
data class CharacterCard(
    val id: Long = 0L,
    val name: String,
    val role: String = "",
    val description: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** 世界观设定 */
data class WorldSetting(
    val id: Long = 0L,
    val category: String = "",
    val title: String,
    val content: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** OpenAI 兼容 API 配置 */
data class AiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        val PRESET_MODELS = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4.1-mini",
            "deepseek-chat",
            "deepseek-reasoner",
            "qwen-plus",
            "moonshot-v1-8k"
        )
    }
}

/** 一套可命名的 OpenAI 兼容 API 配置，用于多方案存档与一键切换 */
data class AiProfile(
    val id: Long = 0L,
    val name: String = "",
    val baseUrl: String = AiConfig.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = AiConfig.DEFAULT_MODEL,
    val updatedAt: Long = 0L
)

/** API 连通状态，对应界面中的绿 / 红 / 黄 / 灰提示 */
enum class ApiConnStatus {
    UNVERIFIED, CHECKING, SUCCESS, FAILURE, TIMEOUT
}

/** 一次连通探测的结果，携带可用于下拉选择的模型列表 */
data class ApiProbeOutcome(
    val status: ApiConnStatus,
    val message: String,
    val models: List<String> = emptyList()
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val activeAiProfileId: Long = -1L
)

enum class ChatRole {
    USER, ASSISTANT
}

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val isError: Boolean = false
)

/** 字数统计：按非空白字符计数，适配中文写作习惯 */
fun countCharacters(text: String): Int = text.count { !it.isWhitespace() }
