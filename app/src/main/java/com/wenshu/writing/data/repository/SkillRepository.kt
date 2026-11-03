package com.wenshu.writing.data.repository

import android.content.Context
import com.wenshu.writing.data.local.dao.SkillDao
import com.wenshu.writing.data.local.entity.SkillEntity
import com.wenshu.writing.data.local.entity.toEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.domain.model.SkillReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class SkillRepository(
    private val skillDao: SkillDao,
    private val context: Context
) {

    val skills: Flow<List<Skill>> = skillDao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    /**
     * 1.0.8 起内置技能完全由 assets/skills 决定：
     * - 删除 assets 中已不存在的旧内置技能（如旧版 novel-writer / skill-creator / 流水线）；
     * - 已存在的同名内置技能覆盖为最新内容（保留用户的启用/停用状态）；
     * - 用户自定义技能与导入技能不受影响。
     */
    suspend fun ensureBuiltInSkills() {
        val builtIns = runCatching { loadBuiltInSkills() }.getOrDefault(emptyList())
        if (builtIns.isEmpty()) return
        val existing = runCatching { skillDao.getAll() }.getOrDefault(emptyList())
        val dbBuiltIns = existing.filter { it.isBuiltIn }
        val newNames = builtIns.map { it.name }.toSet()
        withContext(Dispatchers.IO) {
            // 移除 assets 中已删除的旧内置技能
            dbBuiltIns.filter { it.name !in newNames }.forEach { skillDao.deleteAnyById(it.id) }
            builtIns.forEach { builtIn ->
                val sameName = dbBuiltIns.firstOrNull { it.name == builtIn.name }
                if (sameName != null) {
                    skillDao.update(
                        builtIn.copy(
                            id = sameName.id,
                            enabled = sameName.enabled,
                            createdAt = sameName.createdAt
                        )
                    )
                } else {
                    skillDao.insert(builtIn)
                }
            }
        }
    }

    /** 按名称精确查找技能（agent 工具 skill_lookup / update_skill 使用）。 */
    suspend fun findByName(name: String): Skill? = withContext(Dispatchers.IO) {
        skillDao.getByName(name.trim())?.toModel()
            ?: skillDao.getAll().firstOrNull {
                it.name.equals(name.trim(), ignoreCase = true) ||
                    it.name.substringBefore(" (").equals(name.trim(), ignoreCase = true)
            }?.toModel()
    }

    suspend fun getAll(): List<Skill> = withContext(Dispatchers.IO) {
        skillDao.getAll().map { it.toModel() }
    }

    suspend fun getById(id: Long): Skill? = withContext(Dispatchers.IO) {
        skillDao.getById(id)?.toModel()
    }

    suspend fun findByPipeline(): Skill? = withContext(Dispatchers.IO) {
        skillDao.getByPipeline()?.toModel()
    }

    suspend fun create(
        name: String,
        description: String,
        mainMarkdown: String,
        references: List<SkillReference>,
        isBuiltIn: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        skillDao.insert(
            Skill(
                name = uniqueName(name),
                description = description,
                mainMarkdown = mainMarkdown,
                references = references,
                isBuiltIn = isBuiltIn,
                enabled = true,
                createdAt = now,
                updatedAt = now
            ).toEntity()
        )
    }

    /** 保存自定义技能；内置技能只允许修改内容，不允许改动内置标记。 */
    suspend fun update(skill: Skill) = withContext(Dispatchers.IO) {
        val existing = skillDao.getById(skill.id) ?: return@withContext
        skillDao.update(
            skill.copy(
                isBuiltIn = existing.isBuiltIn,
                isPipeline = existing.isPipeline,
                enabled = skill.enabled,
                createdAt = if (skill.createdAt > 0L) skill.createdAt else existing.createdAt,
                updatedAt = System.currentTimeMillis()
            ).toEntity()
        )
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        skillDao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        skillDao.deleteById(id)
    }

    /** 解析 zip 包，不写库；供 UI 先检测重名并弹窗确认。 */
    suspend fun parseImport(input: InputStream): List<SkillArchive.ImportedSkill> =
        withContext(Dispatchers.IO) { SkillArchive.readZip(input) }

    suspend fun findConflicts(items: List<SkillArchive.ImportedSkill>): List<String> =
        withContext(Dispatchers.IO) {
            val existingNames = skillDao.getAll().map { it.name }.toSet()
            items.map { it.name }.filter { it in existingNames }.distinct()
        }

    /**
     * 写入已解析的技能。overwrite=true 时同名技能覆盖内容；否则自动追加序号避免重名。
     * 返回导入数量。
     */
    suspend fun importSkills(
        items: List<SkillArchive.ImportedSkill>,
        overwrite: Boolean
    ): Int = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        val usedNames = skillDao.getAll().map { it.name }.toMutableSet()
        val entities = items.mapIndexed { index, item ->
            val referencesJson = Skill(
                name = item.name,
                description = item.description,
                mainMarkdown = item.mainMarkdown,
                references = item.references
            ).toEntity().referencesJson
            val existing = if (overwrite) skillDao.getByName(item.name) else null
            if (existing != null) {
                skillDao.update(
                    existing.copy(
                        description = item.description,
                        mainMarkdown = item.mainMarkdown,
                        referencesJson = referencesJson,
                        updatedAt = now + index
                    )
                )
                null
            } else {
                val name = if (overwrite) item.name else uniqueName(item.name, usedNames)
                usedNames.add(name)
                Skill(
                    name = name,
                    description = item.description,
                    mainMarkdown = item.mainMarkdown,
                    references = item.references,
                    isBuiltIn = false,
                    enabled = true,
                    createdAt = now + index,
                    updatedAt = now + index
                ).toEntity()
            }
        }.filterNotNull()
        if (entities.isNotEmpty()) skillDao.insertAll(entities)
        items.size
    }

    suspend fun exportSingle(output: OutputStream, skill: Skill) = withContext(Dispatchers.IO) {
        SkillArchive.writeSingle(output, skill)
    }

    suspend fun exportBatch(output: OutputStream, skills: List<Skill>) =
        withContext(Dispatchers.IO) {
            SkillArchive.writeBatch(output, skills)
        }

    private suspend fun uniqueName(name: String): String {
        return uniqueName(name, skillDao.getAll().map { it.name }.toMutableSet())
    }

    private fun uniqueName(name: String, existing: Set<String>): String {
        val base = name.trim().ifBlank { "未命名技能" }
        if (base !in existing) return base
        var index = 2
        var candidate = "$base ($index)"
        while (candidate in existing) {
            index++
            candidate = "$base ($index)"
        }
        return candidate
    }

    private fun loadBuiltInSkills(): List<SkillEntity> {
        val root = "skills"
        val dirs = context.assets.list(root).orEmpty().filter { it.isNotBlank() }
        val now = System.currentTimeMillis()
        return dirs.mapIndexedNotNull { index, dir ->
            // 兼容 SKILL.md（1.0.8 内置技能）与历史 main.md / main。
            val main = listOf("SKILL.md", "main.md", "main")
                .firstNotNullOfOrNull { fileName ->
                    runCatching {
                        context.assets.open("$root/$dir/$fileName")
                            .bufferedReader()
                            .use { it.readText() }
                    }.getOrNull()
                } ?: return@mapIndexedNotNull null
            val references = context.assets.list("$root/$dir/references")
                .orEmpty()
                .filter {
                    it.endsWith(".md", ignoreCase = true) ||
                        it.endsWith(".txt", ignoreCase = true)
                }
                .mapNotNull { fileName ->
                    runCatching {
                        val content = context.assets
                            .open("$root/$dir/references/$fileName")
                            .bufferedReader()
                            .use { it.readText() }
                        SkillReference(fileName, content)
                    }.getOrNull()
                }
            val frontMatter = regexFrontMatter(main)
            Skill(
                name = frontMatter.name ?: dir,
                description = frontMatter.description.orEmpty(),
                mainMarkdown = main,
                references = references,
                isBuiltIn = true,
                enabled = true,
                isPipeline = frontMatter.pipeline,
                createdAt = now + index,
                updatedAt = now + index
            ).toEntity()
        }
    }

    private data class FrontMatter(
        val name: String?,
        val description: String?,
        val pipeline: Boolean
    )

    private fun regexFrontMatter(markdown: String): FrontMatter {
        val text = markdown.removePrefix("\uFEFF").trimStart()
        if (!text.startsWith("---")) return FrontMatter(null, null, false)
        val end = text.indexOf("\n---", startIndex = 3)
        if (end < 0) return FrontMatter(null, null, false)
        val body = text.substring(3, end)
        val name = body.lineSequence()
            .firstOrNull { it.trimStart().startsWith("name:") }
            ?.substringAfter(":")?.trim()?.trim('"', '\'')
        val description = body.lineSequence()
            .firstOrNull { it.trimStart().startsWith("description:") }
            ?.substringAfter(":")?.trim()?.trim('"', '\'')
        val pipeline = body.lineSequence()
            .firstOrNull { it.trimStart().startsWith("pipeline:") }
            ?.substringAfter(":")?.trim()?.equals("true", ignoreCase = true) ?: false
        return FrontMatter(
            name = name?.takeIf { it.isNotBlank() },
            description = description?.takeIf { it.isNotBlank() },
            pipeline = pipeline
        )
    }
}
