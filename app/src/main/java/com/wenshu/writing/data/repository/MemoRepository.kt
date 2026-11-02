package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.MemoDao
import com.wenshu.writing.data.local.entity.toEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.MemoNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemoRepository(
    private val memoDao: MemoDao
) {

    val notes: Flow<List<MemoNote>> = memoDao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    fun observeNote(id: Long): Flow<MemoNote?> = memoDao.observeById(id).map { it?.toModel() }

    suspend fun create(title: String, content: String = ""): Long {
        val now = System.currentTimeMillis()
        return memoDao.insert(
            MemoNote(
                title = title,
                content = content,
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
}
