package com.isotjs.todosian.ui.home

import com.isotjs.todosian.data.settings.AppSettings
import com.isotjs.todosian.data.settings.NewTodoFilePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeViewModelTest {

    private val existingLines = listOf("# Notes", "- [ ] Existing task")

    @Test
    fun `blank quick add text does not create a task`() {
        val result = addQuickTodoToLines(existingLines, " \t ", AppSettings())

        assertNull(result)
    }

    @Test
    fun `quick add inserts at the top when configured`() {
        val result = addQuickTodoToLines(
            lines = existingLines,
            text = "  New task  ",
            settings = AppSettings(newTodoFilePosition = NewTodoFilePosition.TOP),
        )

        assertEquals(
            listOf("- [ ] New task", "# Notes", "- [ ] Existing task"),
            result,
        )
    }

    @Test
    fun `quick add inserts at the bottom by default`() {
        val result = addQuickTodoToLines(
            lines = existingLines,
            text = "  New task  ",
            settings = AppSettings(),
        )

        assertEquals(
            listOf("# Notes", "- [ ] Existing task", "- [ ] New task"),
            result,
        )
    }
}
