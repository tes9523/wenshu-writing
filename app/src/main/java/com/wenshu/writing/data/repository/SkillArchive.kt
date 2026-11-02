package com.wenshu.writing.data.repository

import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.domain.model.SkillReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 导入失败时会携带面向用户的提示文案。 */
class SkillImportException(message: String) : IllegalArgumentException(message)

/**
 * Skill zip 包的读写规则（1.0.4 定稿）。
 *
 * 读取兼容：技能根目录的 `main.md` / `main` / `skill.md` 为主体文件，`meta.json` 为可选项。
 * 缺少 meta.json 时自动使用「导入Skill_时间戳」作为名称、简介留空。根目录存在主体文件视为单技能包；
 * 否则每个一级子目录视为一个技能。仅读取技能根目录的主体文件，不递归嵌套包。
 *
 * 写出统一：`main` + `meta.json` + `references/`。
 */
object SkillArchive {

    data class ImportedSkill(
        val name: String,
        val description: String,
        val mainMarkdown: String,
        val references: List<SkillReference>
    )

    private val MAIN_FILE_NAMES = listOf("main.md", "main", "skill.md")
    /** 项目导出固定主体文件名（1.0.3 起的全局规范）。 */
    private const val MAIN_FILE_NAME = "main"
    private const val META_FILE_NAME = "meta.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun readZip(input: InputStream): List<ImportedSkill> {
        val entries = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val rawPath = entry.name.replace('\\', '/')
                    if (!entry.isDirectory) {
                        validatePath(rawPath)
                        val path = rawPath.trimStart('/')
                        val isHidden = path.startsWith("__MACOSX/") ||
                            path.substringAfterLast('/').startsWith(".")
                        if (!isHidden) {
                            entries[path] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: SkillImportException) {
            throw e
        } catch (e: ZipException) {
            val message = e.message.orEmpty()
            throw SkillImportException(
                if (message.contains("encrypt", ignoreCase = true)) {
                    "不支持加密压缩包，请解压后重新打包"
                } else {
                    "压缩包损坏，不是合法ZIP"
                }
            )
        } catch (e: Exception) {
            throw SkillImportException(e.message?.takeIf { it.contains("encrypt", true) }
                ?: "压缩包损坏，不是合法ZIP")
        }

        val mainByDir = entries.keys
            .filter { path -> MAIN_FILE_NAMES.any { it.equals(path.substringAfterLast('/'), true) } }
            .groupBy { path -> path.substringBeforeLast('/', "") }

        if (mainByDir.isEmpty()) {
            throw SkillImportException("未识别到Skill主体文件（main / skill.md）")
        }

        // 根目录主体文件优先，视为单技能包；否则只取一级子目录，避免嵌套包错乱。
        val roots = if (mainByDir.containsKey("")) {
            listOf("")
        } else {
            mainByDir.keys.filter { it.isNotBlank() && !it.contains('/') }.sorted()
        }
        if (roots.isEmpty()) {
            throw SkillImportException("未识别到Skill主体文件（main / skill.md）")
        }

        return roots.mapIndexed { index, root ->
            val mainPath = pickMainPath(mainByDir[root].orEmpty())
                ?: throw SkillImportException("未识别到Skill主体文件（main / skill.md）")
            val main = readText(entries.getValue(mainPath))
            val meta = parseMeta(entries, root, index)
            val folderName = root.substringAfterLast('/')
            val references = entries.asSequence()
                .filter { (path, _) ->
                    if (root.isEmpty()) {
                        path.startsWith("references/", ignoreCase = true)
                    } else {
                        path.startsWith("$root/references/", ignoreCase = true)
                    }
                }
                .filter { (path, _) ->
                    path.endsWith(".md", true) || path.endsWith(".txt", true)
                }
                .map { (path, bytes) ->
                    SkillReference(
                        name = path.substringAfterLast('/'),
                        content = readText(bytes)
                    )
                }
                .sortedBy { it.name }
                .toList()

            ImportedSkill(
                name = meta.first,
                description = meta.second,
                mainMarkdown = main,
                references = references
            )
        }
    }

