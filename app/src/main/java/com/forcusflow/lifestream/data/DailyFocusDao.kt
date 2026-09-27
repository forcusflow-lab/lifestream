package com.forcusflow.lifestream.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyFocusDao {
    @Query("SELECT * FROM daily_focus WHERE date = :date LIMIT 1")
    fun getFocusByDate(date: String): Flow<DailyFocusEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(dailyFocus: DailyFocusEntity)

    @Query("DELETE FROM daily_focus WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("SELECT * FROM daily_focus ORDER BY date DESC")
    fun getAll(): Flow<List<DailyFocusEntity>>
}
