package com.wenshu.writing.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wenshu.writing.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {

    @Query("SELECT * FROM settings WHERE (bookId = :bookId OR (bookId IS NULL AND :bookId IS NULL)) ORDER BY updatedAt DESC")
    fun observeAll(bookId: Long?): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings WHERE (bookId = :bookId OR (bookId IS NULL AND :bookId IS NULL)) AND category = :category ORDER BY updatedAt DESC")
    fun observeByCategory(bookId: Long?, category: String): Flow<List<SettingEntity>>

    @Query("SELECT DISTINCT category FROM settings WHERE (bookId = :bookId OR (bookId IS NULL AND :bookId IS NULL)) ORDER BY category")
    fun observeCategories(bookId: Long?): Flow<List<String>>

    @Query("SELECT * FROM settings WHERE (bookId = :bookId OR (bookId IS NULL AND :bookId IS NULL)) ORDER BY updatedAt DESC")
    suspend fun getAll(bookId: Long?): List<SettingEntity>

    @Query("SELECT * FROM settings WHERE (bookId = :bookId OR (bookId IS NULL AND :bookId IS NULL)) AND category = :category ORDER BY updatedAt DESC")
    suspend fun getByCategory(bookId: Long?, category: String): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SettingEntity): Long

    @Update
    suspend fun update(entity: SettingEntity)

    @Delete
    suspend fun delete(entity: SettingEntity)

    @Query("DELETE FROM settings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM settings WHERE (bookId = :bookId OR (bookId IS NULL AND :bookId IS NULL)) AND category = :category")
    suspend fun deleteByCategory(bookId: Long?, category: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SettingEntity>)
}
