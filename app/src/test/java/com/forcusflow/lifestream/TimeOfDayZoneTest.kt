package com.forcusflow.lifestream

import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.viewmodel.TimeOfDayZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimeOfDayZoneTest {

    private fun getCurrentZone(time: LocalTime): TimeOfDayZone {
        val hour = time.hour
        return when {
            hour in 4..11 -> TimeOfDayZone.MORNING
            hour in 12..16 -> TimeOfDayZone.AFTERNOON
            else -> TimeOfDayZone.EVENING_NIGHT
        }
    }

    private fun isTemplateActive(templateZone: String?, time: LocalTime): Boolean {
        if (templateZone == null || templateZone == "ALL_DAY" || templateZone.isBlank()) return true
        val zone = getCurrentZone(time)
        return zone.code == templateZone
    }

    @Test
    fun testMorningZoneBoundaries() {
        assertEquals(TimeOfDayZone.MORNING, getCurrentZone(LocalTime.of(4, 0)))
        assertEquals(TimeOfDayZone.MORNING, getCurrentZone(LocalTime.of(8, 30)))
        assertEquals(TimeOfDayZone.MORNING, getCurrentZone(LocalTime.of(11, 59)))
    }

    @Test
    fun testAfternoonZoneBoundaries() {
        assertEquals(TimeOfDayZone.AFTERNOON, getCurrentZone(LocalTime.of(12, 0)))
        assertEquals(TimeOfDayZone.AFTERNOON, getCurrentZone(LocalTime.of(14, 15)))
        assertEquals(TimeOfDayZone.AFTERNOON, getCurrentZone(LocalTime.of(16, 59)))
    }

    @Test
    fun testEveningNightZoneBoundaries() {
        assertEquals(TimeOfDayZone.EVENING_NIGHT, getCurrentZone(LocalTime.of(17, 0)))
        assertEquals(TimeOfDayZone.EVENING_NIGHT, getCurrentZone(LocalTime.of(21, 0)))
        assertEquals(TimeOfDayZone.EVENING_NIGHT, getCurrentZone(LocalTime.of(23, 59)))
        assertEquals(TimeOfDayZone.EVENING_NIGHT, getCurrentZone(LocalTime.of(0, 0)))
        assertEquals(TimeOfDayZone.EVENING_NIGHT, getCurrentZone(LocalTime.of(3, 59)))
    }

    @Test
    fun testAllDayTemplateIsAlwaysActive() {
        val template = TemplateEntity(
            title = "水分補給",
            type = "INTERVAL",
            intervalDays = 1,
            timeOfDayZone = "ALL_DAY"
        )
        assertTrue(isTemplateActive(template.timeOfDayZone, LocalTime.of(6, 0)))
        assertTrue(isTemplateActive(template.timeOfDayZone, LocalTime.of(13, 0)))
        assertTrue(isTemplateActive(template.timeOfDayZone, LocalTime.of(20, 0)))
        assertTrue(isTemplateActive(template.timeOfDayZone, LocalTime.of(1, 0)))
    }

    @Test
    fun testMorningTemplateActiveOnlyInMorning() {
        val template = TemplateEntity(
            title = "朝のストレッチ",
            type = "INTERVAL",
            intervalDays = 1,
            timeOfDayZone = "MORNING"
        )
        assertTrue(isTemplateActive(template.timeOfDayZone, LocalTime.of(7, 30)))
        assertFalse(isTemplateActive(template.timeOfDayZone, LocalTime.of(13, 0)))
        assertFalse(isTemplateActive(template.timeOfDayZone, LocalTime.of(20, 0)))
    }

    @Test
    fun testAfternoonTemplateActiveOnlyInAfternoon() {
        val template = TemplateEntity(
            title = "昼のウォーキング",
            type = "INTERVAL",
            intervalDays = 1,
            timeOfDayZone = "AFTERNOON"
        )
        assertFalse(isTemplateActive(template.timeOfDayZone, LocalTime.of(8, 0)))
        assertTrue(isTemplateActive(template.timeOfDayZone, LocalTime.of(13, 30)))
        assertFalse(isTemplateActive(template.timeOfDayZone, LocalTime.of(19, 0)))
    }

    @Test
    fun testEveningNightTemplateActiveOnlyInEveningNight() {
        val template = TemplateEntity(
            title = "日記を書く",
            type = "INTERVAL",
            intervalDays = 1,
            timeOfDayZone = "EVENING_NIGHT"
        )
        assertFalse(isTemplateActive(template.timeOfDayZone, LocalTime.of(8, 0)))
        assertFalse(isTemplateActive(template.timeOfDayZone, LocalTime.of(13, 0)))
        assertTrue(isTemplateActive(template.timeOfDayZone, LocalTime.of(21, 30)))
        assertTrue(isTemplateActive(template.timeOfDayZone, LocalTime.of(1, 0)))
    }
}
