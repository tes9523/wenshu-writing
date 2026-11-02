package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wenshu.writing.data.local.entity.ChapterTaskEntity
import com.wenshu.writing.data.local.entity.OutlineEntity
import com.wenshu.writing.data.local.entity.PipelineRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineDao {

    @Query("SELECT * FROM outlines WHERE novelId = :novelId ORDER BY id ASC")
    fun observeOutlines(novelId: Long): Flow<List<OutlineEntity>>

    @Query("SELECT * FROM outlines WHERE novelId = :novelId ORDER BY id ASC")
    suspend fun getOutlines(novelId: Long): List<OutlineEntity>

    @Insert
    suspend fun insertOutlines(entities: List<OutlineEntity>)

    @Query("DELETE FROM outlines WHERE novelId = :novelId")
    suspend fun deleteOutlines(novelId: Long)

    @Query("SELECT * FROM chapter_tasks WHERE novelId = :novelId ORDER BY orderIndex ASC, id ASC")
    fun observeTasks(novelId: Long): Flow<List<ChapterTaskEntity>>

    @Query("SELECT * FROM chapter_tasks WHERE novelId = :novelId ORDER BY orderIndex ASC, id ASC")
    suspend fun getTasks(novelId: Long): List<ChapterTaskEntity>

    @Insert
    suspend fun insertTasks(entities: List<ChapterTaskEntity>)

    @Query("DELETE FROM chapter_tasks WHERE novelId = :novelId")
    suspend fun deleteTasks(novelId: Long)

    @Query("SELECT * FROM pipeline_runs WHERE novelId = :novelId ORDER BY id DESC LIMIT 1")
    suspend fun getRun(novelId: Long): PipelineRunEntity?

    @Insert
    suspend fun insertRun(entity: PipelineRunEntity): Long

    @Update
    suspend fun updateRun(entity: PipelineRunEntity)

    @Query("DELETE FROM pipeline_runs WHERE novelId = :novelId")
    suspend fun deleteRun(novelId: Long)
}
