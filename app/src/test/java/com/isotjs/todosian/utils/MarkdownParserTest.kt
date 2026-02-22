package com.isotjs.todosian.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MarkdownParserTest {
    @Test
    fun parse_ignores_non_todo_lines_and_preserves_line_index() {
        val lines = listOf(
            "# Header",
            "- [ ] First",
            "not a todo",
            "- [x] Done one",
            "- [ ] Second",
        )

        val todos = MarkdownParser.parse(lines)

        assertEquals(3, todos.size)
        assertEquals("First", todos[0].text)
        assertEquals(false, todos[0].isDone)
        assertEquals(1, todos[0].lineIndex)

        assertEquals("Done one", todos[1].text)
        assertEquals(true, todos[1].isDone)
        assertEquals(3, todos[1].lineIndex)

        assertEquals("Second", todos[2].text)
        assertEquals(false, todos[2].isDone)
        assertEquals(4, todos[2].lineIndex)
    }

    @Test
    fun toggleLine_toggles_only_target_line() {
        val lines = listOf(
            "Intro",
            "- [ ] Task",
            "Outro",
        )

        val toggled = MarkdownParser.toggleLine(lines, lineIndex = 1, enableTasksPlugin = false)

        assertEquals("Intro", toggled[0])
        assertEquals("- [x] Task", toggled[1])
        assertEquals("Outro", toggled[2])
    }

    @Test
    fun addTodo_appends_todo_line() {
        val lines = listOf(
            "# Notes",
            "Some text",
        )

        val updated = MarkdownParser.addTodo(lines, text = "New thing")

        assertEquals(3, updated.size)
        assertEquals("# Notes", updated[0])
        assertEquals("Some text", updated[1])
        assertEquals("- [ ] New thing", updated[2])
    }

    @Test
    fun deleteTodo_removes_exact_line() {
        val lines = listOf(
            "Keep",
            "- [ ] Remove me",
            "Keep too",
        )

        val updated = MarkdownParser.deleteTodo(lines, lineIndex = 1)

        assertEquals(listOf("Keep", "Keep too"), updated)
    }

    @Test
    fun editTodoText_preserves_checkbox_state() {
        val lines = listOf(
            "- [x] Old",
        )

        val updated = MarkdownParser.editTodoText(lines, lineIndex = 0, newText = "New")
        assertEquals(listOf("- [x] New"), updated)
    }

    @Test
    fun tryEditTodoText_returns_null_for_non_todo_line() {
        val lines = listOf(
            "Not a todo",
        )

        val updated = MarkdownParser.tryEditTodoText(lines, lineIndex = 0, newText = "X")
        assertEquals(null, updated)
    }

    @Test
    fun parse_extracts_tasks_plugin_metadata() {
        val lines = listOf(
            "- [ ] Test task ➕ 2023-04-13 🛫 2023-04-15 ⏳ 2023-04-14 📅 2023-04-16 🔁 every day 🔼",
        )

        val todos = MarkdownParser.parse(lines)
        assertEquals(1, todos.size)
        val todo = todos[0]
        assertEquals("Test task", todo.text)
        assertEquals("2023-04-13", todo.createdDate)
        assertEquals("2023-04-15", todo.startDate)
        assertEquals("2023-04-14", todo.scheduledDate)
        assertEquals("2023-04-16", todo.dueDate)
        assertEquals("every day", todo.recurrence)
        assertEquals(com.isotjs.todosian.data.model.TasksPriority.MEDIUM, todo.priority)
    }

    @Test
    fun toggleLine_when_enabled_adds_done_date_and_preserves_other_meta() {
        val today = LocalDate.now().toString()
        val lines = listOf(
            "- [ ] Task 📅 2024-01-02 🔼",
        )

        val toggled = MarkdownParser.toggleLine(lines, lineIndex = 0, enableTasksPlugin = true)
        assertEquals("- [x] Task 📅 2024-01-02 🔼 ✅ $today", toggled[0])
    }

    @Test
    fun toggleLine_when_enabled_removes_only_done_date_when_unchecking() {
        val lines = listOf(
            "- [x] Task 📅 2024-01-02 ✅ 2024-01-03 🔼",
        )

        val toggled = MarkdownParser.toggleLine(lines, lineIndex = 0, enableTasksPlugin = true)
        assertEquals("- [ ] Task 📅 2024-01-02 🔼", toggled[0])
    }

    @Test
    fun addTodo_when_enabled_sets_created_date_by_default() {
        val today = LocalDate.now().toString()
        val updated = MarkdownParser.addTodo(
            lines = emptyList(),
            text = "New thing",
            meta = null,
            enableTasksPlugin = true,
        )

        assertEquals(listOf("- [ ] New thing ➕ $today"), updated)
    }
}
