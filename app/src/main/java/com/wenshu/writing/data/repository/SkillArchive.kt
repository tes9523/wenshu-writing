package com.wenshu.writing.data.repository

import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.domain.model.SkillReference
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Skill zip 包的读写规则。
 * 单技能包：根目录 main.md + references/。
 * 批量合集包：每个技能一个子目录，子目录内同样是 main.md + references/。
 */
object SkillArchive {

    data class ImportedSkill(
        val name: String,
        val description: String,
        val mainMarkdown: String,
        val references: List<SkillReference>
    )

    fun readZip(input: InputStream): List<ImportedSkill> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val path = entry.name.replace('\\', '/').trimStart('/')
                val isHidden = path.startsWith("__MACOSX/") ||
                    path.substringAfterLast('/').startsWith(".")
                if (!entry.isDirectory && !isHidden) {
                    entries[path] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val mainPaths = entries.keys.filter {
            it.substringAfterLast('/').equals("main.md", ignoreCase = true)
        }
        if (mainPaths.isEmpty()) {
            throw IllegalArgumentException("压缩包中未找到 main.md，请检查 Skill 包结构")
        }

        // 根目录存在 main.md 时视为单技能包，避免把嵌套目录重复识别为多个技能。
        val rootMain = mainPaths.firstOrNull { it == "main.md" }
        val targets = if (rootMain != null) listOf(rootMain) else mainPaths

        return targets.map { mainPath ->
            val root = mainPath.substringBeforeLast('/', "")
            val folderName = root.trimEnd('/').substringAfterLast('/')
            val main = entries[mainPath]?.toString(Charsets.UTF_8).orEmpty()
            val frontMatter = parseFrontMatter(main)
            val references = entries.asSequence()
                .filter { (path, _) ->
                    if (root.isEmpty()) true else path.startsWith("$root/")
                }
                .filter { (path, _) ->
                    val relative = if (root.isEmpty()) path else path.removePrefix("$root/")
                    relative.startsWith("references/") &&
                        (relative.endsWith(".md", ignoreCase = true) ||
                            relative.endsWith(".txt", ignoreCase = true))
                }
                .map { (path, bytes) ->
                    SkillReference(
                        name = path.substringAfterLast('/'),
                        content = bytes.toString(Charsets.UTF_8)
                    )
                }
                .sortedBy { it.name }
                .toList()

            ImportedSkill(
                name = folderName.ifBlank { frontMatter.first.orEmpty() }.ifBlank { "导入技能" },
                description = frontMatter.second.orEmpty(),
                mainMarkdown = main,
                references = references
            )
        }
    }

    fun writeSingle(output: OutputStream, skill: Skill) {
        ZipOutputStream(output).use { zip ->
            zip.putText("main.md", skill.mainMarkdown)
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
                zip.putText("$folder/main.md", skill.mainMarkdown)
                skill.references.forEach { reference ->
                    zip.putText(
                        "$folder/references/${safeFileName(reference.name)}",
                        reference.content
                    )
                }
            }
        }
    }

    /** 解析 main.md 顶部 YAML frontmatter 中的 name / description。 */
    private fun parseFrontMatter(markdown: String): Pair<String?, String?> {
        val text = markdown.trimStart()
        if (!text.startsWith("---")) return null to null
        val end = text.indexOf("\n---", startIndex = 3)
        if (end < 0) return null to null
        val body = text.substring(3, end)
        val name = body.lineSequence()
            .firstOrNull { it.trimStart().startsWith("name:") }
            ?.substringAfter(":")?.trim()?.trim('"', '\'')
        val description = body.lineSequence()
            .firstOrNull { it.trimStart().startsWith("description:") }
            ?.substringAfter(":")?.trim()?.trim('"', '\'')
        return name?.takeIf { it.isNotBlank() } to description?.takeIf { it.isNotBlank() }
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
