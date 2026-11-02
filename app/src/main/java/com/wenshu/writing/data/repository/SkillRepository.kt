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

    /** 首次启动时把 assets/skills 下的内置技能写入数据库；已存在内置技能时不覆盖用户数据。 */
    suspend fun ensureBuiltInSkills() {
        if (skillDao.countBuiltIn() > 0) return
        val builtIns = runCatching { loadBuiltInSkills() }.getOrDefault(emptyList())
        if (builtIns.isNotEmpty()) {
            withContext(Dispatchers.IO) { skillDao.insertAll(builtIns) }
        }
    }

    suspend fun getAll(): List<Skill> = withContext(Dispatchers.IO) {
        skillDao.getAll().map { it.toModel() }
    }

    suspend fun getById(id: Long): Skill? = withContext(Dispatchers.IO) {
        skillDao.getById(id)?.toModel()
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

    /** 导入 zip 包，返回成功导入的技能数量。 */
    suspend fun importZip(input: InputStream): Int = withContext(Dispatchers.IO) {
        val imported = SkillArchive.readZip(input)
        val now = System.currentTimeMillis()
        val usedNames = skillDao.getAll().map { it.name }.toMutableSet()
        val entities = imported.mapIndexed { index, item ->
            val name = uniqueName(item.name, usedNames)
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
        if (entities.isNotEmpty()) skillDao.insertAll(entities)
        entities.size
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
            val mainPath = "$root/$dir/main.md"
            val main = runCatching {
                context.assets.open(mainPath).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapIndexedNotNull null
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
                name = frontMatter.first ?: dir,
                description = frontMatter.second.orEmpty(),
                mainMarkdown = main,
                references = references,
                isBuiltIn = true,
                enabled = true,
                createdAt = now + index,
                updatedAt = now + index
            ).toEntity()
        }
    }

    private fun regexFrontMatter(markdown: String): Pair<String?, String?> {
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
}
