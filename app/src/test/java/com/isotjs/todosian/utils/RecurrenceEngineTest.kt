package com.isotjs.todosian.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceEngineTest {

    private fun next(
        rule: String,
        due: String? = null,
        scheduled: String? = null,
        start: String? = null,
        today: LocalDate = LocalDate.of(2024, 3, 15), // a Friday
    ) = RecurrenceEngine.nextOccurrence(
        ruleText = rule,
        dueDate = due,
        scheduledDate = scheduled,
        startDate = start,
        today = today,
    )

    @Test
    fun `every day advances due date by one day`() {
        val result = next("every day", due = "2024-03-10")
        assertEquals("2024-03-11", result?.dueDate?.toString())
        assertNull(result?.scheduledDate)
        assertNull(result?.startDate)
    }

    @Test
    fun `every day case insensitive`() {
        val result = next("Every Day", due = "2024-03-10")
        assertEquals("2024-03-11", result?.dueDate?.toString())
    }

    @Test
    fun `every day when done uses today as base`() {
        val today = LocalDate.of(2024, 3, 15)
        val result = next("every day when done", due = "2024-03-10", today = today)
        assertEquals("2024-03-16", result?.dueDate?.toString())
    }

    @Test
    fun `every 3 days advances by 3 days`() {
        val result = next("every 3 days", due = "2024-03-10")
        assertEquals("2024-03-13", result?.dueDate?.toString())
    }

    @Test
    fun `every 1 day same as every day`() {
        val result = next("every 1 day", due = "2024-03-10")
        assertEquals("2024-03-11", result?.dueDate?.toString())
    }

    @Test
    fun `every weekday skips weekend from Friday`() {
        val result = next("every weekday", due = "2024-03-15")
        assertEquals("2024-03-18", result?.dueDate?.toString())
    }

    @Test
    fun `every weekday skips weekend from Thursday`() {
        val result = next("every weekday", due = "2024-03-14")
        assertEquals("2024-03-15", result?.dueDate?.toString())
    }

    @Test
    fun `every weekend from Friday lands on Saturday`() {
        val result = next("every weekend", due = "2024-03-15")
        assertEquals("2024-03-16", result?.dueDate?.toString())
    }

    @Test
    fun `every weekend from Saturday lands on next Sunday`() {
        val result = next("every weekend", due = "2024-03-16")
        assertEquals("2024-03-17", result?.dueDate?.toString())
    }

    @Test
    fun `every week adds 7 days`() {
        val result = next("every week", due = "2024-03-10")
        assertEquals("2024-03-17", result?.dueDate?.toString())
    }

    @Test
    fun `every 2 weeks adds 14 days`() {
        val result = next("every 2 weeks", due = "2024-03-10")
        assertEquals("2024-03-24", result?.dueDate?.toString())
    }

    @Test
    fun `every week on Monday from a Wednesday`() {
        val result = next("every week on Monday", due = "2024-03-13")
        assertEquals("2024-03-18", result?.dueDate?.toString())
    }

    @Test
    fun `every week on Saturday Sunday returns earlier of the two in next period`() {
        val result = next("every week on Saturday, Sunday", due = "2024-03-11")
        assertEquals("2024-03-23", result?.dueDate?.toString())
    }

    @Test
    fun `every month advances by one month`() {
        val result = next("every month", due = "2024-03-10")
        assertEquals("2024-04-10", result?.dueDate?.toString())
    }

    @Test
    fun `every 3 months advances by 3 months`() {
        val result = next("every 3 months", due = "2024-01-31")
        assertEquals("2024-04-30", result?.dueDate?.toString())
    }

    @Test
    fun `every month on the 15th`() {
        val result = next("every month on the 15th", due = "2024-03-10")
        assertEquals("2024-04-15", result?.dueDate?.toString())
    }

    @Test
    fun `every month on the last`() {
        val result = next("every month on the last", due = "2024-03-31")
        assertEquals("2024-04-30", result?.dueDate?.toString())
    }

    @Test
    fun `every month on the last Friday`() {
        val result = next("every month on the last Friday", due = "2024-03-29")
        assertEquals("2024-04-26", result?.dueDate?.toString())
    }

    @Test
    fun `every year advances by one year`() {
        val result = next("every year", due = "2024-03-10")
        assertEquals("2025-03-10", result?.dueDate?.toString())
    }

    @Test
    fun `every 2 years advances by two years`() {
        val result = next("every 2 years", due = "2024-03-10")
        assertEquals("2026-03-10", result?.dueDate?.toString())
    }

    @Test
    fun `every year on Feb 29 advances to Feb 28 in non leap year`() {
        val result = next("every year", due = "2024-02-29")
        assertEquals("2025-02-28", result?.dueDate?.toString())
    }

    @Test
    fun `advancing multiple dates preserves relative offsets`() {
        val result = next(
            "every week",
            due = "2024-03-15",
            scheduled = "2024-03-13",
            start = "2024-03-10",
        )
        assertEquals("2024-03-22", result?.dueDate?.toString())
        assertEquals("2024-03-20", result?.scheduledDate?.toString())
        assertEquals("2024-03-17", result?.startDate?.toString())
    }

    @Test
    fun `when only start date present it becomes reference`() {
        val result = next("every week", start = "2024-03-10")
        assertEquals("2024-03-17", result?.startDate?.toString())
        assertNull(result?.dueDate)
        assertNull(result?.scheduledDate)
    }

    @Test
    fun `when only scheduled date present it becomes reference`() {
        val result = next("every week", scheduled = "2024-03-10")
        assertEquals("2024-03-17", result?.scheduledDate?.toString())
        assertNull(result?.dueDate)
        assertNull(result?.startDate)
    }

    @Test
    fun `due date wins over scheduled and start as reference`() {
        val result = next(
            "every month",
            due = "2024-03-20",
            scheduled = "2024-03-18",
            start = "2024-03-15",
        )
        assertEquals("2024-04-20", result?.dueDate?.toString())
        assertEquals("2024-04-18", result?.scheduledDate?.toString())
        assertEquals("2024-04-15", result?.startDate?.toString())
    }

    @Test
    fun `no dates returns occurrence with null dates but non null result`() {
        val result = next("every day")
        assertEquals(null, result?.dueDate)
        assertEquals(null, result?.scheduledDate)
        assertEquals(null, result?.startDate)
    }

    @Test
    fun `unrecognised rule returns null`() {
        val result = next("banana")
        assertNull(result)
    }

    @Test
    fun `empty rule returns null`() {
        val result = next("")
        assertNull(result)
    }

    @Test
    fun `every week when done uses today`() {
        val today = LocalDate.of(2024, 3, 20) // Wednesday
        val result = next("every week when done", due = "2024-03-01", today = today)
        assertEquals("2024-03-27", result?.dueDate?.toString())
    }

    @Test
    fun `every month when done uses today`() {
        val today = LocalDate.of(2024, 3, 15)
        val result = next("every month when done", due = "2024-02-01", today = today)
        assertEquals("2024-04-15", result?.dueDate?.toString())
    }

    @Test
    fun `every month clamps Jan 31 to Feb 28`() {
        val result = next("every month", due = "2024-01-31")
        assertEquals("2024-02-29", result?.dueDate?.toString())
    }

    @Test
    fun `every month clamps Jan 31 to Feb 28 non-leap`() {
        val result = next("every month", due = "2023-01-31")
        assertEquals("2023-02-28", result?.dueDate?.toString())
    }
}
