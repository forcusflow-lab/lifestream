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
    val lastCompletedAt: Long? = null,
    val actionType: String = "CHECK", // "CHECK", "COUNT", "TIMER"
    val unit: String = "", // "杯", "回", "分"
    val stepValue: Int = 1, // 1タップあたりの増分 (デフォルト: 1)
    val isPinned: Boolean = false, // クイックスタンプバーへのピン留め
    val displayOrder: Int = 0,
    val timeOfDayZone: String = "ALL_DAY", // "ALL_DAY", "MORNING", "AFTERNOON", "EVENING_NIGHT"
    val postponedUntilDate: String? = null, // "YYYY-MM-DD" 次回目安日まで穏やかに見送り
    val lastPostponedAt: Long? = null // 最終見送り日時
)
