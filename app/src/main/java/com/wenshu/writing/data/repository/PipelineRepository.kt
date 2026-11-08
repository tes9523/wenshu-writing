package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.PipelineDao
import com.wenshu.writing.data.local.entity.ChapterTaskEntity
import com.wenshu.writing.data.local.entity.PipelineRunEntity
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
    /** 大纲读写走 SettingRepository，自动同步到 wenshu/setting 目录。 */
    private val settingRepository: SettingRepository
) {

    fun observeOutlines(bookId: Long): Flow<List<Setting>> =
        settingRepository.observeByCategory(bookId, SettingCategories.OUTLINE)

    fun observeTasks(novelId: Long): Flow<List<ChapterTask>> =
        pipelineDao.observeTasks(novelId).map { list -> list.map { it.toModel() } }

    suspend fun getOutlines(bookId: Long): List<Setting> = withContext(Dispatchers.IO) {
        settingRepository.getByCategory(bookId, SettingCategories.OUTLINE)
    }

    suspend fun getTasks(novelId: Long): List<ChapterTask> = withContext(Dispatchers.IO) {
        pipelineDao.getTasks(novelId).map { it.toModel() }
    }

    suspend fun replaceOutlines(bookId: Long, outlines: List<Setting>) = withContext(Dispatchers.IO) {
        settingRepository.replaceByCategory(
            bookId = bookId,
            category = SettingCategories.OUTLINE,
            items = outlines.map { it.copy(bookId = bookId, category = SettingCategories.OUTLINE) }
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
