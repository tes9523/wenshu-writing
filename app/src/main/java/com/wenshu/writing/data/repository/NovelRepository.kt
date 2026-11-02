package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.ChapterDao
import com.wenshu.writing.data.local.dao.NovelDao
import com.wenshu.writing.data.local.entity.ChapterEntity
import com.wenshu.writing.data.local.entity.NovelEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.Novel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NovelRepository(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao
) {

    val novels: Flow<List<Novel>> = novelDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeNovel(novelId: Long): Flow<Novel?> =
        novelDao.observeById(novelId).map { it?.toModel() }

    fun observeChapters(novelId: Long): Flow<List<Chapter>> =
        chapterDao.observeByNovel(novelId).map { list -> list.map { it.toModel() } }

    fun observeChapter(chapterId: Long): Flow<Chapter?> =
        chapterDao.observeById(chapterId).map { it?.toModel() }

    suspend fun getNovel(novelId: Long): Novel? =
        novelDao.getById(novelId)?.toModel()

    suspend fun getChapter(chapterId: Long): Chapter? =
        chapterDao.getById(chapterId)?.toModel()

    suspend fun createNovel(title: String, description: String): Long {
        val now = System.currentTimeMillis()
        return novelDao.insert(
            NovelEntity(
                title = title.trim(),
                description = description.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteNovel(novelId: Long) {
        novelDao.deleteById(novelId)
    }

    suspend fun createChapter(novelId: Long, title: String): Long {
        val now = System.currentTimeMillis()
        val orderIndex = chapterDao.maxOrderIndex(novelId) + 1
        val chapterNumber = chapterDao.countByNovel(novelId) + 1
        val resolvedTitle = title.trim().ifBlank { "第 $chapterNumber 章" }
        val id = chapterDao.insert(
            ChapterEntity(
                novelId = novelId,
                title = resolvedTitle,
                orderIndex = orderIndex,
                createdAt = now,
                updatedAt = now
            )
        )
        novelDao.touch(novelId, now)
        return id
    }

    suspend fun saveChapter(chapterId: Long, title: String, content: String) {
        val existing = chapterDao.getById(chapterId) ?: return
        val now = System.currentTimeMillis()
        chapterDao.update(
            existing.copy(
                title = title.trim().ifBlank { existing.title },
                content = content,
                updatedAt = now
            )
        )
        novelDao.touch(existing.novelId, now)
    }

    suspend fun deleteChapter(chapterId: Long) {
        val existing = chapterDao.getById(chapterId) ?: return
        chapterDao.deleteById(chapterId)
        novelDao.touch(existing.novelId, System.currentTimeMillis())
    }

    /** 将 AI 生成内容追加到章节末尾，返回章节标题 */
    suspend fun appendToChapter(chapterId: Long, content: String): String? {
        val existing = chapterDao.getById(chapterId) ?: return null
        val now = System.currentTimeMillis()
        val addition = if (existing.content.isBlank()) content else "\n\n$content"
        chapterDao.update(
            existing.copy(content = existing.content + addition, updatedAt = now)
        )
        novelDao.touch(existing.novelId, now)
        return existing.title
    }
}
