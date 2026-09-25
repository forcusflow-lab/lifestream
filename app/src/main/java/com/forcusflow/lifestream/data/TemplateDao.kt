package com.forcusflow.lifestream.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY displayOrder ASC, id ASC")
    fun getAllFlow(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY displayOrder ASC, id ASC")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: Long): TemplateEntity?

    @Query("DELETE FROM templates")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<TemplateEntity>)

    @Update
    suspend fun update(template: TemplateEntity)

    @Update
    suspend fun updateAll(templates: List<TemplateEntity>)

    @Delete
    suspend fun delete(template: TemplateEntity)

    @Query("UPDATE templates SET usageCount = usageCount + 1, lastCompletedAt = :completedAt WHERE id = :id")
    suspend fun recordCompletion(id: Long, completedAt: Long)
}
