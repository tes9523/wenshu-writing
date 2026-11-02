package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wenshu.writing.data.local.entity.NovelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

    @Query("SELECT * FROM novels ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE id = :id")
    fun observeById(id: Long): Flow<NovelEntity?>

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun getById(id: Long): NovelEntity?

    @Insert
    suspend fun insert(entity: NovelEntity): Long

    @Query("UPDATE novels SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM novels WHERE id = :id")
    suspend fun deleteById(id: Long)
}
