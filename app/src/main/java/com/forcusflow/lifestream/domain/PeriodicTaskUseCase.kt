package com.forcusflow.lifestream.domain

import com.forcusflow.lifestream.data.AppDatabase
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import java.time.LocalDate

/**
 * Domain UseCase for Periodic Habit Tasks.
 * Distinguishes completion from postponement ("今回は見送る"):
 * - Postponement does NOT create a completion log.
 * - Postponement does NOT update lastCompletedAt.
 * - Postponement extends the next due date using postponedUntilDate.
 */
class PeriodicTaskUseCase(
    private val db: AppDatabase,
    private val dateProvider: LifeDateProvider
) {
    /**
     * Records regular completion of a periodic task.
     * Inserts a DONE timeline item, updates lastCompletedAt, and clears any postponement.
     */
    suspend fun recordCompletion(
        template: TemplateEntity,
        targetDate: LocalDate,
        cutoffHour: Int
    ) {
        val logicalToday = dateProvider.getLogicalDate(cutoffHour = cutoffHour)
        val nowMillis = dateProvider.nowEpochMilli()
        val millis = if (targetDate == logicalToday) {
            nowMillis
        } else {
            dateProvider.toEpochMilli(targetDate.atTime(dateProvider.nowLocalTime()))
        }

        val item = TimelineItemEntity(
            title = template.title,
            isDone = true,
            completedAt = millis,
            note = null,
            templateId = template.id,
            createdAt = millis
        )
        db.timelineItemDao().insert(item)

        val latestMillis = maxOf(millis, template.lastCompletedAt ?: 0L)
        val updated = template.copy(
            usageCount = template.usageCount + 1,
            lastCompletedAt = latestMillis,
            postponedUntilDate = null // Clears postponement on completion
        )
        db.templateDao().update(updated)
    }

    /**
     * Postpones the periodic task ("今回は見送る").
     * Crucially:
     * 1. No completion log is created.
     * 2. lastCompletedAt is untouched (preserving genuine streak and historical record).
     * 3. postponedUntilDate is advanced to tomorrow or specified date.
     */
    suspend fun postponeTask(
        template: TemplateEntity,
        cutoffHour: Int,
        delayDays: Int = 1
    ) {
        val logicalToday = dateProvider.getLogicalDate(cutoffHour = cutoffHour)
        val nextDate = logicalToday.plusDays(delayDays.toLong())
        val nowMillis = dateProvider.nowEpochMilli()

        val updated = template.copy(
            postponedUntilDate = nextDate.toString(),
            lastPostponedAt = nowMillis
        )
        db.templateDao().update(updated)
    }

    /**
     * Clears postponement if the user wishes to bring the task back to today.
     */
    suspend fun cancelPostponement(template: TemplateEntity) {
        val updated = template.copy(postponedUntilDate = null)
        db.templateDao().update(updated)
    }
}
