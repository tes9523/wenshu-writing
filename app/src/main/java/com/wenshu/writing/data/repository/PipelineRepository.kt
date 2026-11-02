package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.PipelineDao
import com.wenshu.writing.data.local.entity.ChapterTaskEntity
import com.wenshu.writing.data.local.entity.OutlineEntity
import com.wenshu.writing.data.local.entity.PipelineRunEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.ChapterTask
import com.wenshu.writing.domain.model.Outline
import com.wenshu.writing.domain.model.PipelineRun
import com.wenshu.writing.domain.model.PipelineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PipelineRepository(
    private val pipelineDao: PipelineDao
) {

    fun observeOutlines(novelId: Long): Flow<List<Outline>> =
        pipelineDao.observeOutlines(novelId).map { list -> list.map { it.toModel() } }

    fun observeTasks(novelId: Long): Flow<List<ChapterTask>> =
        pipelineDao.observeTasks(novelId).map { list -> list.map { it.toModel() } }

    suspend fun getOutlines(novelId: Long): List<Outline> = withContext(Dispatchers.IO) {
        pipelineDao.getOutlines(novelId).map { it.toModel() }
    }

    suspend fun getTasks(novelId: Long): List<ChapterTask> = withContext(Dispatchers.IO) {
        pipelineDao.getTasks(novelId).map { it.toModel() }
    }

    suspend fun replaceOutlines(novelId: Long, outlines: List<Outline>) = withContext(Dispatchers.IO) {
        pipelineDao.deleteOutlines(novelId)
        if (outlines.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        pipelineDao.insertOutlines(
            outlines.mapIndexed { index, outline ->
                OutlineEntity(
                    novelId = novelId,
                    title = outline.title,
                    content = outline.content,
                    kind = outline.kind,
                    createdAt = now + index,
                    updatedAt = now + index
                )
            }
        )
    }

    /** 追加一条大纲，保留本书原有大纲内容。 */
    suspend fun appendOutline(
        novelId: Long,
        title: String,
        content: String,
        kind: String = "outline"
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        pipelineDao.insertOutline(
            OutlineEntity(
                novelId = novelId,
                title = title,
                content = content,
                kind = kind,
                createdAt = now,
                updatedAt = now
            )
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
                    createdAt = now + index,
                    updatedAt = now + index
                )
            }
        )
    }

    suspend fun getRun(novelId: Long): PipelineRun? = withContext(Dispatchers.IO) {
        pipelineDao.getRun(novelId)?.toModel()
    }

    /** 创建或更新断点，返回最新断点。 */
    suspend fun saveRun(
        novelId: Long,
        skillId: Long,
        stage: Int,
        status: PipelineStatus,
        inputJson: String,
        outputJson: String
    ): PipelineRun = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = pipelineDao.getRun(novelId)
        if (existing == null) {
            val id = pipelineDao.insertRun(
                PipelineRunEntity(
                    novelId = novelId,
                    skillId = skillId,
                    stage = stage,
                    status = status.name,
                    inputJson = inputJson,
                    outputJson = outputJson,
                    createdAt = now,
                    updatedAt = now
                )
            )
            PipelineRun(
                id = id,
                novelId = novelId,
                skillId = skillId,
                stage = stage,
                status = status,
                inputJson = inputJson,
                outputJson = outputJson,
                createdAt = now,
                updatedAt = now
            )
        } else {
            val updated = existing.copy(
                skillId = skillId,
                stage = stage,
                status = status.name,
                inputJson = inputJson,
                outputJson = outputJson,
                updatedAt = now
            )
            pipelineDao.updateRun(updated)
            updated.toModel()
        }
    }

    suspend fun deleteRun(novelId: Long) = withContext(Dispatchers.IO) {
        pipelineDao.deleteRun(novelId)
    }
}
