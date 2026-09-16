package com.forcusflow.lifestream.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: String, // "DAILY_COUNT", "INTERVAL", "SIMPLE"
    val intervalDays: Int? = null,
    val defaultAmount: Long? = null,
    val iconKey: String? = null,
    val colorHex: String? = null,
    val usageCount: Int = 0,
    val lastCompletedAt: Long? = null
)