    fun writeSingle(output: OutputStream, skill: Skill) {
        ZipOutputStream(output).use { zip ->
            zip.putText(MAIN_FILE_NAME, skill.mainMarkdown)
            zip.putText(META_FILE_NAME, metaJson(skill))
            skill.references.forEach { reference ->
                zip.putText("references/${safeFileName(reference.name)}", reference.content)
            }
        }
    }

    fun writeBatch(output: OutputStream, skills: List<Skill>) {
        ZipOutputStream(output).use { zip ->
            val usedFolders = mutableSetOf<String>()
            skills.forEach { skill ->
                val folder = uniqueFolderName(skill.name, usedFolders)
                zip.putText("$folder/$MAIN_FILE_NAME", skill.mainMarkdown)
                zip.putText("$folder/$META_FILE_NAME", metaJson(skill))
                skill.references.forEach { reference ->
                    zip.putText(
                        "$folder/references/${safeFileName(reference.name)}",
                        reference.content
                    )
                }
            }
        }
    }

    private fun pickMainPath(paths: List<String>): String? {
        MAIN_FILE_NAMES.forEach { expected ->
            paths.firstOrNull { it.substringAfterLast('/').equals(expected, true) }
                ?.let { return it }
        }
        return paths.firstOrNull()
    }

    /**
     * meta.json 为可选：存在且格式正确时读取 name / desc；
     * 缺失或无法解析时回落到「导入Skill_时间戳」，简介留空，允许用户在详情页修改。
     */
    private fun parseMeta(
        entries: Map<String, ByteArray>,
        root: String,
        index: Int
    ): Pair<String, String> {
        val metaPath = entries.keys.firstOrNull { path ->
            val expected = if (root.isEmpty()) META_FILE_NAME else "$root/$META_FILE_NAME"
            path.equals(expected, ignoreCase = true)
        } ?: return defaultSkillName(index) to ""

        val raw = readText(entries.getValue(metaPath))
        val obj: JsonObject = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            return defaultSkillName(index) to ""
        }
        val name = obj.stringField("name")?.trim()?.takeIf { it.isNotBlank() }
            ?: defaultSkillName(index)
        val desc = (obj.stringField("desc") ?: obj.stringField("description"))
            ?.trim()
            .orEmpty()
        return name to desc
    }

    private fun defaultSkillName(index: Int): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(System.currentTimeMillis() + index))
        return "导入Skill_$stamp"
    }

    private fun JsonObject.stringField(key: String): String? {
        val value = this[key] as? JsonPrimitive ?: return null
        return value.content
    }

    private fun validatePath(path: String) {
        val normalized = path.replace('\\', '/')
        val segments = normalized.split('/')
        if (normalized.startsWith("/") || segments.any { it == ".." }) {
            throw SkillImportException("压缩包包含非法路径，已拒绝导入")
        }
        if (normalized.contains(':')) {
            throw SkillImportException("压缩包包含非法路径，已拒绝导入")
        }
    }

    /** 统一按 UTF-8 解码并剔除 BOM 头，避免 Windows 编辑器产生的乱码。 */
    private fun readText(bytes: ByteArray): String {
        val text = bytes.toString(Charsets.UTF_8)
        return text.removePrefix("\uFEFF").removePrefix("\uFEFF")
    }

    private fun metaJson(skill: Skill): String = buildString {
        append("{\n")
        append("  \"name\": ").append(quote(skill.name)).append(",\n")
        append("  \"desc\": ").append(quote(skill.description)).append("\n")
        append("}\n")
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun safeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "reference.md" }.take(80)
    }

    private fun uniqueFolderName(name: String, used: MutableSet<String>): String {
        val base = safeFileName(name.ifBlank { "skill" })
        var candidate = base
        var index = 2
        while (!used.add(candidate.lowercase())) {
            candidate = "$base-$index"
            index++
        }
        return candidate
    }

    private fun ZipOutputStream.putText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
