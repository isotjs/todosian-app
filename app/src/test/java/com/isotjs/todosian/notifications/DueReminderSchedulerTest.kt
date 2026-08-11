package com.isotjs.todosian.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DueReminderSchedulerTest {

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("UTC"))

    @Test
    fun `same day when time is in the future`() {
        val now = utc(2026, 8, 10, 12, 0)
        val expected = utc(2026, 8, 10, 19, 0).toInstant().toEpochMilli()
        assertEquals(expected, DueReminderScheduler.nextTriggerMillis(19, 0, now))
    }

    @Test
    fun `next day when time has already passed`() {
        val now = utc(2026, 8, 10, 20, 30)
        val expected = utc(2026, 8, 11, 19, 0).toInstant().toEpochMilli()
        assertEquals(expected, DueReminderScheduler.nextTriggerMillis(19, 0, now))
    }

    @Test
    fun `next day when time is exactly now`() {
        val now = utc(2026, 8, 10, 19, 0)
        val expected = utc(2026, 8, 11, 19, 0).toInstant().toEpochMilli()
        assertEquals(expected, DueReminderScheduler.nextTriggerMillis(19, 0, now))
    }

    @Test
    fun `handles minute component`() {
        val now = utc(2026, 8, 10, 21, 45)
        val expected = utc(2026, 8, 11, 8, 15).toInstant().toEpochMilli()
        assertEquals(expected, DueReminderScheduler.nextTriggerMillis(8, 15, now))
    }
}
