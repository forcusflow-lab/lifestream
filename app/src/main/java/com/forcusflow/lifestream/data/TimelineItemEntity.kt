package com.forcusflow.lifestream.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "timeline_items")
data class TimelineItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val isDone: Boolean = false,
    val scheduledAt: Long? = null,
    val completedAt: Long? = null,
    val amount: Long? = null,
    val note: String? = null,
    val templateId: Long? = null,
    val durationSeconds: Int? = null,
    val countValue: Int? = null
)
