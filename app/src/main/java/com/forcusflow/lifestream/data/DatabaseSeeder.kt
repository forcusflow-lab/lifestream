package com.forcusflow.lifestream.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object DatabaseSeeder {
    suspend fun seed(templateDao: TemplateDao, timelineItemDao: TimelineItemDao) {
        // Only seed if empty
        if (templateDao.getAll().isNotEmpty()) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val sep12 = LocalDate.of(2026, 9, 12)
        val sep5 = LocalDate.of(2026, 9, 5)
        val sep13 = LocalDate.of(2026, 9, 13)
        val aug15 = LocalDate.of(2026, 8, 15)

        fun millis(date: LocalDate, hour: Int, minute: Int): Long {
            return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        }

        // 1. Templates (core templates with ActionTypes: CHECK, COUNT, TIMER)
        val templates = listOf(
            TemplateEntity(
                id = 1,
                title = "水を飲む",
                type = "DAILY_COUNT",
                intervalDays = null,
                defaultAmount = null,
                iconKey = "💧",
                colorHex = "#38BDF8",
                usageCount = 10,
                lastCompletedAt = millis(today, 14, 10),
                actionType = "COUNT",
                unit = "杯",
                stepValue = 1
            ),
            TemplateEntity(
                id = 2,
                title = "フェイスパック",
                type = "INTERVAL",
                intervalDays = 3,
                defaultAmount = null,
                iconKey = "🧖",
                colorHex = "#A855F7",
                usageCount = 8,
                lastCompletedAt = millis(sep12, 20, 0),
                actionType = "CHECK",
                unit = "回",
                stepValue = 1
            ),
            TemplateEntity(
                id = 3,
                title = "夜食",
                type = "SIMPLE",
                intervalDays = null,
                defaultAmount = 240,
                iconKey = "🍜",
                colorHex = "#EF4444",
                usageCount = 5,
                lastCompletedAt = millis(sep12, 23, 15),
                actionType = "CHECK",
                unit = "回",
                stepValue = 1
            ),
            TemplateEntity(
                id = 4,
                title = "風呂 排水ネット交換",
                type = "INTERVAL",
                intervalDays = 7,
                defaultAmount = null,
                iconKey = "🧹",
                colorHex = "#8C5A3C",
                usageCount = 6,
                lastCompletedAt = millis(sep5, 10, 0),
                actionType = "CHECK",
                unit = "回",
                stepValue = 1
            ),
            TemplateEntity(
                id = 5,
                title = "シーツ洗濯",
                type = "INTERVAL",
                intervalDays = 7,
                defaultAmount = null,
                iconKey = "🛏️",
                colorHex = "#10B981",
                usageCount = 4,
                lastCompletedAt = millis(sep13, 9, 0),
                actionType = "CHECK",
                unit = "回",
                stepValue = 1
            ),
            TemplateEntity(
                id = 6,
                title = "洗濯槽クリーナー",
                type = "INTERVAL",
                intervalDays = 30,
                defaultAmount = null,
                iconKey = "🧼",
                colorHex = "#F59E0B",
                usageCount = 2,
                lastCompletedAt = millis(aug15, 11, 0),
                actionType = "CHECK",
                unit = "回",
                stepValue = 1
            ),
            TemplateEntity(
                id = 7,
                title = "換気扇フィルター清掃",
                type = "INTERVAL",
                intervalDays = 30,
                defaultAmount = null,
                iconKey = "🌀",
                colorHex = "#6366F1",
                usageCount = 2,
                lastCompletedAt = millis(sep5, 14, 0),
                actionType = "CHECK",
                unit = "回",
                stepValue = 1
            ),
            TemplateEntity(
                id = 8,
                title = "風呂 防カビくん煙剤",
                type = "INTERVAL",
                intervalDays = 60,
                defaultAmount = null,
                iconKey = "💨",
                colorHex = "#8B5CF6",
                usageCount = 1,
                lastCompletedAt = millis(sep5, 15, 0),
                actionType = "CHECK",
                unit = "回",
                stepValue = 1
            ),
            TemplateEntity(
                id = 9,
                title = "勉強・読書",
                type = "SIMPLE",
                intervalDays = null,
                defaultAmount = null,
                iconKey = "⏱️",
                colorHex = "#3B82F6",
                usageCount = 3,
                lastCompletedAt = null,
                actionType = "TIMER",
                unit = "分",
                stepValue = 1
            )
        )
        templateDao.insertAll(templates)

        // 2. Timeline Items
        val items = mutableListOf<TimelineItemEntity>()

        // Anytime ToDo
        items.add(
            TimelineItemEntity(
                title = "今日中: 洗濯用洗剤をネットでポチる",
                isDone = false,
                scheduledAt = null,
                completedAt = null
            )
        )

        // Today's done items
        items.add(
            TimelineItemEntity(
                title = "起床・白湯を飲む",
                note = "しっかり睡眠とれた",
                isDone = true,
                completedAt = millis(today, 8, 15)
            )
        )
        items.add(
            TimelineItemEntity(
                title = "定食ランチ",
                note = "チキン南蛮定食",
                amount = 920,
                isDone = true,
                completedAt = millis(today, 12, 30)
            )
        )
        items.add(
            TimelineItemEntity(
                title = "水を飲む (3杯目)",
                note = "デイリー習慣カウント",
                isDone = true,
                completedAt = millis(today, 14, 10),
                templateId = 1
            )
        )

        // Today's future scheduled items
        items.add(
            TimelineItemEntity(
                title = "スーパー買い出し",
                note = "牛乳, 卵, 納豆",
                isDone = false,
                scheduledAt = millis(today, 18, 30)
            )
        )
        items.add(
            TimelineItemEntity(
                title = "風呂 排水ネット交換",
                note = "前回から7日経過・推奨日！",
                isDone = false,
                scheduledAt = millis(today, 20, 0),
                templateId = 4
            )
        )
        items.add(
            TimelineItemEntity(
                title = "フェイスパック",
                note = "ビタミンC導入パック",
                isDone = false,
                scheduledAt = millis(today, 21, 30),
                templateId = 2
            )
        )

        // September 12 (土) historical records (matching Tab 2 exactly)
        items.add(
            TimelineItemEntity(
                title = "電車移動 (駅前カフェへ)",
                note = "読書タイム",
                amount = 380,
                isDone = true,
                completedAt = millis(sep12, 9, 20)
            )
        )
        items.add(
            TimelineItemEntity(
                title = "水を飲む (目標達成・計5回)",
                note = "デイリー水分補給",
                isDone = true,
                completedAt = millis(sep12, 14, 30),
                templateId = 1
            )
        )
        items.add(
            TimelineItemEntity(
                title = "フェイスパック実施",
                note = "3日周期ルーティン完了",
                isDone = true,
                completedAt = millis(sep12, 20, 0),
                templateId = 2
            )
        )
        items.add(
            TimelineItemEntity(
                title = "夜食カップ麺 (罪悪感ゼロ)",
                note = "残業帰りにコンビニで購入",
                amount = 240,
                isDone = true,
                completedAt = millis(sep12, 23, 15),
                templateId = 3
            )
        )

        // Other history dots for calendar
        items.add(
            TimelineItemEntity(
                title = "シーツ洗濯",
                isDone = true,
                completedAt = millis(sep13, 10, 0),
                templateId = 5
            )
        )
        items.add(
            TimelineItemEntity(
                title = "換気扇フィルター清掃",
                isDone = true,
                completedAt = millis(sep5, 14, 0),
                templateId = 7
            )
        )
        items.add(
            TimelineItemEntity(
                title = "風呂 排水ネット交換",
                isDone = true,
                completedAt = millis(sep5, 10, 0),
                templateId = 4
            )
        )

        timelineItemDao.insertAll(items)
    }
}
