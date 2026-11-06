package com.wenshu.writing.feature.ai

import com.wenshu.writing.data.remote.AgentToolCall
import com.wenshu.writing.data.remote.AgentToolSpec
import com.wenshu.writing.data.repository.ChatRepository
import com.wenshu.writing.data.repository.NovelRepository
import com.wenshu.writing.data.repository.SettingRepository
import com.wenshu.writing.data.repository.SkillRepository
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.Setting
import com.wenshu.writing.domain.model.SettingCategories
import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.domain.model.SkillReference
import com.wenshu.writing.domain.model.stripFrontMatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 技能通过 question 工具向用户发起的确认。 */
data class PendingQuestionOption(val label: String, val description: String = "")

data class PendingQuestion(
    val question: String,
    val header: String = "确认",
    val options: List<PendingQuestionOption> = emptyList()
)

/**
 * 本地工具集：把 novel-writer-manager / skill-creator 需要的工具映射到 App 内的书稿、设定、章节与技能数据。
 * 所有文件路径都是"项目虚拟文件"，例如 小说资料/人物库.md、章节内容/第1章.md。
 */
class AgentToolExecutor(
    private val novelRepository: NovelRepository,
    private val settingRepository: SettingRepository,
    private val skillRepository: SkillRepository,
    private val chatRepository: ChatRepository,
    private val bookIdProvider: () -> Long?,
    private val sessionIdProvider: () -> Long?,
    private val activeSkillsProvider: () -> List<Skill>,
    private val askQuestion: suspend (PendingQuestion) -> String,
    private val log: (String) -> Unit,
    /** 写入成功后回调，用于把书稿切换为当前激活书稿，保证用户在书稿/设定库中能看到刚写入的内容。 */
    private val onBookVisible: suspend (Long) -> Unit = {},
    /** 是否允许创建书稿。仅「创建新书」类技能开启；制作/调试 Skill 时禁止生成书稿条目。 */
    private val allowCreateProject: Boolean = true
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 最近一次 skill_lookup 的技能名，用于在多个技能都有同名配套文件时消除歧义。 */
    private var lastSkillName: String? = null

    suspend fun execute(call: AgentToolCall): String {
        log("调用工具 ${call.name}（参数 ${call.arguments.take(400)}）")
        val args = runCatching {
            json.parseToJsonElement(call.arguments.ifBlank { "{}" }).jsonObject
        }.getOrElse { return "工具参数解析失败：${it.message}" }
        return runCatching {
            when (call.name) {
                "create_project" -> createProject(args)
                "file_read" -> readFile(args)
                "file_write" -> writeFile(args)
                "skill_lookup" -> lookupSkill(args)
                "create_skill" -> createSkill(args)
                "update_skill" -> updateSkill(args)
                "question" -> askUser(args)
                else -> "未知工具：${call.name}"
            }
        }.getOrElse { "工具 ${call.name} 执行失败：${it.message ?: "未知错误"}" }
    }

    private suspend fun createProject(args: JsonObject): String {
        if (!allowCreateProject) {
            return "当前技能不允许创建书稿。创建书稿仅由「创建新书」类技能执行；制作或调试 Skill 不会在书稿模块生成条目。"
        }
        val title = args.stringOf("title", "name", "book_name", "project_name")
        if (title.isBlank()) return "创建失败：缺少书名参数 title"
        val description = args.stringOf("description", "intro").ifBlank { "由 AI 技能创建" }
        val existing = novelRepository.findNovelByTitle(title)
        val novelId = existing?.id ?: novelRepository.createNovel(title, description)
        val saved = novelRepository.getNovel(novelId)
            ?: return "创建失败：书稿《$title》未能写入数据库，请稍后重试"
        sessionIdProvider()?.let { chatRepository.bindSessionBook(it, novelId) }
        onBookVisible(novelId)
        log("create_project -> novelId=$novelId")
        return if (existing != null) {
            "书稿《${saved.title}》已存在（novelId=$novelId），已绑定到当前会话。"
        } else {
            "已创建书稿《${saved.title}》（novelId=$novelId），并绑定到当前会话。"
        }
    }

    private suspend fun readFile(args: JsonObject): String {
        val rawPath = args.stringOf("path", "file_path", "filename", "name")
        if (rawPath.isBlank()) return "读取失败：缺少 path 参数"
        val path = normalizePath(rawPath)

        // 技能配套资源：references/xxx.md；主文件兼容 skill.md / SKILL.md / main.md / main。
        val fileName = path.substringAfterLast('/')
        val isMainFile = fileName.lowercase() in setOf("skill.md", "main.md", "main")
        if (path.contains("references/") || isMainFile) {
            val skills = activeSkillsProvider()
            // 优先在最近一次 skill_lookup 的技能里查找，避免同名 templates.md 串台。
            val ordered = lastSkillName?.let { name ->
                val preferred = skills.filter { it.name == name }
                preferred + skills.filterNot { it.name == name }
            } ?: skills
            ordered.forEach { skill ->
                if (isMainFile) {
                    return buildString {
                        appendLine("# 技能：${skill.name}")
                        appendLine(stripFrontMatter(skill.mainMarkdown))
                        if (skill.references.isNotEmpty()) {
                            appendLine()
                            appendLine("配套文件：")
                            skill.references.forEach { appendLine("- ${it.name}") }
                        }
                    }
                }
                skill.references.firstOrNull {
                    it.name == fileName || it.name.endsWith("/$fileName") || it.name == path
                }?.let { return it.content }
            }
        }

        val bookId = currentBookId()
            ?: return "当前会话未绑定书稿。请先调用 create_project 创建书稿，或在会话中绑定书稿。"

        return when {
            path == "小说资料/人物库.md" || path == "人物库.md" -> readCharacters(bookId)
            path == "小说资料/世界观.md" || path == "世界观.md" -> readWorld(bookId)
            path == "小说资料/章节摘要.md" || path == "章节摘要.md" -> readSummaries(bookId)
            path == "小说资料/大纲.md" || path == "大纲.md" -> readOutline(bookId)
            path == "章节内容" || path == "章节内容/" -> listChapters(bookId)
            path == "小说资料" || path == "小说资料/" -> listDocs(bookId)
            path.startsWith("章节内容/") -> readChapter(bookId, path)
            else -> readProjectDoc(bookId, path)
        }
    }

    private suspend fun writeFile(args: JsonObject): String {
        val rawPath = args.stringOf("path", "file_path", "filename", "name")
        val content = args.stringOf("content", "text", "data")
        if (rawPath.isBlank()) return "写入失败：缺少 path 参数"
        if (content.isBlank()) return "写入失败：content 为空"
        val path = normalizePath(rawPath)
        val bookId = currentBookId()
            ?: return "写入失败：当前会话未绑定书稿。请先调用 create_project 创建书稿后再写入文件。"
        // 校验目标书稿真实存在，避免写入到已被删除或不存在的书稿。
        if (novelRepository.getNovel(bookId) == null) {
            return "写入失败：目标书稿不存在（ID=$bookId，可能已被删除），请重新创建或绑定书稿。"
        }

        return when {
            path == "小说资料/人物库.md" || path == "人物库.md" -> {
                val items = parseCharacters(content)
                replaceCategory(bookId, SettingCategories.CHARACTER, items)
                val error = verifyCategory(bookId, SettingCategories.CHARACTER, items)
                if (error != null) return error
                finishWrite(bookId, "已写入 小说资料/人物库.md（${items.size} 条人物设定，已校验入库）")
            }
            path == "小说资料/世界观.md" || path == "世界观.md" -> {
                val items = parseWorld(content)
                replaceCategory(bookId, SettingCategories.WORLD, items)
                val error = verifyCategory(bookId, SettingCategories.WORLD, items)
                if (error != null) return error
                finishWrite(bookId, "已写入 小说资料/世界观.md（${items.size} 条世界观设定，已校验入库）")
            }
            path == "小说资料/章节摘要.md" || path == "章节摘要.md" -> {
                val items = listOf(
                    Setting(category = CATEGORY_SUMMARY, title = "章节摘要", content = content)
                )
                replaceCategory(bookId, CATEGORY_SUMMARY, items)
                val error = verifyCategory(bookId, CATEGORY_SUMMARY, items)
                if (error != null) return error
                finishWrite(bookId, "已写入 小说资料/章节摘要.md（已校验入库）")
            }
            path.startsWith("章节内容/") -> writeChapter(bookId, path, content)
            path == "小说资料/大纲.md" || path == "大纲.md" -> {
                val items = listOf(
                    Setting(category = SettingCategories.OUTLINE, title = "大纲", content = content)
                )
                replaceCategory(bookId, SettingCategories.OUTLINE, items)
                val error = verifyCategory(bookId, SettingCategories.OUTLINE, items)
                if (error != null) return error
                finishWrite(bookId, "已写入 设定库/大纲（已校验入库）")
            }
            else -> {
                writeProjectDoc(bookId, path, content)
                val saved = settingRepository.getByCategory(bookId, CATEGORY_DOC)
                    .firstOrNull { it.title == path }
                if (saved == null || saved.content.trim() != content.trim()) {
                    "写入失败：项目文档「$path」写入后校验未通过（库中未找到对应内容）"
                } else {
                    finishWrite(bookId, "已写入 $path（已校验入库）")
                }
            }
        }
    }

    /** 写入成功后的统一出口：切换激活书稿并返回成功话术。 */
    private suspend fun finishWrite(bookId: Long, message: String): String {
        onBookVisible(bookId)
        return message
    }

    /** 写入后重新读取校验：读取失败时返回错误信息，成功返回 null。 */
    private suspend fun verifyCategory(bookId: Long, category: String, expected: List<Setting>): String? {
        val saved = runCatching { settingRepository.getByCategory(bookId, category) }
            .getOrElse { return "写入失败：读取校验异常（${it.message}）" }
        if (expected.isEmpty()) return "写入失败：解析后没有可写入的条目，请检查文件格式"
        val missing = expected.filter { item ->
            saved.none { it.title == item.title && it.content.trim() == item.content.trim() }
        }
        return if (missing.isEmpty()) {
            null
        } else {
            "写入失败：校验未通过，${missing.size} 条内容未入库（如「${missing.first().title}」），请重试"
        }
    }

    private suspend fun lookupSkill(args: JsonObject): String {
        val name = args.stringOf("name", "skill_name", "skill")
        if (name.isBlank()) return "缺少 name 参数"
        val skill = skillRepository.findByName(name) ?: return "未找到技能「$name」"
        lastSkillName = skill.name
        return buildString {
            appendLine("技能名称：${skill.name}")
            appendLine("描述：${skill.description}")
            appendLine("类型：${if (skill.isBuiltIn) "内置技能" else "自定义技能"}")
            appendLine()
            appendLine(stripFrontMatter(skill.mainMarkdown))
            if (skill.references.isNotEmpty()) {
                appendLine()
                appendLine("配套文件：")
                skill.references.forEach { appendLine("- ${it.name}") }
            }
        }
    }

    private suspend fun createSkill(args: JsonObject): String {
        val name = args.stringOf("name", "skill_name")
        if (name.isBlank()) return "创建失败：缺少 name 参数"
        val description = args.stringOf("description", "desc")
        val content = args.stringOf("content", "body", "main")
        if (content.isBlank()) return "创建失败：缺少 content 参数（完整 skill.md 内容）"
        val references = parseReferences(args["references"])
        val id = skillRepository.create(
            name = name,
            description = description,
            mainMarkdown = content,
            references = references,
            isBuiltIn = false
        )
        val saved = skillRepository.getById(id)
        log("create_skill -> ${saved?.name}")
        return "已创建技能《${saved?.name ?: name}》（${references.size} 个配套文件），可在技能管理页查看。"
    }

    private suspend fun updateSkill(args: JsonObject): String {
        val name = args.stringOf("skill_name", "name")
        if (name.isBlank()) return "更新失败：缺少 skill_name 参数"
        val skill = skillRepository.findByName(name) ?: return "未找到技能「$name」"
        val content = args.stringOf("content", "body", "main").takeIf { it.isNotBlank() }
        val referenceName = args.stringOf("reference_name", "reference_path")
        val referenceContent = args.stringOf("reference_content", "reference_body")

        if (skill.isBuiltIn && args.stringOf("force_builtin") != "true") {
            // 内置技能不原地修改：克隆为用户副本，避免下次启动被内置同步覆盖。
            val clonedId = skillRepository.create(
                name = "${skill.name}（自定义）",
                description = skill.description,
                mainMarkdown = content ?: skill.mainMarkdown,
                references = updatedReferences(skill, referenceName, referenceContent),
                isBuiltIn = false
            )
            val cloned = skillRepository.getById(clonedId)
            log("update_skill builtin cloned -> ${cloned?.name}")
            return "内置技能不可直接修改，已克隆为用户副本《${cloned?.name ?: skill.name}》。"
        }

        skillRepository.update(
            skill.copy(
                mainMarkdown = content ?: skill.mainMarkdown,
                references = updatedReferences(skill, referenceName, referenceContent)
            )
        )
        return "已更新技能《${skill.name}》。"
    }

    private fun updatedReferences(
        skill: Skill,
        referenceName: String,
        referenceContent: String
    ): List<SkillReference> {
        if (referenceName.isBlank() || referenceContent.isBlank()) return skill.references
        val existed = skill.references.any { it.name == referenceName }
        return if (existed) {
            skill.references.map {
                if (it.name == referenceName) it.copy(content = referenceContent) else it
            }
        } else {
            skill.references + SkillReference(referenceName, referenceContent)
        }
    }

    private suspend fun askUser(args: JsonObject): String {
        val questions = runCatching { args["questions"]?.jsonArray }.getOrNull()
        val parsed = questions?.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val text = obj["question"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val header = obj["header"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val options = runCatching {
                obj["options"]?.jsonArray?.mapNotNull { optionElement ->
                    val option = optionElement.jsonObject
                    val label = option["label"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null
                    PendingQuestionOption(
                        label = label,
                        description = option["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    )
                }.orEmpty()
            }.getOrDefault(emptyList())
            text to (header to options)
        }.orEmpty()

        if (parsed.isEmpty()) {
            val text = args.stringOf("question", "text", "prompt")
            if (text.isBlank()) return "缺少问题内容"
            return askQuestion(PendingQuestion(text, "确认", defaultOptions()))
        }

        // 支持一次询问多个问题：逐个弹窗，汇总答案回传给模型。
        return buildString {
            parsed.forEachIndexed { index, (text, meta) ->
                val (header, options) = meta
                val resolved = options.ifEmpty { defaultOptions() }
                val answer = askQuestion(PendingQuestion(text, header, resolved))
                if (index > 0) appendLine()
                append("问题：$text → 用户选择：$answer")
            }
        }
    }

    private fun defaultOptions(): List<PendingQuestionOption> = listOf(
        PendingQuestionOption("继续", "按当前方案继续"),
        PendingQuestionOption("取消", "暂不执行")
    )

    /**
     * 优先使用 UI 状态中的绑定书稿；create_project 刚绑定时 Room Flow 可能还没回调，
     * 因此兜底直接查一次会话记录，避免紧接着的 file_write 误判为未绑定。
     */
    private suspend fun currentBookId(): Long? {
        bookIdProvider()?.let { return it }
        val sessionId = sessionIdProvider() ?: return null
        return chatRepository.getSession(sessionId)?.bookId
    }

    // ---- 读取帮助 ----

    private suspend fun readCharacters(bookId: Long): String {
        val items = settingRepository.getByCategory(bookId, SettingCategories.CHARACTER)
        if (items.isEmpty()) return "人物库为空。"
        if (items.size == 1 && items.first().title == "人物库") return items.first().content
        return buildString {
            appendLine("# 人物库")
            appendLine()
            items.forEach { item ->
                appendLine("### ${item.displayTitle}")
                if (!item.subtitle.isNullOrBlank()) appendLine("- **身份**：${item.subtitle}")
                appendLine(item.content)
                appendLine()
            }
        }
    }

    private suspend fun readWorld(bookId: Long): String {
        val items = settingRepository.getByCategory(bookId, SettingCategories.WORLD)
        if (items.isEmpty()) return "世界观为空。"
        if (items.size == 1 && items.first().title == "世界观") return items.first().content
        return buildString {
            appendLine("# 世界观")
            appendLine()
            items.forEach { item ->
                appendLine("## ${item.displayTitle}")
                appendLine(item.content)
                appendLine()
            }
        }
    }

    private suspend fun readSummaries(bookId: Long): String {
        val items = settingRepository.getByCategory(bookId, CATEGORY_SUMMARY)
        return items.firstOrNull()?.content ?: "章节摘要为空。"
    }

    private suspend fun readOutline(bookId: Long): String {
        val items = settingRepository.getByCategory(bookId, SettingCategories.OUTLINE)
        if (items.isEmpty()) return "大纲为空。"
        return items.joinToString("\n\n") { it.content }
    }

    private suspend fun listChapters(bookId: Long): String {
        val chapters = novelRepository.getChapters(bookId)
        if (chapters.isEmpty()) return "章节内容/ 目录为空，还没有章节文件。"
        val recent = chapters.takeLast(5)
        return buildString {
            appendLine("章节内容/ 目录（共 ${chapters.size} 章）：")
            chapters.forEach { chapter ->
                val mark = if (chapter in recent) "（最近）" else ""
                appendLine("- 章节内容/${chapterFileOf(chapter)}｜${chapter.title}｜${chapter.wordCount} 字$mark")
            }
            appendLine()
            appendLine("提示：请读取最近五章的正文，保持情节连贯。")
        }
    }

    private suspend fun listDocs(bookId: Long): String {
        val docs = settingRepository.getByCategory(bookId, CATEGORY_DOC)
        return buildString {
            appendLine("小说资料/ 目录：")
            appendLine("- 小说资料/人物库.md（设定库/人物）")
            appendLine("- 小说资料/世界观.md（设定库/世界观）")
            appendLine("- 小说资料/大纲.md（设定库/大纲）")
            appendLine("- 小说资料/章节摘要.md（设定库/章节摘要）")
            docs.forEach { appendLine("- ${it.title}") }
        }
    }

    private suspend fun readChapter(bookId: Long, path: String): String {
        val fileName = path.substringAfterLast('/').substringBeforeLast('.')
        val chapters = novelRepository.getChapters(bookId)
        val target = chapters.firstOrNull { chapter ->
            chapter.title == fileName ||
                chapter.title.startsWith(fileName) ||
                chapterFileOf(chapter) == path.substringAfterLast('/')
        } ?: return "未找到章节文件：$path"
        return "# ${target.title}\n\n${target.content}"
    }

    private suspend fun readProjectDoc(bookId: Long, path: String): String {
        val docs = settingRepository.getByCategory(bookId, CATEGORY_DOC)
        val entry = docs.firstOrNull { it.title == path }
            ?: return "未找到文件：$path"
        return entry.content
    }

    // ---- 写入帮助 ----

    private suspend fun replaceCategory(bookId: Long, category: String, items: List<Setting>) {
        settingRepository.replaceByCategory(bookId, category, items)
    }

    private suspend fun writeChapter(bookId: Long, path: String, content: String): String {
        val fileName = path.substringAfterLast('/')
        val base = fileName.substringBeforeLast('.')
        val headingTitle = content.lines()
            .firstOrNull { it.trimStart().startsWith("# ") }
            ?.trimStart()
            ?.removePrefix("# ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val title = headingTitle ?: base
        val existing = novelRepository.getChapters(bookId).firstOrNull {
            it.title == title || it.title == base || it.title.startsWith(base)
        }
        val chapterId = if (existing != null) {
            novelRepository.saveChapter(existing.id, title, content)
            existing.id
        } else {
            novelRepository.createChapter(bookId, title).also { id ->
                novelRepository.saveChapter(id, title, content)
            }
        }
        // 写入后重新读取校验，确保章节正文真正持久化。
        val saved = novelRepository.getChapter(chapterId)
            ?: return "写入失败：章节「$title」写入后无法读取（可能未成功入库）"
        if (saved.content.trim() != content.trim()) {
            return "写入失败：章节「$title」写入后校验未通过（库中正文与写入内容不一致）"
        }
        onBookVisible(bookId)
        return if (existing != null) {
            "已更新章节文件 $path（章节「$title」已校验入库）"
        } else {
            "已创建章节文件 $path（章节「$title」已校验入库）"
        }
    }

    private suspend fun writeProjectDoc(bookId: Long, path: String, content: String) {
        val docs = settingRepository.getByCategory(bookId, CATEGORY_DOC)
        docs.filter { it.title == path }.forEach { settingRepository.delete(it) }
        settingRepository.addSetting(CATEGORY_DOC, path, content, bookId)
    }

    // ---- 解析帮助 ----

    private fun parseCharacters(markdown: String): List<Setting> {
        val result = mutableListOf<Setting>()
        var name: String? = null
        var subtitle = ""
        val body = StringBuilder()

        fun flush() {
            val current = name ?: return
            result.add(
                Setting(
                    category = SettingCategories.CHARACTER,
                    title = current,
                    subtitle = subtitle.ifBlank { null },
                    content = body.toString().trim()
                )
            )
            name = null
            subtitle = ""
            body.clear()
        }

        markdown.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("### ")) {
                flush()
                name = trimmed.removePrefix("### ").trim().trim('[', ']')
            } else if (name != null) {
                val roleMatch = ROLE_REGEX.find(trimmed)
                if (roleMatch != null && subtitle.isBlank()) {
                    subtitle = roleMatch.groupValues[1].trim()
                } else {
                    body.appendLine(line)
                }
            }
        }
        flush()
        if (result.isEmpty()) {
            return listOf(
                Setting(
                    category = SettingCategories.CHARACTER,
                    title = "人物库",
                    content = markdown
                )
            )
        }
        return result
    }

    private fun parseWorld(markdown: String): List<Setting> {
        val result = mutableListOf<Setting>()
        var title: String? = null
        val body = StringBuilder()

        fun flush() {
            val current = title ?: return
            result.add(
                Setting(
                    category = SettingCategories.WORLD,
                    title = current,
                    content = body.toString().trim()
                )
            )
            title = null
            body.clear()
        }

        markdown.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("## ")) {
                flush()
                title = trimmed.removePrefix("## ").trim()
            } else if (title != null) {
                body.appendLine(line)
            }
        }
        flush()
        if (result.isEmpty()) {
            return listOf(
                Setting(
                    category = SettingCategories.WORLD,
                    title = "世界观",
                    content = markdown
                )
            )
        }
        return result
    }

    private fun parseReferences(element: kotlinx.serialization.json.JsonElement?): List<SkillReference> {
        if (element == null) return emptyList()
        // 对象形式：{ "路径": "内容" }
        runCatching { element.jsonObject }.getOrNull()?.let { obj ->
            return obj.mapNotNull { (key, value) ->
                val content = runCatching { value.jsonPrimitive.contentOrNull }.getOrNull()
                    ?: return@mapNotNull null
                if (content.isBlank()) null else SkillReference(key, content)
            }
        }
        // 数组形式：[{ "path": "...", "content": "..." }]
        runCatching { element.jsonArray }.getOrNull()?.let { array ->
            return array.mapNotNull { item ->
                val obj = runCatching { item.jsonObject }.getOrNull()
                    ?: return@mapNotNull null
                val name = runCatching {
                    obj["path"]?.jsonPrimitive?.contentOrNull
                        ?: obj["name"]?.jsonPrimitive?.contentOrNull
                        ?: obj["file_name"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val content = runCatching {
                    obj["content"]?.jsonPrimitive?.contentOrNull
                }.getOrNull().orEmpty()
                if (content.isBlank()) null else SkillReference(name, content)
            }
        }
        return emptyList()
    }

    private fun chapterFileOf(chapter: Chapter): String {
        val short = chapter.title.substringBefore(' ')
            .ifBlank { "第${chapter.orderIndex + 1}章" }
        return "$short.md"
    }

    /**
     * 统一文件路径。兼容 novel-writer-manager 使用的「设定库/人物」等别名，
     * 并支持「目录 小说资料」「目录/章节内容」这类目录写法。
     */
    private fun normalizePath(path: String): String {
        val cleaned = path.trim()
            .replace('\\', '/')
            .removePrefix("./")
            .trim('/')
        val stripped = when {
            cleaned.startsWith("目录/") -> cleaned.removePrefix("目录/")
            cleaned.startsWith("目录 ") -> cleaned.removePrefix("目录 ").trim('/')
            else -> cleaned
        }
        return when (stripped) {
            "设定库", "设定库/", "小说资料", "小说资料/", "目录" -> "小说资料"
            "设定库/人物", "设定库/人物.md", "设定库/人物库", "设定库/人物库.md" -> "小说资料/人物库.md"
            "设定库/世界观", "设定库/世界观.md" -> "小说资料/世界观.md"
            "设定库/大纲", "设定库/大纲.md" -> "小说资料/大纲.md"
            "设定库/章节摘要", "设定库/章节摘要.md" -> "小说资料/章节摘要.md"
            else -> stripped
        }
    }

    private fun JsonObject.stringOf(vararg keys: String): String {
        keys.forEach { key ->
            val value = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
            if (!value.isNullOrBlank()) return value
        }
        return ""
    }

    companion object {
        val CATEGORY_SUMMARY = "章节摘要"
        val CATEGORY_DOC = "项目文档"

        private val ROLE_REGEX = Regex("""^[-*]\s*\*\*(身份|定位|角色)\*\*[：:]\s*(.*)$""")

        /** 供 Agent 循环注入的系统提示。create_project 仅在「创建新书」类技能激活时提供。 */
        fun toolSpecs(allowCreateProject: Boolean = true): List<AgentToolSpec> = buildList {
            if (allowCreateProject) add(
                AgentToolSpec(
                    name = "create_project",
                    description = "创建一本新书稿并绑定到当前会话。写小说前若还没有书稿，必须先调用此工具。",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") {
                                put("type", "string")
                                put("description", "书名")
                            }
                            putJsonObject("description") {
                                put("type", "string")
                                put("description", "一句话简介，可选")
                            }
                        }
                        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("title")) }
                    }
                )
            )
            add(AgentToolSpec(
                name = "file_read",
                description = "读取当前书稿的项目文件。path 可为 设定库/人物、设定库/世界观、设定库/大纲、设定库/章节摘要（等价于 小说资料/人物库.md 等），或 章节内容/第X章.md、目录 章节内容 / 小说资料。也可读取技能配套文件 references/xxx.md。",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "文件路径或目录")
                        }
                    }
                    putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")) }
                }
            ))
            add(AgentToolSpec(
                name = "file_write",
                description = "写入当前书稿的项目文件。设定库/人物、设定库/世界观、设定库/大纲、设定库/章节摘要会同步到设定库，章节内容/第X章.md 会创建或更新章节。",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "文件路径，如 章节内容/第3章.md")
                        }
                        putJsonObject("content") {
                            put("type", "string")
                            put("description", "完整文件内容")
                        }
                    }
                    putJsonArray("required") {
                        add(kotlinx.serialization.json.JsonPrimitive("path"))
                        add(kotlinx.serialization.json.JsonPrimitive("content"))
                    }
                }
            ))
            add(AgentToolSpec(
                name = "skill_lookup",
                description = "按名称读取技能的完整 skill.md 正文与配套文件列表。修改技能前先用它了解现状。",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("name") {
                            put("type", "string")
                            put("description", "技能名称")
                        }
                    }
                    putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("name")) }
                }
            ))
            add(AgentToolSpec(
                name = "create_skill",
                description = "创建新的自定义技能。content 为完整 skill.md（含 frontmatter），references 为 路径->内容 的对象。",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("name") {
                            put("type", "string")
                            put("description", "英文小写+连字符的技能名")
                        }
                        putJsonObject("description") {
                            put("type", "string")
                            put("description", "技能何时触发的描述")
                        }
                        putJsonObject("content") {
                            put("type", "string")
                            put("description", "完整 skill.md 内容，以 --- 开头")
                        }
                        putJsonObject("references") {
                            put("type", "object")
                            put("description", "配套文件映射：路径 -> 内容")
                        }
                    }
                    putJsonArray("required") {
                        add(kotlinx.serialization.json.JsonPrimitive("name"))
                        add(kotlinx.serialization.json.JsonPrimitive("description"))
                        add(kotlinx.serialization.json.JsonPrimitive("content"))
                    }
                }
            ))
            add(AgentToolSpec(
                name = "update_skill",
                description = "更新已有技能。内置技能被修改时会自动克隆为用户副本。",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("skill_name") {
                            put("type", "string")
                            put("description", "技能名称")
                        }
                        putJsonObject("content") {
                            put("type", "string")
                            put("description", "新的完整 skill.md，可选")
                        }
                        putJsonObject("reference_name") {
                            put("type", "string")
                            put("description", "只更新某个配套文件时填其路径")
                        }
                        putJsonObject("reference_content") {
                            put("type", "string")
                            put("description", "配套文件的新内容")
                        }
                    }
                    putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("skill_name")) }
                }
            ))
            add(AgentToolSpec(
                name = "question",
                description = "向用户发起确认或选择。任何写入文件前都必须先用它征求用户同意。",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("questions") {
                            put("type", "array")
                            put("description", "问题列表")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("question") { put("type", "string") }
                                    putJsonObject("header") { put("type", "string") }
                                    putJsonObject("options") {
                                        put("type", "array")
                                        putJsonObject("items") {
                                            put("type", "object")
                                            putJsonObject("properties") {
                                                putJsonObject("label") { put("type", "string") }
                                                putJsonObject("description") { put("type", "string") }
                                            }
                                            putJsonArray("required") {
                                                add(kotlinx.serialization.json.JsonPrimitive("label"))
                                            }
                                        }
                                    }
                                }
                                putJsonArray("required") {
                                    add(kotlinx.serialization.json.JsonPrimitive("question"))
                                    add(kotlinx.serialization.json.JsonPrimitive("options"))
                                }
                            }
                        }
                    }
                    putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("questions")) }
                }
            ))
        }
    }
}
