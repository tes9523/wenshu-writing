package com.wenshu.writing.data.repository

import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.domain.model.SkillReference
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
 * Skill zip 包的读写规则（1.0.9 更新）。
 *
 * 读取：兼容 skill.md（新规范）、SKILL.md、main.md / main（旧包），无需任何配置文件。
 * 根目录主体文件视为单技能包；否则每个一级子目录视为一个技能。
 * 名称优先取一级目录名，根目录单技能则用「导入Skill_时间戳」。
 *
 * 写出：主体文件统一为 skill.md，附 references/ 参考素材，不写入任何 json 或冗余文件。
 */
object SkillArchive {

    data class ImportedSkill(
        val name: String,
        val description: String,
        val mainMarkdown: String,
        val references: List<SkillReference>
    )

    /** 1.0.9 起主文件统一 skill.md；导入时继续兼容旧包里的 main.md / main。 */
    private val MAIN_FILE_NAMES = listOf("skill.md", "SKILL.md", "main.md", "main")
    private const val MAIN_FILE_NAME = "skill.md"

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
            throw SkillImportException("未识别到Skill主体文件（skill.md / main.md）")
        }

        // 根目录主体文件优先，视为单技能包；否则只取一级子目录，避免嵌套包错乱。
        val roots = if (mainByDir.containsKey("")) {
            listOf("")
        } else {
            mainByDir.keys.filter { it.isNotBlank() && !it.contains('/') }.sorted()
        }
        if (roots.isEmpty()) {
            throw SkillImportException("未识别到Skill主体文件（skill.md / main.md）")
        }

        return roots.mapIndexed { index, root ->
            val mainPath = pickMainPath(mainByDir[root].orEmpty())
                ?: throw SkillImportException("未识别到Skill主体文件（skill.md / main.md）")
            val main = readText(entries.getValue(mainPath))
            val folderName = root.substringAfterLast('/')
            // 名称：一级目录名优先；根目录单技能用时间戳兜底。
            val name = folderName.ifBlank { defaultSkillName(index) }
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
                name = name,
                description = "",
                mainMarkdown = main,
                references = references
            )
        }
    }

    fun writeSingle(output: OutputStream, skill: Skill) {
        ZipOutputStream(output).use { zip ->
            zip.putText(MAIN_FILE_NAME, skill.mainMarkdown)
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

    private fun defaultSkillName(index: Int): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(System.currentTimeMillis() + index))
        return "导入Skill_$stamp"
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
