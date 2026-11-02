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

/** AI 接口类型：OpenAI 兼容规范 / Anthropic Messages 规范 */
enum class ApiProviderType(val label: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic")
}

/** AI 接口配置 */
data class AiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val apiType: ApiProviderType = ApiProviderType.OPENAI
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

/** 一套可命名的 AI 提供商配置，用于多方案存档与一键切换 */
data class AiProfile(
    val id: Long = 0L,
    val name: String = "",
    val baseUrl: String = AiConfig.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = AiConfig.DEFAULT_MODEL,
    val apiType: ApiProviderType = ApiProviderType.OPENAI,
    val models: List<String> = emptyList(),
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

/** 灵感备忘录（纯文本） */
data class MemoNote(
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val displayTitle: String get() = title.ifBlank { "无标题笔记" }

    val preview: String
        get() = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .take(120)
}

/** Skill 的参考素材文件（references 目录下的 md / txt） */
data class SkillReference(
    val name: String,
    val content: String
)

/**
 * 写作技能。mainMarkdown 为主提示词，references 为按需注入的参考素材。
 * 内置技能随应用预置，自定义技能由用户新建或导入。
 */
data class Skill(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val mainMarkdown: String = "",
    val references: List<SkillReference> = emptyList(),
    val isBuiltIn: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    /** 合并为注入系统提示词的一段文本。 */
    fun toPromptSection(): String = buildString {
        appendLine("## 技能：$name")
        if (description.isNotBlank()) appendLine(description)
        if (mainMarkdown.isNotBlank()) appendLine(stripFrontMatter(mainMarkdown))
        references.forEach { reference ->
            appendLine("### 参考素材：${reference.name}")
            appendLine(reference.content)
        }
    }
}

/** 去除 Skill 主文件开头的 YAML frontmatter，避免注入时产生噪声。 */
fun stripFrontMatter(markdown: String): String {
    val trimmed = markdown.trimStart()
    if (!trimmed.startsWith("---")) return markdown
    val end = trimmed.indexOf("\n---", startIndex = 3)
    if (end < 0) return markdown
    val after = trimmed.substring(end + 4)
    return after.trimStart('\n', '\r')
}

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
