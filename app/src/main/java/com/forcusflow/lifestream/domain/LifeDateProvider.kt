package com.forcusflow.lifestream.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Unified date and time provider with injectable Clock and centralized ZoneId.
 * Guarantees consistent logical date (day cutoff) handling across Timeline, History,
 * Periodic, Settings, and Glance Widgets.
 */
class LifeDateProvider(
    private val clock: Clock = Clock.systemDefaultZone(),
    val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun nowInstant(): Instant = clock.instant()
    fun nowLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(nowInstant(), zoneId)
    fun nowLocalDate(): LocalDate = nowLocalDateTime().toLocalDate()
    fun nowLocalTime(): LocalTime = nowLocalDateTime().toLocalTime()
    fun nowEpochMilli(): Long = nowInstant().toEpochMilli()

    /**
     * Computes the logical date respecting the day cutoff hour.
     * For example, with cutoffHour = 4, 03:59 on Oct 2nd is logically Oct 1st.
     */
    fun getLogicalDate(
        dateTime: LocalDateTime = nowLocalDateTime(),
        cutoffHour: Int = 4
    ): LocalDate {
        return if (dateTime.hour < cutoffHour) {
            dateTime.toLocalDate().minusDays(1)
        } else {
            dateTime.toLocalDate()
        }
    }

    /**
     * Returns [startEpochMilli, endEpochMilli] for the given logical date.
     * Window: from [logicalDate at cutoffHour:00:00] to [next day at cutoffHour:00:00 - 1ns].
     */
    fun getDayRange(
        logicalDate: LocalDate,
        cutoffHour: Int = 4
    ): Pair<Long, Long> {
        val startDateTime = LocalDateTime.of(logicalDate, LocalTime.of(cutoffHour, 0))
        val endDateTime = startDateTime.plusDays(1).minusNanos(1_000_000) // 1ms before next cutoff
        val startMillis = startDateTime.atZone(zoneId).toInstant().toEpochMilli()
        val endMillis = endDateTime.atZone(zoneId).toInstant().toEpochMilli()
        return Pair(startMillis, endMillis)
    }

    /**
     * Checks if the given epoch millis falls within the logical day window.
     */
    fun isInLogicalDay(
        epochMillis: Long,
        logicalDate: LocalDate,
        cutoffHour: Int = 4
    ): Boolean {
        val (start, end) = getDayRange(logicalDate, cutoffHour)
        return epochMillis in start..end
    }

    fun toLocalDate(epochMillis: Long): LocalDate {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
    }

    fun toLocalDateTime(epochMillis: Long): LocalDateTime {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDateTime()
    }

    fun toEpochMilli(dateTime: LocalDateTime): Long {
        return dateTime.atZone(zoneId).toInstant().toEpochMilli()
    }

    fun formatJapaneseDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE))
    }

    fun formatTime(time: LocalTime): String {
        return time.format(DateTimeFormatter.ofPattern("HH:mm"))
    }
}
