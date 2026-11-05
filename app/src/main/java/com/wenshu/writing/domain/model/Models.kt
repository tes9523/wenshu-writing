package com.wenshu.writing.domain.model

/** 作品 */
data class Novel(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** 自定义封面图片的本地绝对路径；null 表示使用默认封面。 */
    val coverPath: String? = null,
    /** 回收站标记：非 null 表示已移入回收站，值为移入时间戳。 */
    val deletedAt: Long? = null
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

/**
 * 统一设定条目。1.0.6 起人物、世界观、大纲与所有自定义分类共用此模型。
 * category 固定内置：人物 / 世界观 / 大纲；其余为用户自定义分类。
 * bookId 为 null 表示全局公共素材，不参与任何单本书稿的自动读写。
 */
data class Setting(
    val id: Long = 0L,
    val bookId: Long? = null,
    val category: String,
    val title: String,
    /** 人物卡的「身份/定位」等短标签；其他分类可为空。 */
    val subtitle: String? = null,
    val content: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val displayTitle: String get() = title.ifBlank { "未命名" }
}

/** 1.0.6 起设定库三大内置分类，禁止删除、禁止改名。 */
object SettingCategories {
    const val CHARACTER = "人物"
    const val WORLD = "世界观"
    const val OUTLINE = "大纲"
    val BUILT_IN = listOf(CHARACTER, WORLD, OUTLINE)
    fun isBuiltIn(category: String): Boolean = BUILT_IN.contains(category)
}

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
    val activeAiProfileId: Long = -1L,
    /** 当前激活书稿；-1 表示未选择，UI 会回落到「全局公共素材」。 */
    val activeNovelId: Long = -1L,
    /** 主题色预设名。 */
    val themeColorName: String = "INK",
    /** 背景色预设名；空串表示跟随主题。 */
    val backgroundColorName: String = "",
    /** 全局背景图片本地路径。 */
    val backgroundImagePath: String? = null,
    /** AI 回复完成 / 弹出确认窗口时播放提示音。 */
    val soundEnabled: Boolean = true,
    /** 提示音类型：notification / ringtone / alarm。 */
    val soundType: String = "notification",
    /** 应用字体：system / serif / sans / mono。 */
    val fontName: String = "system",
    /** 聊天消息字号（sp）。 */
    val chatFontSize: Int = 14
)

/** 灵感备忘录（纯文本）。bookId 为 null 表示全局公共备忘录。 */
data class MemoNote(
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val bookId: Long? = null,
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
    /** 内置流水线技能（如「开启新书」），在 @ 面板中被选中时进入分阶段流程。 */
    val isPipeline: Boolean = false,
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
    val isError: Boolean = false,
    val createdAt: Long = 0L
)

/** AI 对话会话。bookId 为 null 表示全局通用会话。 */
data class ChatSession(
    val id: Long = 0L,
    val title: String = "新对话",
    val bookId: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** 章节任务卡：正文生成前标准化任务单。 */
data class ChapterTask(
    val id: Long = 0L,
    val novelId: Long = 0L,
    val orderIndex: Int = 0,
    val title: String = "",
    val goal: String = "",
    val conflict: String = "",
    val infoIncrement: String = "",
    val payoff: String = "",
    val hook: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun toPromptCard(): String = buildString {
        appendLine("### 第 ${orderIndex + 1} 章：$title")
        appendLine("本章目标：$goal")
        appendLine("核心冲突：$conflict")
        appendLine("信息增量：$infoIncrement")
        appendLine("爽点设计：$payoff")
        appendLine("章末钩子：$hook")
    }
}

enum class PipelineStatus {
    RUNNING, PAUSED, COMPLETED, FAILED
}

/** 流水线断点：stage 表示下一个待执行阶段（1~6）。 */
data class PipelineRun(
    val id: Long = 0L,
    val novelId: Long = 0L,
    val skillId: Long = 0L,
    val stage: Int = 1,
    val status: PipelineStatus = PipelineStatus.PAUSED,
    val inputJson: String = "",
    val outputJson: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** 「开启新书」阶段 1 采集的新书信息。 */
@kotlinx.serialization.Serializable
data class NewBookInfo(
    val title: String = "",
    val genre: String = "",
    val sellingPoint: String = "",
    val track: String = "",
    val coreIdea: String = "",
    val style: String = ""
) {
    fun toPromptText(): String = buildString {
        appendLine("书名：$title")
        appendLine("题材：$genre")
        appendLine("卖点：$sellingPoint")
        appendLine("目标赛道：$track")
        appendLine("核心创意：$coreIdea")
        appendLine("风格偏好：$style")
    }

    fun isValid(): Boolean = title.isNotBlank() && genre.isNotBlank() &&
        sellingPoint.isNotBlank() && track.isNotBlank() &&
        coreIdea.isNotBlank() && style.isNotBlank()
}

/** 字数统计：按非空白字符计数，适配中文写作习惯 */
fun countCharacters(text: String): Int = text.count { !it.isWhitespace() }
