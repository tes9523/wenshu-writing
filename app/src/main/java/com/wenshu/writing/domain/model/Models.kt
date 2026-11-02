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

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM
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
