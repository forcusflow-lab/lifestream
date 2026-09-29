package com.forcusflow.lifestream

import com.forcusflow.lifestream.data.TimelineItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TaskRolloverPurificationTest {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 9, 30)
    private val cutoffHour = 4

    private val startDateTime = LocalDateTime.of(today, LocalTime.of(cutoffHour, 0))
    private val endDateTime = startDateTime.plusDays(1).minusNanos(1)
    private val todayStart = startDateTime.atZone(zone).toInstant().toEpochMilli()
    private val todayEnd = endDateTime.atZone(zone).toInstant().toEpochMilli()

    private fun filterTodayPending(allItems: List<TimelineItemEntity>): List<TimelineItemEntity> {
        val scheduledToday = allItems.filter { item ->
            !item.isDone && item.scheduledAt != null && item.scheduledAt in todayStart..todayEnd
        }.sortedBy { it.scheduledAt ?: 0L }

        val anytimeToday = allItems.filter { item ->
            !item.isDone && item.scheduledAt == null && (item.createdAt >= todayStart || item.createdAt == 0L)
        }.sortedBy { it.id }

        return (scheduledToday + anytimeToday).distinctBy { it.id }
    }

    private fun filterDrawerStock(allItems: List<TimelineItemEntity>): List<TimelineItemEntity> {
        return allItems.filter { item ->
            val sched = item.scheduledAt
            !item.isDone && (
                // Future scheduled tasks
                (sched != null && sched > todayEnd) ||
                // Past scheduled unfinished tasks (rolled over into drawer!)
                (sched != null && sched < todayStart) ||
                // Past anytime unfinished tasks (rolled over into drawer!)
                (sched == null && item.createdAt in 1 until todayStart)
            )
        }
    }

    private fun filterPurgeCandidates(allItems: List<TimelineItemEntity>, nowMillis: Long): List<TimelineItemEntity> {
        val thirtyDaysAgo = nowMillis - 30L * 24 * 60 * 60 * 1000
        return allItems.filter {
            val sched = it.scheduledAt
            !it.isDone &&
            (it.createdAt in 1..thirtyDaysAgo) &&
            (sched == null || sched < thirtyDaysAgo)
        }
    }

    @Test
    fun testTodayTasksAppearInTodayPending() {
        val timedToday = TimelineItemEntity(
            id = 1,
            title = "14時の打ち合わせ",
            isDone = false,
            scheduledAt = todayStart + 10 * 3600 * 1000L, // 14:00
            createdAt = todayStart + 1000L
        )
        val anytimeToday = TimelineItemEntity(
            id = 2,
            title = "牛乳を買う",
            isDone = false,
            scheduledAt = null,
            createdAt = todayStart + 2000L
        )

        val items = listOf(timedToday, anytimeToday)
        val todayPending = filterTodayPending(items)
        val drawerStock = filterDrawerStock(items)

        assertEquals(2, todayPending.size)
        assertEquals("14時の打ち合わせ", todayPending[0].title) // Timed task sorted to top!
        assertEquals("牛乳を買う", todayPending[1].title)
        assertTrue(drawerStock.isEmpty())
    }

    @Test
    fun testYesterdayUnfinishedTasksRolloverToDrawerQuietly() {
        val yesterdayMillis = todayStart - 5 * 3600 * 1000L
        val yesterdayTimedUnfinished = TimelineItemEntity(
            id = 10,
            title = "昨日のレポート提出",
            isDone = false,
            scheduledAt = yesterdayMillis,
            createdAt = yesterdayMillis - 1000L
        )
        val yesterdayAnytimeUnfinished = TimelineItemEntity(
            id = 11,
            title = "昨日の読書",
            isDone = false,
            scheduledAt = null,
            createdAt = yesterdayMillis
        )

        val items = listOf(yesterdayTimedUnfinished, yesterdayAnytimeUnfinished)
        val todayPending = filterTodayPending(items)
        val drawerStock = filterDrawerStock(items)

        // Today's tray starts completely fresh (真っ白)!
        assertTrue(todayPending.isEmpty())

        // Rolled over tasks quietly reside in the drawer (引き出し)!
        assertEquals(2, drawerStock.size)
        assertTrue(drawerStock.any { it.title == "昨日のレポート提出" })
        assertTrue(drawerStock.any { it.title == "昨日の読書" })
    }

    @Test
    fun testFutureTasksStayInDrawer() {
        val tomorrowMillis = todayEnd + 5 * 3600 * 1000L
        val futureTask = TimelineItemEntity(
            id = 20,
            title = "明日の歯医者",
            isDone = false,
            scheduledAt = tomorrowMillis,
            createdAt = todayStart + 1000L
        )

        val items = listOf(futureTask)
        val todayPending = filterTodayPending(items)
        val drawerStock = filterDrawerStock(items)

        assertTrue(todayPending.isEmpty())
        assertEquals(1, drawerStock.size)
        assertEquals("明日の歯医者", drawerStock[0].title)
    }

    @Test
    fun testNaturalPurificationOfAncientStockTasks() {
        val now = todayStart + 12 * 3600 * 1000L
        val thirtyFiveDaysAgo = now - 35L * 24 * 60 * 60 * 1000L

        val ancientTask = TimelineItemEntity(
            id = 99,
            title = "去年の片付けメモ",
            isDone = false,
            scheduledAt = null,
            createdAt = thirtyFiveDaysAgo
        )
        val recentTask = TimelineItemEntity(
            id = 100,
            title = "先週のメモ",
            isDone = false,
            scheduledAt = null,
            createdAt = now - 7L * 24 * 60 * 60 * 1000L
        )

        val purgeCandidates = filterPurgeCandidates(listOf(ancientTask, recentTask), now)
        assertEquals(1, purgeCandidates.size)
        assertEquals("去年の片付けメモ", purgeCandidates[0].title)
    }
}
