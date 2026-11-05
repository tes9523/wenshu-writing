package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.ChapterDao
import com.wenshu.writing.data.local.dao.MemoDao
import com.wenshu.writing.data.local.dao.NovelDao
import com.wenshu.writing.data.local.dao.SettingDao
import com.wenshu.writing.data.local.entity.ChapterEntity
import com.wenshu.writing.data.local.entity.NovelEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.Novel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NovelRepository(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val settingDao: SettingDao,
    private val memoDao: MemoDao
) {

    /** 书稿列表（不含回收站中的书稿）。 */
    val novels: Flow<List<Novel>> = novelDao.observeAll().map { list -> list.map { it.toModel() } }

    /** 回收站中的书稿（按移入时间倒序）。 */
    val trash: Flow<List<Novel>> = novelDao.observeTrash().map { list -> list.map { it.toModel() } }

    fun observeNovel(novelId: Long): Flow<Novel?> =
        novelDao.observeById(novelId).map { it?.toModel() }

    fun observeChapters(novelId: Long): Flow<List<Chapter>> =
        chapterDao.observeByNovel(novelId).map { list -> list.map { it.toModel() } }

    fun observeChapter(chapterId: Long): Flow<Chapter?> =
        chapterDao.observeById(chapterId).map { it?.toModel() }

    suspend fun getNovel(novelId: Long): Novel? =
        novelDao.getById(novelId)?.toModel()

    suspend fun findNovelByTitle(title: String): Novel? =
        novelDao.getByTitle(title.trim())?.toModel()

    suspend fun getChapter(chapterId: Long): Chapter? =
        chapterDao.getById(chapterId)?.toModel()

    suspend fun getChapters(novelId: Long): List<Chapter> =
        chapterDao.getByNovel(novelId).map { it.toModel() }

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

    /** 移入回收站（软删除），设定、章节、备忘录保留，可随时恢复。 */
    suspend fun moveToTrash(novelId: Long) {
        novelDao.softDelete(novelId, System.currentTimeMillis())
    }

    /** 从回收站恢复书稿。 */
    suspend fun restoreFromTrash(novelId: Long) {
        novelDao.restore(novelId)
    }

    /** 回收站中的永久删除：连同关联的设定、章节、备忘录一并清除。 */
    suspend fun deleteNovelPermanently(novelId: Long) {
        settingDao.deleteByBookId(novelId)
        memoDao.deleteByBookId(novelId)
        novelDao.deleteById(novelId)
    }

    suspend fun deleteNovel(novelId: Long) {
        novelDao.deleteById(novelId)
    }

    /** 设置或清除书稿自定义封面；传 null 恢复默认封面。 */
    suspend fun setCover(novelId: Long, coverPath: String?) {
        novelDao.updateCover(novelId, coverPath)
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
