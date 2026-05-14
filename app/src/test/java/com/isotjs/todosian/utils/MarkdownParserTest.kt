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
        assertEquals("", todos[0].indentPrefix)
        assertEquals(0, todos[0].indentLevel)

        assertEquals("Done one", todos[1].text)
        assertEquals(true, todos[1].isDone)
        assertEquals(3, todos[1].lineIndex)
        assertEquals("", todos[1].indentPrefix)
        assertEquals(0, todos[1].indentLevel)

        assertEquals("Second", todos[2].text)
        assertEquals(false, todos[2].isDone)
        assertEquals(4, todos[2].lineIndex)
        assertEquals("", todos[2].indentPrefix)
        assertEquals(0, todos[2].indentLevel)
    }

    @Test
    fun parse_captures_indented_todo_lines() {
        val lines = listOf(
            "- [ ] Parent",
            "  - [ ] Child",
            "\t- [x] Tabbed child",
        )

        val todos = MarkdownParser.parse(lines)

        assertEquals(3, todos.size)
        assertEquals("", todos[0].indentPrefix)
        assertEquals(0, todos[0].indentLevel)
        assertEquals("  ", todos[1].indentPrefix)
        assertEquals(1, todos[1].indentLevel)
        assertEquals("\t", todos[2].indentPrefix)
        assertEquals(2, todos[2].indentLevel)
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
    fun toggleLine_preserves_indent_prefix() {
        val lines = listOf(
            "  - [ ] Task",
        )

        val toggled = MarkdownParser.toggleLine(lines, lineIndex = 0, enableTasksPlugin = false)

        assertEquals("  - [x] Task", toggled[0])
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
    fun addSubTodo_inserts_after_parent_block() {
        val lines = listOf(
            "- [ ] Parent",
            "  - [ ] Child",
            "- [ ] Sibling",
        )

        val updated = MarkdownParser.addSubTodo(
            lines = lines,
            parentLineIndex = 0,
            text = "New child",
        ) ?: error("Expected subtask insert")

        assertEquals(
            listOf(
                "- [ ] Parent",
                "  - [ ] Child",
                "  - [ ] New child",
                "- [ ] Sibling",
            ),
            updated,
        )
    }

    @Test
    fun addSubTodo_uses_parent_indent_unit() {
        val lines = listOf(
            "\t- [ ] Parent",
            "\t\t- [ ] Child",
        )

        val updated = MarkdownParser.addSubTodo(
            lines = lines,
            parentLineIndex = 0,
            text = "Another",
        ) ?: error("Expected subtask insert")

        assertEquals("\t\t- [ ] Another", updated[2])
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
    fun editTodoText_preserves_indent_prefix() {
        val lines = listOf(
            "    - [x] Old",
        )

        val updated = MarkdownParser.editTodoText(lines, lineIndex = 0, newText = "New")
        assertEquals(listOf("    - [x] New"), updated)
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

    @Test
    fun tryMoveTodoLine_moves_only_todo_line_and_preserves_target() {
        val source = listOf(
            "# Header",
            "- [ ] Move me",
            "Keep",
        )
        val target = listOf(
            "Intro",
            "- [x] Existing",
        )

        val result = MarkdownParser.tryMoveTodoLine(
            sourceLines = source,
            lineIndex = 1,
            targetLines = target,
        )

        val (newSource, newTarget) = result ?: error("Expected move to succeed")
        assertEquals(listOf("# Header", "Keep"), newSource)
        assertEquals(listOf("Intro", "- [x] Existing", "- [ ] Move me"), newTarget)
    }

    @Test
    fun tryMoveTodoLine_moves_block_with_subtasks() {
        val source = listOf(
            "- [ ] Parent",
            "  - [ ] Child",
            "- [ ] Sibling",
        )
        val target = listOf("Intro")

        val result = MarkdownParser.tryMoveTodoLine(
            sourceLines = source,
            lineIndex = 0,
            targetLines = target,
        )

        val (newSource, newTarget) = result ?: error("Expected move to succeed")
        assertEquals(listOf("- [ ] Sibling"), newSource)
        assertEquals(listOf("Intro", "- [ ] Parent", "  - [ ] Child"), newTarget)
    }

    @Test
    fun tryCopyTodoLine_keeps_source_intact_and_appends_to_target() {
        val source = listOf(
            "- [ ] Copy me",
        )
        val target = listOf("Other")

        val result = MarkdownParser.tryCopyTodoLine(
            sourceLines = source,
            lineIndex = 0,
            targetLines = target,
        )

        val (newSource, newTarget) = result ?: error("Expected copy to succeed")
        assertEquals(listOf("- [ ] Copy me"), newSource)
        assertEquals(listOf("Other", "- [ ] Copy me"), newTarget)
    }

    @Test
    fun tryMoveTodoLine_returns_null_for_non_todo_source() {
        val source = listOf("Not a todo")
        val target = listOf("- [ ] Valid")

        val result = MarkdownParser.tryMoveTodoLine(
            sourceLines = source,
            lineIndex = 0,
            targetLines = target,
        )

        assertEquals(null, result)
    }

    @Test
    fun tryDeleteTodoWithSubtasks_removes_nested_block() {
        val lines = listOf(
            "- [ ] Parent",
            "  - [ ] Child",
            "  - [ ] Child two",
            "- [ ] Sibling",
        )

        val updated = MarkdownParser.tryDeleteTodoWithSubtasks(lines, lineIndex = 0)
            ?: error("Expected delete to succeed")

        assertEquals(listOf("- [ ] Sibling"), updated)
    }

    @Test
    fun hasSubtasks_returns_true_when_next_is_more_indented() {
        val lines = listOf(
            "- [ ] Parent",
            "  - [ ] Child",
        )

        assertEquals(true, MarkdownParser.hasSubtasks(lines, lineIndex = 0))
        assertEquals(false, MarkdownParser.hasSubtasks(lines, lineIndex = 1))
    }
    
    @Test
    fun toggleLine_recurring_daily_inserts_new_open_task_above() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Water plants 📅 2024-03-14 🔁 every day",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(2, result.size)
        assertEquals("- [ ] Water plants 📅 2024-03-15 🔁 every day", result[0])
        assertEquals("- [x] Water plants 📅 2024-03-14 🔁 every day ✅ 2024-03-15", result[1])
    }

    @Test
    fun toggleLine_recurring_weekly_inserts_new_task_with_advanced_due_date() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Weekly review 📅 2024-03-08 🔁 every week",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(2, result.size)
        assertEquals("- [ ] Weekly review 📅 2024-03-15 🔁 every week", result[0])
        assertEquals("- [x] Weekly review 📅 2024-03-08 🔁 every week ✅ 2024-03-15", result[1])
    }

    @Test
    fun toggleLine_recurring_preserves_surrounding_non_todo_lines() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "# Heading",
            "- [ ] Task 📅 2024-03-14 🔁 every day",
            "Some note",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 1,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(4, result.size)
        assertEquals("# Heading", result[0])
        assertEquals("- [ ] Task 📅 2024-03-15 🔁 every day", result[1])
        assertEquals("- [x] Task 📅 2024-03-14 🔁 every day ✅ 2024-03-15", result[2])
        assertEquals("Some note", result[3])
    }

    @Test
    fun toggleLine_recurring_preserves_priority_on_new_task() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Important task 📅 2024-03-10 🔁 every month 🔺",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(2, result.size)
        assertEquals("- [ ] Important task 📅 2024-04-10 🔁 every month 🔺", result[0])
    }

    @Test
    fun toggleLine_recurring_updates_created_date_on_new_task() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Daily habit ➕ 2024-01-01 📅 2024-03-14 🔁 every day",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(2, result.size)
        assertEquals(
            "- [ ] Daily habit ➕ 2024-03-15 📅 2024-03-15 🔁 every day",
            result[0],
        )
    }

    @Test
    fun toggleLine_recurring_when_done_uses_today_as_base() {
        val today = LocalDate.of(2024, 3, 20) // Wednesday; task was late
        val lines = listOf(
            "- [ ] Catch-up 📅 2024-03-10 🔁 every week when done",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(2, result.size)
        assertEquals("- [ ] Catch-up 📅 2024-03-27 🔁 every week when done", result[0])
    }

    @Test
    fun toggleLine_non_recurring_task_not_affected_by_recurrence_logic() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Simple task 📅 2024-03-14",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(1, result.size)
        assertEquals("- [x] Simple task 📅 2024-03-14 ✅ 2024-03-15", result[0])
    }

    @Test
    fun toggleLine_plugin_disabled_ignores_recurrence_field() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Daily 📅 2024-03-14 🔁 every day",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = false,
            today = today,
        )

        assertEquals(1, result.size)
        assertEquals("- [x] Daily 📅 2024-03-14 🔁 every day", result[0])
    }

    @Test
    fun toggleLine_unchecking_recurring_done_task_removes_done_date_only() {
        val today = LocalDate.of(2024, 3, 16)
        val lines = listOf(
            "- [x] Daily 📅 2024-03-15 🔁 every day ✅ 2024-03-15",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(1, result.size)
        assertEquals("- [ ] Daily 📅 2024-03-15 🔁 every day", result[0])
    }

    @Test
    fun toggleLine_recurring_with_indented_task_preserves_indent() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "  - [ ] Sub-task 📅 2024-03-14 🔁 every day",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(2, result.size)
        assertEquals("  - [ ] Sub-task 📅 2024-03-15 🔁 every day", result[0])
        assertEquals("  - [x] Sub-task 📅 2024-03-14 🔁 every day ✅ 2024-03-15", result[1])
    }

    @Test
    fun toggleLine_recurring_multi_date_preserves_offsets() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Multi-date 🛫 2024-03-15 ⏳ 2024-03-18 📅 2024-03-20 🔁 every week",
        )

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(2, result.size)
        assertEquals(
            "- [ ] Multi-date 🛫 2024-03-22 ⏳ 2024-03-25 📅 2024-03-27 🔁 every week",
            result[0],
        )
    }
}
