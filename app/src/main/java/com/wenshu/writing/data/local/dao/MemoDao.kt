package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wenshu.writing.data.local.entity.MemoNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {

    @Query("SELECT * FROM memo_notes ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<MemoNoteEntity>>

    @Query("SELECT * FROM memo_notes WHERE id = :id")
    fun observeById(id: Long): Flow<MemoNoteEntity?>

    @Query("SELECT * FROM memo_notes WHERE id = :id")
    suspend fun getById(id: Long): MemoNoteEntity?

    @Insert
    suspend fun insert(entity: MemoNoteEntity): Long

    @Update
    suspend fun update(entity: MemoNoteEntity)

    @Query("DELETE FROM memo_notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
