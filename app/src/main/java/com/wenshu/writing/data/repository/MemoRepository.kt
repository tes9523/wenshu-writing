package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.MemoDao
import com.wenshu.writing.data.local.entity.MemoNoteEntity
import com.wenshu.writing.data.local.entity.toEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.MemoNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MemoRepository(
    private val memoDao: MemoDao
) {

    fun observeNotes(bookId: Long?): Flow<List<MemoNote>> =
        memoDao.observeAll(bookId).map { list -> list.map { it.toModel() } }

    fun observeNote(id: Long): Flow<MemoNote?> = memoDao.observeById(id).map { it?.toModel() }

    suspend fun getNotes(bookId: Long?): List<MemoNote> = withContext(Dispatchers.IO) {
        memoDao.getByBook(bookId).map { it.toModel() }
    }

    suspend fun create(title: String, content: String = "", bookId: Long? = null): Long {
        val now = System.currentTimeMillis()
        return memoDao.insert(
            MemoNote(
                title = title,
                content = content,
                bookId = bookId,
                createdAt = now,
                updatedAt = now
            ).toEntity()
        )
    }

    suspend fun update(note: MemoNote) {
        memoDao.update(note.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun delete(id: Long) {
        memoDao.deleteById(id)
    }

    /** 供整书导入使用：批量写入指定书稿。 */
    suspend fun addNotes(notes: List<MemoNote>, bookId: Long?) {
        if (notes.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = notes.mapIndexed { index, note ->
            MemoNoteEntity(
                title = note.title.trim(),
                content = note.content,
                bookId = bookId,
                createdAt = now + index,
                updatedAt = now + index
            )
        }
        memoDao.insertAll(entities)
    }
}
