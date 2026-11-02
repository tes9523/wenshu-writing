package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wenshu.writing.data.local.entity.ChapterTaskEntity
import com.wenshu.writing.data.local.entity.PipelineRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineDao {

    @Query("SELECT * FROM chapter_tasks WHERE novelId = :novelId ORDER BY orderIndex ASC")
    fun observeTasks(novelId: Long): Flow<List<ChapterTaskEntity>>

    @Query("SELECT * FROM chapter_tasks WHERE novelId = :novelId ORDER BY orderIndex ASC")
    suspend fun getTasks(novelId: Long): List<ChapterTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(entity: ChapterTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(entities: List<ChapterTaskEntity>)

    @Query("DELETE FROM chapter_tasks WHERE novelId = :novelId")
    suspend fun deleteTasks(novelId: Long)

    @Query("SELECT * FROM pipeline_runs WHERE novelId = :novelId ORDER BY id DESC LIMIT 1")
    suspend fun getRun(novelId: Long): PipelineRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(entity: PipelineRunEntity): Long

    @Update
    suspend fun updateRun(entity: PipelineRunEntity)

    @Query("DELETE FROM pipeline_runs WHERE novelId = :novelId")
    suspend fun deleteRun(novelId: Long)
}
