package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wenshu.writing.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY orderIndex ASC, id ASC")
    fun observeByNovel(novelId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY orderIndex ASC, id ASC")
    suspend fun getByNovel(novelId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    fun observeById(id: Long): Flow<ChapterEntity?>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getById(id: Long): ChapterEntity?

    @Insert
    suspend fun insert(entity: ChapterEntity): Long

    @Update
    suspend fun update(entity: ChapterEntity)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM chapters WHERE novelId = :novelId")
    suspend fun countByNovel(novelId: Long): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM chapters WHERE novelId = :novelId")
    suspend fun maxOrderIndex(novelId: Long): Int
}
