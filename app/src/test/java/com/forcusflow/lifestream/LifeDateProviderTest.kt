package com.forcusflow.lifestream

import com.forcusflow.lifestream.domain.LifeDateProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LifeDateProviderTest {

    private val zoneTokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun testLogicalDateAtMidnightToCutoffBoundary() {
        // Cutoff is 04:00.
        // At 03:59:59 on Oct 2nd, the logical date should be Oct 1st!
        val instantBeforeCutoff = LocalDateTime.of(2026, 10, 2, 3, 59, 59)
            .atZone(zoneTokyo).toInstant()
        val providerBefore = LifeDateProvider(Clock.fixed(instantBeforeCutoff, zoneTokyo), zoneTokyo)
        val logicalDateBefore = providerBefore.getLogicalDate(cutoffHour = 4)
        assertEquals(LocalDate.of(2026, 10, 1), logicalDateBefore)

        // At exactly 04:00:00 on Oct 2nd, the logical date switches to Oct 2nd!
        val instantAtCutoff = LocalDateTime.of(2026, 10, 2, 4, 0, 0)
            .atZone(zoneTokyo).toInstant()
        val providerAt = LifeDateProvider(Clock.fixed(instantAtCutoff, zoneTokyo), zoneTokyo)
        val logicalDateAt = providerAt.getLogicalDate(cutoffHour = 4)
        assertEquals(LocalDate.of(2026, 10, 2), logicalDateAt)

        // At 23:59:59 on Oct 2nd, the logical date remains Oct 2nd.
        val instantNight = LocalDateTime.of(2026, 10, 2, 23, 59, 59)
            .atZone(zoneTokyo).toInstant()
        val providerNight = LifeDateProvider(Clock.fixed(instantNight, zoneTokyo), zoneTokyo)
        assertEquals(LocalDate.of(2026, 10, 2), providerNight.getLogicalDate(cutoffHour = 4))
    }

    @Test
    fun testCutoffZeroMeansStandardMidnight() {
        val instant = LocalDateTime.of(2026, 10, 2, 0, 30, 0)
            .atZone(zoneTokyo).toInstant()
        val provider = LifeDateProvider(Clock.fixed(instant, zoneTokyo), zoneTokyo)
        assertEquals(LocalDate.of(2026, 10, 2), provider.getLogicalDate(cutoffHour = 0))
    }

    @Test
    fun testDayRangeCoversExactCutoffWindow() {
        val date = LocalDate.of(2026, 10, 2)
        val provider = LifeDateProvider(zoneId = zoneTokyo)
        val (start, end) = provider.getDayRange(date, cutoffHour = 4)

        val startDt = provider.toLocalDateTime(start)
        val endDt = provider.toLocalDateTime(end)

        assertEquals(LocalDateTime.of(2026, 10, 2, 4, 0, 0), startDt)
        assertEquals(2026, endDt.year)
        assertEquals(10, endDt.monthValue)
        assertEquals(3, endDt.dayOfMonth)
        assertEquals(3, endDt.hour)
        assertEquals(59, endDt.minute)

        assertTrue(provider.isInLogicalDay(start, date, 4))
        assertTrue(provider.isInLogicalDay(end, date, 4))
        assertFalse(provider.isInLogicalDay(start - 1, date, 4))
        assertFalse(provider.isInLogicalDay(end + 2, date, 4))
    }

    @Test
    fun testDaylightSavingTimeTransition() {
        // US Eastern time has DST transitions
        val zoneUS = ZoneId.of("America/New_York")
        val fixedInstant = Instant.parse("2026-11-01T05:30:00Z") // 1:30 AM EDT/EST
        val provider = LifeDateProvider(Clock.fixed(fixedInstant, zoneUS), zoneUS)

        val localDt = provider.nowLocalDateTime()
        val logicalDate = provider.getLogicalDate(localDt, cutoffHour = 4)
        // Verify no crash and range calculates correctly
        val (start, end) = provider.getDayRange(logicalDate, cutoffHour = 4)
        assertTrue(end > start)
    }
}
