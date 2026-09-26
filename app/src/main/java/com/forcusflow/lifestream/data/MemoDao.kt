package com.forcusflow.lifestream.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos ORDER BY isPinned DESC, updatedAt DESC, id DESC")
    fun getAllFlow(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getById(id: Long): MemoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memo: MemoEntity): Long

    @Update
    suspend fun update(memo: MemoEntity)

    @Delete
    suspend fun delete(memo: MemoEntity)

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memos")
    suspend fun deleteAll()
}
