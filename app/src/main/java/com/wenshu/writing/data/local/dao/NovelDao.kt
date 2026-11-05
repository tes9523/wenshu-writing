package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wenshu.writing.data.local.entity.NovelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

    @Query("SELECT * FROM novels WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NovelEntity>>

    /** 回收站中的书稿（按移入时间倒序）。 */
    @Query("SELECT * FROM novels WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<NovelEntity>>

    /** 移入回收站（软删除）。 */
    @Query("UPDATE novels SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    /** 从回收站恢复。 */
    @Query("UPDATE novels SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM novels WHERE id = :id")
    fun observeById(id: Long): Flow<NovelEntity?>

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun getById(id: Long): NovelEntity?

    @Query("SELECT * FROM novels WHERE title = :title AND deletedAt IS NULL LIMIT 1")
    suspend fun getByTitle(title: String): NovelEntity?

    @Insert
    suspend fun insert(entity: NovelEntity): Long

    @Query("UPDATE novels SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("UPDATE novels SET coverPath = :coverPath WHERE id = :id")
    suspend fun updateCover(id: Long, coverPath: String?)

    @Query("DELETE FROM novels WHERE id = :id")
    suspend fun deleteById(id: Long)
}
