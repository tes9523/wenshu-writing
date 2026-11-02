package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wenshu.writing.data.local.entity.SkillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {

    @Query("SELECT * FROM skills ORDER BY isBuiltIn DESC, createdAt ASC, id ASC")
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills ORDER BY isBuiltIn DESC, createdAt ASC, id ASC")
    suspend fun getAll(): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE id = :id")
    suspend fun getById(id: Long): SkillEntity?

    @Query("SELECT COUNT(*) FROM skills WHERE isBuiltIn = 1")
    suspend fun countBuiltIn(): Int

    @Insert
    suspend fun insert(entity: SkillEntity): Long

    @Insert
    suspend fun insertAll(entities: List<SkillEntity>)

    @Update
    suspend fun update(entity: SkillEntity)

    @Query("UPDATE skills SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM skills WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteById(id: Long)
}
