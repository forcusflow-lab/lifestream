package com.forcusflow.lifestream.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "daily_focus")
data class DailyFocusEntity(
    @PrimaryKey
    val date: String, // "YYYY-MM-DD"
    val content: String,
    val updatedAt: Long = System.currentTimeMillis()
)
