package com.forcusflow.lifestream

import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.domain.LifeDateProvider
import com.forcusflow.lifestream.domain.TodayTimelineCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TodayTimelineCalculatorTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private val fixedInstant = LocalDateTime.of(2026, 10, 2, 10, 0, 0)
        .atZone(zone).toInstant()
    private val dateProvider = LifeDateProvider(Clock.fixed(fixedInstant, zone), zone)
    private val cutoffHour = 4

    @Test
    fun testTodayBadgeCountContainsOnlyScheduledTodayTasks() {
        val (todayStart, todayEnd) = dateProvider.getDayRange(LocalDate.of(2026, 10, 2), cutoffHour)

        // 1. Scheduled today task (should be in badge count)
        val task1 = TimelineItemEntity(
            id = 1,
            title = "15時のミーティング",
            isDone = false,
            scheduledAt = todayStart + 11 * 3600 * 1000L, // 15:00
            createdAt = todayStart
        )

        // 2. Anytime today task (should NOT be in badge count)
        val task2 = TimelineItemEntity(
            id = 2,
            title = "牛乳を買う",
            isDone = false,
            scheduledAt = null,
            createdAt = todayStart + 1000L
        )

        // 3. Past unfinished rollover task in drawer (should NOT be in badge count)
        val task3 = TimelineItemEntity(
            id = 3,
            title = "昨日のタスク",
            isDone = false,
            scheduledAt = null,
            createdAt = todayStart - 10000L
        )

        // 4. Overdue periodic habit template (should NOT be in badge count)
        val overdueHabit = TemplateEntity(
            id = 10,
            title = "部屋の掃除",
            type = "INTERVAL",
            intervalDays = 7,
            lastCompletedAt = todayStart - 10 * 86400 * 1000L // 10 days ago
        )

        val result = TodayTimelineCalculator.calculate(
            allItems = listOf(task1, task2, task3),
            templates = listOf(overdueHabit),
            dateProvider = dateProvider,
            cutoffHour = cutoffHour
        )

        // Badge count MUST be exactly 1 (only task1)
        assertEquals(1, result.todayBadgeCount)
        assertEquals(1, result.scheduledTodayItems.size)
        assertEquals(1, result.anytimeTodayItems.size)
        assertEquals(1, result.drawerStockItems.size)
        assertEquals(2, result.pendingHandItems.size)
    }

    @Test
    fun testPeriodicTaskPostponementDoesNotCreateCompletionAndDelaysDue() {
        val (todayStart, _) = dateProvider.getDayRange(LocalDate.of(2026, 10, 2), cutoffHour)

        // An interval task that was postponed to tomorrow ("2026-10-03")
        val habit = TemplateEntity(
            id = 20,
            title = "ストレッチ",
            type = "INTERVAL",
            intervalDays = 3,
            lastCompletedAt = todayStart - 5 * 86400 * 1000L,
            postponedUntilDate = "2026-10-03",
            lastPostponedAt = todayStart
        )

        val result = TodayTimelineCalculator.calculate(
            allItems = emptyList(),
            templates = listOf(habit),
            dateProvider = dateProvider,
            cutoffHour = cutoffHour
        )

        val status = result.duePeriodicTemplates.first()
        assertTrue(status.isPostponed)
        assertFalse("Postponed task should not be due today", status.isDueToday)
        assertEquals("次回まで見送り中", status.gentleStatusText)
        assertEquals(LocalDate.of(2026, 10, 3), status.nextDueDate)
    }

    @Test
    fun testDoneItemsCollectedWithinCutoffWindow() {
        val (todayStart, todayEnd) = dateProvider.getDayRange(LocalDate.of(2026, 10, 2), cutoffHour)

        val doneItemToday = TimelineItemEntity(
            id = 50,
            title = "朝の散歩",
            isDone = true,
            completedAt = todayStart + 2 * 3600 * 1000L // 06:00
        )
        val doneItemYesterday = TimelineItemEntity(
            id = 51,
            title = "昨日の散歩",
            isDone = true,
            completedAt = todayStart - 1000L // Just before cutoff
        )

        val result = TodayTimelineCalculator.calculate(
            allItems = listOf(doneItemToday, doneItemYesterday),
            templates = emptyList(),
            dateProvider = dateProvider,
            cutoffHour = cutoffHour
        )

        assertEquals(1, result.doneItems.size)
        assertEquals("朝の散歩", result.doneItems[0].title)
    }
}
