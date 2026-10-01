package com.forcusflow.lifestream.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Safe database seeder for initial sample data.
 * Adheres strictly to the principle of never automatically modifying or deleting user-created data.
 */
object DatabaseSeeder {
    private val mutex = Mutex()

    suspend fun seed(
        templateDao: TemplateDao,
        timelineItemDao: TimelineItemDao,
        force: Boolean = false
    ) {
        mutex.withLock {
            val existingTemplates = templateDao.getAll()
            val existingItems = timelineItemDao.getAll()

            // If data already exists and this is not a forced reload, do nothing.
            // Never delete or touch user data based on title/note strings!
            if (!force && (existingTemplates.isNotEmpty() || existingItems.isNotEmpty())) {
                return@withLock
            }

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val fiveDaysAgo = today.minusDays(5)
            val twoDaysAgo = today.minusDays(2)

            fun millis(date: LocalDate, hour: Int, minute: Int): Long {
                return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
            }

            // 1. Templates: 4 Quick Records (Pinned) + 2 Periodic Routine Tasks
            val templates = listOf(
                // === クイック記録（日常の即時ログ用・上部バーピン留め） ===
                TemplateEntity(
                    id = if (force) 0 else 1,
                    title = "勉強",
                    type = "SIMPLE",
                    intervalDays = null,
                    defaultAmount = null,
                    iconKey = "📚",
                    colorHex = "#3B82F6",
                    usageCount = 0,
                    lastCompletedAt = null,
                    actionType = "TIMER",
                    unit = "分",
                    stepValue = 1,
                    isPinned = true,
                    displayOrder = 0
                ),
                TemplateEntity(
                    id = if (force) 0 else 2,
                    title = "家事",
                    type = "SIMPLE",
                    intervalDays = null,
                    defaultAmount = null,
                    iconKey = "🧹",
                    colorHex = "#10B981",
                    usageCount = 0,
                    lastCompletedAt = null,
                    actionType = "TIMER",
                    unit = "分",
                    stepValue = 1,
                    isPinned = true,
                    displayOrder = 1
                ),
                TemplateEntity(
                    id = if (force) 0 else 3,
                    title = "水を飲む",
                    type = "DAILY_COUNT",
                    intervalDays = null,
                    defaultAmount = null,
                    iconKey = "💧",
                    colorHex = "#38BDF8",
                    usageCount = 0,
                    lastCompletedAt = null,
                    actionType = "COUNT",
                    unit = "杯",
                    stepValue = 1,
                    isPinned = true,
                    displayOrder = 2
                ),
                TemplateEntity(
                    id = if (force) 0 else 4,
                    title = "お菓子食べる",
                    type = "DAILY_COUNT",
                    intervalDays = null,
                    defaultAmount = null,
                    iconKey = "🍪",
                    colorHex = "#F59E0B",
                    usageCount = 0,
                    lastCompletedAt = null,
                    actionType = "COUNT",
                    unit = "個",
                    stepValue = 1,
                    isPinned = true,
                    displayOrder = 3
                ),

                // === 周期・ルーティン（定期サイクル） ===
                TemplateEntity(
                    id = if (force) 0 else 5,
                    title = "眉毛を整える",
                    type = "INTERVAL",
                    intervalDays = 7,
                    defaultAmount = null,
                    iconKey = "✂️",
                    colorHex = "#8C5A3C",
                    usageCount = 1,
                    lastCompletedAt = millis(fiveDaysAgo, 19, 0),
                    actionType = "CHECK",
                    unit = "回",
                    stepValue = 1,
                    isPinned = false,
                    displayOrder = 0
                ),
                TemplateEntity(
                    id = if (force) 0 else 6,
                    title = "フェイスパック",
                    type = "INTERVAL",
                    intervalDays = 7,
                    defaultAmount = null,
                    iconKey = "🧖",
                    colorHex = "#A855F7",
                    usageCount = 1,
                    lastCompletedAt = millis(twoDaysAgo, 21, 0),
                    actionType = "CHECK",
                    unit = "回",
                    stepValue = 1,
                    isPinned = false,
                    displayOrder = 1
                )
            )

            // If force reload, check which templates already exist by title to avoid duplication
            val insertedTemplates = mutableListOf<TemplateEntity>()
            templates.forEach { tmpl ->
                val existing = templateDao.getAll().firstOrNull { it.title == tmpl.title }
                if (existing == null) {
                    val newId = templateDao.insert(tmpl)
                    insertedTemplates.add(tmpl.copy(id = newId))
                } else {
                    insertedTemplates.add(existing)
                }
            }

            // 2. Timeline Items: Clear, explicitly labeled starter sample items
            val now = LocalDateTime.now()
            val template5Id = insertedTemplates.firstOrNull { it.title == "眉毛を整える" }?.id
            val template6Id = insertedTemplates.firstOrNull { it.title == "フェイスパック" }?.id

            val sampleItems = listOf(
                TimelineItemEntity(
                    title = "byLifeへようこそ！",
                    note = "【サンプル】上のクイック記録や右下の＋から今日の行動を記録できます。この項目はいつでも削除できます。",
                    isDone = true,
                    completedAt = now.minusMinutes(10).atZone(zone).toInstant().toEpochMilli(),
                    createdAt = now.minusMinutes(10).atZone(zone).toInstant().toEpochMilli()
                ),
                TimelineItemEntity(
                    title = "洗濯用洗剤をネットでポチる",
                    note = "【サンプルToDo】完了チェックしたり引き出しに保管できます。編集や削除も自由です。",
                    isDone = false,
                    scheduledAt = null,
                    completedAt = null,
                    createdAt = now.minusMinutes(5).atZone(zone).toInstant().toEpochMilli()
                ),
                TimelineItemEntity(
                    title = "眉毛を整える",
                    note = "【サンプル周期実績】5日前の完了ログです。",
                    isDone = true,
                    completedAt = millis(fiveDaysAgo, 19, 0),
                    templateId = template5Id,
                    createdAt = millis(fiveDaysAgo, 19, 0)
                ),
                TimelineItemEntity(
                    title = "フェイスパック",
                    note = "【サンプル周期実績】2日前の完了ログです。",
                    isDone = true,
                    completedAt = millis(twoDaysAgo, 21, 0),
                    templateId = template6Id,
                    createdAt = millis(twoDaysAgo, 21, 0)
                )
            )

            // Insert sample items without clobbering existing ones
            sampleItems.forEach { sample ->
                val alreadyExists = existingItems.any { it.title == sample.title && it.note == sample.note }
                if (!alreadyExists) {
                    timelineItemDao.insert(sample)
                }
            }
        }
    }
}
