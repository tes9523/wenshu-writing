package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.PipelineDao
import com.wenshu.writing.data.local.dao.SettingDao
import com.wenshu.writing.data.local.entity.ChapterTaskEntity
import com.wenshu.writing.data.local.entity.PipelineRunEntity
import com.wenshu.writing.data.local.entity.SettingEntity
import com.wenshu.writing.data.local.entity.toEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.ChapterTask
import com.wenshu.writing.domain.model.PipelineRun
import com.wenshu.writing.domain.model.Setting
import com.wenshu.writing.domain.model.SettingCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PipelineRepository(
    private val pipelineDao: PipelineDao,
    private val settingDao: SettingDao
) {

    fun observeOutlines(bookId: Long): Flow<List<Setting>> =
        settingDao.observeByCategory(bookId, SettingCategories.OUTLINE).map { it.map { e -> e.toModel() } }

    fun observeTasks(novelId: Long): Flow<List<ChapterTask>> =
        pipelineDao.observeTasks(novelId).map { list -> list.map { it.toModel() } }

    suspend fun getOutlines(bookId: Long): List<Setting> = withContext(Dispatchers.IO) {
        settingDao.getByCategory(bookId, SettingCategories.OUTLINE).map { it.toModel() }
    }

    suspend fun getTasks(novelId: Long): List<ChapterTask> = withContext(Dispatchers.IO) {
        pipelineDao.getTasks(novelId).map { it.toModel() }
    }

    suspend fun replaceOutlines(bookId: Long, outlines: List<Setting>) = withContext(Dispatchers.IO) {
        settingDao.deleteByCategory(bookId, SettingCategories.OUTLINE)
        if (outlines.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        settingDao.insertAll(
            outlines.map { outline ->
                SettingEntity(
                    bookId = bookId,
                    category = SettingCategories.OUTLINE,
                    title = outline.title,
                    content = outline.content,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
    }

    suspend fun replaceTasks(novelId: Long, tasks: List<ChapterTask>) = withContext(Dispatchers.IO) {
        pipelineDao.deleteTasks(novelId)
        if (tasks.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        pipelineDao.insertTasks(
            tasks.mapIndexed { index, task ->
                ChapterTaskEntity(
                    novelId = novelId,
                    orderIndex = index,
                    title = task.title,
                    goal = task.goal,
                    conflict = task.conflict,
                    infoIncrement = task.infoIncrement,
                    payoff = task.payoff,
                    hook = task.hook,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
    }

    suspend fun getRun(novelId: Long): PipelineRun? = withContext(Dispatchers.IO) {
        pipelineDao.getRun(novelId)?.toModel()
    }

    suspend fun saveRun(run: PipelineRun) = withContext(Dispatchers.IO) {
        val entity = run.toEntity()
        if (entity.id == 0L) {
            pipelineDao.insertRun(entity)
        } else {
            pipelineDao.updateRun(entity)
        }
    }

    suspend fun deleteRun(novelId: Long) = withContext(Dispatchers.IO) {
        pipelineDao.deleteRun(novelId)
    }
}
