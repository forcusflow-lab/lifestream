package com.forcusflow.lifestream.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object DatabaseSeeder {
    private val mutex = Mutex()

    suspend fun seed(templateDao: TemplateDao, timelineItemDao: TimelineItemDao) {
        mutex.withLock {
            val existingTemplates = templateDao.getAll()
            if (existingTemplates.isNotEmpty()) {
                // 自動クリーンアップ: 以前のレースコンディションで重複してしまったテンプレートを削除
                val seenTitles = mutableSetOf<String>()
                existingTemplates.forEach { tmpl ->
                    if (!seenTitles.add(tmpl.title)) {
                        templateDao.delete(tmpl)
                    }
                }
                // 同様に初期タイムラインアイテムの重複もクリーンアップ
                val existingItems = timelineItemDao.getAll()
                val seenItemKeys = mutableSetOf<String>()
                existingItems.forEach { item ->
                    val key = "${item.title}_${item.note}_${item.templateId}"
                    if (item.title in listOf("byLifeへようこそ！", "洗濯用洗剤をネットでポチる", "眉毛を整える 実施", "フェイスパック 実施")) {
                        if (!seenItemKeys.add(key)) {
                            timelineItemDao.delete(item)
                        }
                    }
                }
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
                id = 1,
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
                id = 2,
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
                id = 3,
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
                id = 4,
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
                id = 5,
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
                id = 6,
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
        templateDao.insertAll(templates)

        // 2. Timeline Items: Minimal, welcoming starter logs
        val now = LocalDateTime.now()
        val items = mutableListOf<TimelineItemEntity>()

        // 1 Welcome log
        items.add(
            TimelineItemEntity(
                title = "byLifeへようこそ！",
                note = "上のクイック記録や右下の＋から今日の行動を記録してみましょう",
                isDone = true,
                completedAt = now.minusMinutes(10).atZone(zone).toInstant().toEpochMilli()
            )
        )

        // 1 Starter ToDo
        items.add(
            TimelineItemEntity(
                title = "洗濯用洗剤をネットでポチる",
                isDone = false,
                scheduledAt = null,
                completedAt = null
            )
        )

        // Previous cycle completions (for periodic tracking reference)
        items.add(
            TimelineItemEntity(
                title = "眉毛を整える",
                isDone = true,
                completedAt = millis(fiveDaysAgo, 19, 0),
                templateId = 5
            )
        )
        items.add(
            TimelineItemEntity(
                title = "フェイスパック",
                isDone = true,
                completedAt = millis(twoDaysAgo, 21, 0),
                templateId = 6
            )
        )

        timelineItemDao.insertAll(items)
        }
    }
}
