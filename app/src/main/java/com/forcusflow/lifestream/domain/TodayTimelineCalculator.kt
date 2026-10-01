package com.forcusflow.lifestream.domain

import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PeriodicTaskStatus(
    val template: TemplateEntity,
    val isDueToday: Boolean,
    val isPostponed: Boolean,
    val daysSinceLastDone: Long?,
    val nextDueDate: LocalDate,
    val gentleStatusText: String
)

data class TodayTimelineData(
    val logicalDate: LocalDate,
    val dateFormatted: String,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val doneItems: List<TimelineItemEntity>,
    val scheduledTodayItems: List<TimelineItemEntity>,
    val anytimeTodayItems: List<TimelineItemEntity>,
    val pendingHandItems: List<TimelineItemEntity>,
    val drawerStockItems: List<TimelineItemEntity>,
    val duePeriodicTemplates: List<PeriodicTaskStatus>,
    val todayBadgeCount: Int
)

/**
 * Pure Domain Calculator for Today's Timeline and Widget.
 * Guarantees that the app UI and Glance Widget share the exact same logical day,
 * cutoff rules, anytime task classification, periodic postponement handling,
 * and badge count logic.
 */
object TodayTimelineCalculator {

    fun calculate(
        allItems: List<TimelineItemEntity>,
        templates: List<TemplateEntity>,
        dateProvider: LifeDateProvider,
        cutoffHour: Int
    ): TodayTimelineData {
        val logicalToday = dateProvider.getLogicalDate(cutoffHour = cutoffHour)
        val (todayStart, todayEnd) = dateProvider.getDayRange(logicalToday, cutoffHour)
        val dateFormatted = dateProvider.formatJapaneseDate(logicalToday)

        // 1. Done logs completed within today's window
        val doneItems = allItems.filter { item ->
            item.isDone && (
                (item.completedAt != null && item.completedAt in todayStart..todayEnd) ||
                (item.completedAt == null && item.scheduledAt != null && item.scheduledAt in todayStart..todayEnd)
            )
        }.sortedBy { it.completedAt ?: it.scheduledAt ?: 0L }

        // 2. Scheduled ToDos for today
        val scheduledToday = allItems.filter { item ->
            !item.isDone && item.scheduledAt != null && item.scheduledAt in todayStart..todayEnd
        }.sortedBy { it.scheduledAt ?: 0L }

        // 3. Anytime ToDos created today
        val anytimeToday = allItems.filter { item ->
            !item.isDone && item.scheduledAt == null && (item.createdAt >= todayStart || item.createdAt == 0L)
        }.sortedBy { it.id }

        // 4. Combined pending hand items (timed tasks first, then anytime)
        val pendingHandItems = (scheduledToday + anytimeToday).distinctBy { it.id }

        // 5. Drawer stock items (future scheduled tasks, past unfinished roll-overs)
        val drawerStock = allItems.filter { item ->
            val sched = item.scheduledAt
            !item.isDone && (
                (sched != null && sched > todayEnd) || // Future scheduled
                (sched != null && sched < todayStart) || // Past scheduled roll-over
                (sched == null && item.createdAt in 1 until todayStart) // Past anytime roll-over
            )
        }.sortedByDescending { it.scheduledAt ?: it.createdAt }

        // 6. Periodic habits analysis
        val periodicStatuses = templates
            .filter { it.type == "INTERVAL" && (it.intervalDays ?: 0) > 0 }
            .map { tmpl ->
                val interval = tmpl.intervalDays ?: 7
                val lastDoneDate = tmpl.lastCompletedAt?.let { dateProvider.toLocalDate(it) }
                val daysSince = if (lastDoneDate != null) ChronoUnit.DAYS.between(lastDoneDate, logicalToday) else null

                // Check postponement ("今回は見送る")
                val isPostponed = !tmpl.postponedUntilDate.isNullOrBlank() &&
                    tmpl.postponedUntilDate > logicalToday.toString()

                val nextDueDate = if (isPostponed && !tmpl.postponedUntilDate.isNullOrBlank()) {
                    try {
                        LocalDate.parse(tmpl.postponedUntilDate)
                    } catch (e: Exception) {
                        logicalToday.plusDays(1)
                    }
                } else if (lastDoneDate != null) {
                    lastDoneDate.plusDays(interval.toLong())
                } else {
                    logicalToday
                }

                val isDue = !isPostponed && (daysSince == null || daysSince >= interval)

                val gentleStatusText = when {
                    isPostponed -> "次回まで見送り中"
                    daysSince == null -> "次の目安: 今日"
                    daysSince > interval -> "${daysSince}日ぶり"
                    daysSince == interval.toLong() -> "そろそろ"
                    else -> "次の目安: ${dateProvider.formatJapaneseDate(nextDueDate)}"
                }

                PeriodicTaskStatus(
                    template = tmpl,
                    isDueToday = isDue,
                    isPostponed = isPostponed,
                    daysSinceLastDone = daysSince,
                    nextDueDate = nextDueDate,
                    gentleStatusText = gentleStatusText
                )
            }

        // 7. Today badge count: ONLY scheduled ToDos for today!
        // Overdue periodic tasks, drawer items, streaks, etc. are NOT included.
        val todayBadgeCount = scheduledToday.size

        return TodayTimelineData(
            logicalDate = logicalToday,
            dateFormatted = dateFormatted,
            windowStartMillis = todayStart,
            windowEndMillis = todayEnd,
            doneItems = doneItems,
            scheduledTodayItems = scheduledToday,
            anytimeTodayItems = anytimeToday,
            pendingHandItems = pendingHandItems,
            drawerStockItems = drawerStock,
            duePeriodicTemplates = periodicStatuses,
            todayBadgeCount = todayBadgeCount
        )
    }
}
