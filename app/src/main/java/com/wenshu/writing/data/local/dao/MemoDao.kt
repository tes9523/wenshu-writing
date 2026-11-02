package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wenshu.writing.data.local.entity.MemoNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {

    @Query(
        "SELECT * FROM memo_notes WHERE (`bookId` = :bookId OR (`bookId` IS NULL AND :bookId IS NULL)) " +
            "ORDER BY updatedAt DESC, id DESC"
    )
    fun observeAll(bookId: Long?): Flow<List<MemoNoteEntity>>

    @Query(
        "SELECT * FROM memo_notes WHERE (`bookId` = :bookId OR (`bookId` IS NULL AND :bookId IS NULL)) " +
            "ORDER BY updatedAt DESC, id DESC"
    )
    suspend fun getByBook(bookId: Long?): List<MemoNoteEntity>

    @Query("SELECT * FROM memo_notes WHERE id = :id")
    fun observeById(id: Long): Flow<MemoNoteEntity?>

    @Query("SELECT * FROM memo_notes WHERE id = :id")
    suspend fun getById(id: Long): MemoNoteEntity?

    @Insert
    suspend fun insert(entity: MemoNoteEntity): Long

    @Insert
    suspend fun insertAll(entities: List<MemoNoteEntity>)

    @Update
    suspend fun update(entity: MemoNoteEntity)

    @Query("DELETE FROM memo_notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
