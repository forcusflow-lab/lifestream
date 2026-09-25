package com.forcusflow.lifestream.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineItemDao {
    @Query("SELECT * FROM timeline_items ORDER BY CASE WHEN scheduledAt IS NOT NULL THEN scheduledAt ELSE completedAt END ASC, id ASC")
    fun getAllFlow(): Flow<List<TimelineItemEntity>>

    @Query("SELECT * FROM timeline_items")
    suspend fun getAll(): List<TimelineItemEntity>

    @Query("SELECT * FROM timeline_items WHERE id = :id")
    suspend fun getById(id: Long): TimelineItemEntity?

    @Query("SELECT * FROM timeline_items WHERE scheduledAt IS NULL AND isDone = 0 ORDER BY id DESC")
    fun getAnytimePendingFlow(): Flow<List<TimelineItemEntity>>

    @Query("SELECT * FROM timeline_items WHERE (scheduledAt BETWEEN :start AND :end) OR (completedAt BETWEEN :start AND :end) ORDER BY COALESCE(scheduledAt, completedAt) ASC")
    fun getItemsBetweenFlow(start: Long, end: Long): Flow<List<TimelineItemEntity>>

    @Query("SELECT * FROM timeline_items WHERE title LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY COALESCE(completedAt, scheduledAt, id) DESC")
    fun searchFlow(query: String): Flow<List<TimelineItemEntity>>

    @Query("SELECT * FROM timeline_items WHERE templateId = :templateId AND completedAt IS NOT NULL AND completedAt BETWEEN :start AND :end")
    suspend fun getCompletedByTemplateAndRange(templateId: Long, start: Long, end: Long): List<TimelineItemEntity>

    @Query("SELECT * FROM timeline_items WHERE templateId = :templateId AND completedAt IS NOT NULL ORDER BY completedAt DESC")
    suspend fun getCompletedByTemplate(templateId: Long): List<TimelineItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TimelineItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TimelineItemEntity>)

    @Update
    suspend fun update(item: TimelineItemEntity)

    @Delete
    suspend fun delete(item: TimelineItemEntity)

    @Query("DELETE FROM timeline_items")
    suspend fun deleteAll()
}
