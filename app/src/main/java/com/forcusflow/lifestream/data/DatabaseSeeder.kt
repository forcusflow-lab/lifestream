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
                    if (item.title in listOf("byLifeへようこそ！", "洗濯用洗剤をネットでポチる", "シーツ洗濯 実施", "フェイスパック 実施")) {
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
            val twentyFiveDaysAgo = today.minusDays(25)

            fun millis(date: LocalDate, hour: Int, minute: Int): Long {
                return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
            }

        // 1. Templates: 4 Action Stamps (Pinned to Quick Bar) + 3 Periodic Maintenance Tasks
        val templates = listOf(
            // === クイックスタンプ（行動ログ用・上部バーピン留め） ===
            TemplateEntity(
                id = 1,
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
                displayOrder = 0
            ),
            TemplateEntity(
                id = 2,
                title = "コーヒー",
                type = "SIMPLE",
                intervalDays = null,
                defaultAmount = 350,
                iconKey = "☕",
                colorHex = "#8C5A3C",
                usageCount = 0,
                lastCompletedAt = null,
                actionType = "CHECK",
                unit = "杯",
                stepValue = 1,
                isPinned = true,
                displayOrder = 1
            ),
            TemplateEntity(
                id = 3,
                title = "読書・勉強",
                type = "SIMPLE",
                intervalDays = null,
                defaultAmount = null,
                iconKey = "📖",
                colorHex = "#3B82F6",
                usageCount = 0,
                lastCompletedAt = null,
                actionType = "TIMER",
                unit = "分",
                stepValue = 1,
                isPinned = true,
                displayOrder = 2
            ),
            TemplateEntity(
                id = 4,
                title = "散歩・運動",
                type = "SIMPLE",
                intervalDays = null,
                defaultAmount = null,
                iconKey = "🚶",
                colorHex = "#10B981",
                usageCount = 0,
                lastCompletedAt = null,
                actionType = "TIMER",
                unit = "分",
                stepValue = 1,
                isPinned = true,
                displayOrder = 3
            ),

            // === 周期タスク（定期メンテナンス） ===
            TemplateEntity(
                id = 5,
                title = "シーツ洗濯",
                type = "INTERVAL",
                intervalDays = 7,
                defaultAmount = null,
                iconKey = "🛏️",
                colorHex = "#10B981",
                usageCount = 1,
                lastCompletedAt = millis(fiveDaysAgo, 10, 0),
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
                intervalDays = 3,
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
            ),
            TemplateEntity(
                id = 7,
                title = "換気扇フィルター清掃",
                type = "INTERVAL",
                intervalDays = 30,
                defaultAmount = null,
                iconKey = "🌀",
                colorHex = "#6366F1",
                usageCount = 1,
                lastCompletedAt = millis(twentyFiveDaysAgo, 14, 0),
                actionType = "CHECK",
                unit = "回",
                stepValue = 1,
                isPinned = false,
                displayOrder = 2
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
                note = "上のスタンプや右下の＋から今日の行動を記録してみましょう",
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
                title = "シーツ洗濯 実施",
                isDone = true,
                completedAt = millis(fiveDaysAgo, 10, 0),
                templateId = 5
            )
        )
        items.add(
            TimelineItemEntity(
                title = "フェイスパック 実施",
                isDone = true,
                completedAt = millis(twoDaysAgo, 21, 0),
                templateId = 6
            )
        )

        timelineItemDao.insertAll(items)
        }
    }
}
