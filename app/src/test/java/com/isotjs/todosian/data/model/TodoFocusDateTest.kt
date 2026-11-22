package com.isotjs.todosian.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoFocusDateTest {

    private fun todo(due: String? = null, scheduled: String? = null) =
        Todo(id = "1", text = "t", isDone = false, lineIndex = 0, dueDate = due, scheduledDate = scheduled)

    @Test
    fun usesEarliestOfDueAndScheduled() {
        assertNull(todo().focusDate)
        assertEquals("2026-11-16", todo(due = "2026-11-16").focusDate)
        assertEquals("2026-11-16", todo(scheduled = "2026-11-16").focusDate)
        assertEquals("2026-11-10", todo(due = "2026-11-20", scheduled = "2026-11-10").focusDate)
        assertEquals("2026-11-10", todo(due = "2026-11-10", scheduled = "2026-11-20").focusDate)
    }
}
