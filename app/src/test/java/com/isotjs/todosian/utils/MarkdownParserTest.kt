package com.isotjs.todosian.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MarkdownParserTest {
    private val customMarkers = TaskMarkers(
        created = "created:",
        start = "start:",
        scheduled = "scheduled:",
        due = "due:",
        done = "done:",
        recurrence = "repeat:",
    )

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
        assertEquals(0, todos[0].indentLevel)

        assertEquals("Done one", todos[1].text)
        assertEquals(true, todos[1].isDone)
        assertEquals(3, todos[1].lineIndex)
        assertEquals(0, todos[1].indentLevel)

        assertEquals("Second", todos[2].text)
        assertEquals(false, todos[2].isDone)
        assertEquals(4, todos[2].lineIndex)
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
        assertEquals(0, todos[0].indentLevel)
        assertEquals(1, todos[1].indentLevel)
        assertEquals(2, todos[2].indentLevel)
    }

    @Test
    fun parse_keeps_ids_stable_when_external_edits_shift_lines() {
        val before = MarkdownParser.parse(listOf(
            "- [ ] Parent",
            "  - [ ] Child",
        ))
        val after = MarkdownParser.parse(listOf(
            "# Added externally",
            "",
            "- [ ] Parent",
            "  - [ ] Child",
        ))

        assertEquals(before.map { it.id }, after.map { it.id })
    }

    @Test
    fun parse_keeps_id_stable_when_todo_is_completed() {
        val open = MarkdownParser.parse(listOf("- [ ] Task")).single()
        val done = MarkdownParser.parse(listOf("- [x] Task ✅ 2024-03-15")).single()

        assertEquals(open.id, done.id)
    }

    @Test
    fun parse_generates_unique_ids_for_duplicate_todos() {
        val todos = MarkdownParser.parse(listOf(
            "- [ ] Same",
            "- [ ] Same",
        ))

        assertEquals(2, todos.map { it.id }.toSet().size)
    }

    @Test
    fun toggleLineWithSubtasks_toggles_only_target_line() {
        val lines = listOf(
            "Intro",
            "- [ ] Task",
            "Outro",
        )

        val toggled = MarkdownParser.toggleLineWithSubtasks(lines, lineIndex = 1, enableTasksPlugin = false)

        assertEquals("Intro", toggled[0])
        assertEquals("- [x] Task", toggled[1])
        assertEquals("Outro", toggled[2])
    }

    @Test
    fun toggleLineWithSubtasks_preserves_indent_prefix() {
        val lines = listOf(
            "  - [ ] Task",
        )

        val toggled = MarkdownParser.toggleLineWithSubtasks(lines, lineIndex = 0, enableTasksPlugin = false)

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
    fun addTodo_prepends_todo_line_when_addAtStart() {
        val lines = listOf(
            "# Notes",
            "Some text",
        )

        val updated = MarkdownParser.addTodo(lines, text = "New thing", addAtStart = true)

        assertEquals(3, updated.size)
        assertEquals("- [ ] New thing", updated[0])
        assertEquals("# Notes", updated[1])
        assertEquals("Some text", updated[2])
    }

    @Test
    fun addTodo_prepends_todo_line_when_addAtStart_with_empty_file() {
        val updated = MarkdownParser.addTodo(emptyList(), text = "New thing", addAtStart = true)

        assertEquals(listOf("- [ ] New thing"), updated)
    }

    @Test
    fun addTodo_inserts_after_frontmatter_when_addAtStart() {
        val lines = listOf(
            "---",
            "tags: [daily]",
            "---",
            "# Notes",
            "Some text",
        )

        val updated = MarkdownParser.addTodo(lines, text = "New thing", addAtStart = true)

        assertEquals(6, updated.size)
        assertEquals("---", updated[0])
        assertEquals("tags: [daily]", updated[1])
        assertEquals("---", updated[2])
        assertEquals("- [ ] New thing", updated[3])
        assertEquals("# Notes", updated[4])
        assertEquals("Some text", updated[5])
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

        val updated = MarkdownParser.tryDeleteTodoWithSubtasks(lines, lineIndex = 1)

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
    fun toggleLineWithSubtasks_when_enabled_adds_done_date_and_preserves_other_meta() {
        val today = LocalDate.now().toString()
        val lines = listOf(
            "- [ ] Task 📅 2024-01-02 🔼",
        )

        val toggled = MarkdownParser.toggleLineWithSubtasks(lines, lineIndex = 0, enableTasksPlugin = true)
        assertEquals("- [x] Task 📅 2024-01-02 🔼 ✅ $today", toggled[0])
    }

    @Test
    fun toggleLineWithSubtasks_when_enabled_removes_only_done_date_when_unchecking() {
        val lines = listOf(
            "- [x] Task 📅 2024-01-02 ✅ 2024-01-03 🔼",
        )

        val toggled = MarkdownParser.toggleLineWithSubtasks(lines, lineIndex = 0, enableTasksPlugin = true)
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
    fun hasSubtasks_finds_subtasks_after_notes_and_blank_lines() {
        val lines = listOf(
            "- [ ] Parent",
            "  A note for the parent",
            "",
            "  - [ ] Child",
        )

        assertEquals(true, MarkdownParser.hasSubtasks(lines, lineIndex = 0))
    }

    @Test
    fun hasSubtasks_returns_false_when_block_contains_only_notes() {
        val lines = listOf(
            "- [ ] Parent",
            "  A note for the parent",
            "- [ ] Sibling",
        )

        assertEquals(false, MarkdownParser.hasSubtasks(lines, lineIndex = 0))
    }

    @Test
    fun addSubTodo_inserts_after_notes_and_existing_subtasks() {
        val lines = listOf(
            "- [ ] Parent",
            "  A note for the parent",
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
                "  A note for the parent",
                "  - [ ] Child",
                "  - [ ] New child",
                "- [ ] Sibling",
            ),
            updated,
        )
    }

    @Test
    fun tryDeleteTodoWithSubtasks_removes_notes_and_subtasks() {
        val lines = listOf(
            "- [ ] Parent",
            "  A note for the parent",
            "  - [ ] Child",
            "- [ ] Sibling",
        )

        val updated = MarkdownParser.tryDeleteTodoWithSubtasks(lines, lineIndex = 0)
            ?: error("Expected delete to succeed")

        assertEquals(listOf("- [ ] Sibling"), updated)
    }
    
    @Test
    fun toggleLineWithSubtasks_recurring_daily_inserts_new_open_task_above() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Water plants 📅 2024-03-14 🔁 every day",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
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
    fun toggleLineWithSubtasks_recurring_weekly_inserts_new_task_with_advanced_due_date() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Weekly review 📅 2024-03-08 🔁 every week",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
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
    fun toggleLineWithSubtasks_recurring_preserves_surrounding_non_todo_lines() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "# Heading",
            "- [ ] Task 📅 2024-03-14 🔁 every day",
            "Some note",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
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
    fun toggleLineWithSubtasks_recurring_preserves_priority_on_new_task() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Important task 📅 2024-03-10 🔁 every month 🔺",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(2, result.size)
        assertEquals("- [ ] Important task 📅 2024-04-10 🔁 every month 🔺", result[0])
    }

    @Test
    fun toggleLineWithSubtasks_recurring_updates_created_date_on_new_task() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Daily habit ➕ 2024-01-01 📅 2024-03-14 🔁 every day",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
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
    fun toggleLineWithSubtasks_recurring_when_done_uses_today_as_base() {
        val today = LocalDate.of(2024, 3, 20) // Wednesday; task was late
        val lines = listOf(
            "- [ ] Catch-up 📅 2024-03-10 🔁 every week when done",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(2, result.size)
        assertEquals("- [ ] Catch-up 📅 2024-03-27 🔁 every week when done", result[0])
    }

    @Test
    fun toggleLineWithSubtasks_non_recurring_task_not_affected_by_recurrence_logic() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Simple task 📅 2024-03-14",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(1, result.size)
        assertEquals("- [x] Simple task 📅 2024-03-14 ✅ 2024-03-15", result[0])
    }

    @Test
    fun toggleLineWithSubtasks_plugin_disabled_ignores_recurrence_field() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Daily 📅 2024-03-14 🔁 every day",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = false,
            today = today,
        )

        assertEquals(1, result.size)
        assertEquals("- [x] Daily 📅 2024-03-14 🔁 every day", result[0])
    }

    @Test
    fun toggleLineWithSubtasks_unchecking_recurring_done_task_removes_done_date_only() {
        val today = LocalDate.of(2024, 3, 16)
        val lines = listOf(
            "- [x] Daily 📅 2024-03-15 🔁 every day ✅ 2024-03-15",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(1, result.size)
        assertEquals("- [ ] Daily 📅 2024-03-15 🔁 every day", result[0])
    }

    @Test
    fun toggleLineWithSubtasks_recurring_with_indented_task_preserves_indent() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "  - [ ] Sub-task 📅 2024-03-14 🔁 every day",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
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
    fun toggleLineWithSubtasks_recurring_multi_date_preserves_offsets() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Multi-date 🛫 2024-03-15 ⏳ 2024-03-18 📅 2024-03-20 🔁 every week",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
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

    @Test
    fun parse_uses_custom_markers() {
        val lines = listOf(
            "- [ ] Test created: 2023-04-13 start: 2023-04-15 due: 2023-04-16 repeat: every day",
        )

        val todos = MarkdownParser.parse(lines, customMarkers)
        val todo = todos.single()
        assertEquals("Test", todo.text)
        assertEquals("2023-04-13", todo.createdDate)
        assertEquals("2023-04-15", todo.startDate)
        assertEquals("2023-04-16", todo.dueDate)
        assertEquals("every day", todo.recurrence)
    }

    @Test
    fun parse_custom_marker_without_space_before_value() {
        val lines = listOf("- [ ] Test due:2023-04-16")

        val todo = MarkdownParser.parse(lines, customMarkers).single()
        assertEquals("2023-04-16", todo.dueDate)
        assertEquals("Test", todo.text)
    }

    @Test
    fun toggleLine_writes_custom_done_marker() {
        val today = LocalDate.now().toString()
        val lines = listOf("- [ ] Task due: 2024-01-02")

        val toggled = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            markers = customMarkers,
        )
        assertEquals("- [x] Task due: 2024-01-02 done: $today", toggled[0])
    }

    @Test
    fun toggleLine_unchecking_removes_custom_done_marker() {
        val lines = listOf("- [x] Task due: 2024-01-02 done: 2024-01-03")

        val toggled = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            markers = customMarkers,
        )
        assertEquals("- [ ] Task due: 2024-01-02", toggled[0])
    }

    @Test
    fun addTodo_writes_custom_created_marker() {
        val today = LocalDate.now().toString()
        val updated = MarkdownParser.addTodo(
            lines = emptyList(),
            text = "New thing",
            meta = null,
            enableTasksPlugin = true,
            markers = customMarkers,
        )

        assertEquals(listOf("- [ ] New thing created: $today"), updated)
    }

    @Test
    fun toggleLine_recurring_with_custom_markers_advances() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf("- [ ] Water plants due: 2024-03-14 repeat: every day")

        val result = MarkdownParser.toggleLine(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
            markers = customMarkers,
        )

        assertEquals(2, result.size)
        assertEquals("- [ ] Water plants due: 2024-03-15 repeat: every day", result[0])
        assertEquals("- [x] Water plants due: 2024-03-14 repeat: every day done: 2024-03-15", result[1])
    }

    @Test
    fun parse_escapes_regex_special_markers() {
        val markers = TaskMarkers(due = "(due)")
        val lines = listOf("- [ ] Test (due) 2024-01-02")

        val todo = MarkdownParser.parse(lines, markers).single()
        assertEquals("2024-01-02", todo.dueDate)
        assertEquals("Test", todo.text)
    }

    @Test
    fun toggleLineWithSubtasks_completes_subtasks_when_parent_checked() {
        val lines = listOf(
            "- [ ] Parent",
            "  - [ ] Child one",
            "    - [ ] Grandchild",
            "  - [x] Child done",
            "- [ ] Sibling",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = false,
        )

        assertEquals(5, result.size)
        assertEquals("- [x] Parent", result[0])
        assertEquals("  - [x] Child one", result[1])
        assertEquals("    - [x] Grandchild", result[2])
        assertEquals("  - [x] Child done", result[3])
        assertEquals("- [ ] Sibling", result[4])
    }

    @Test
    fun toggleLineWithSubtasks_unchecking_parent_leaves_subtasks_unchanged() {
        val lines = listOf(
            "- [x] Parent",
            "  - [x] Child one",
            "  - [ ] Child two",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = false,
        )

        assertEquals("- [ ] Parent", result[0])
        assertEquals("  - [x] Child one", result[1])
        assertEquals("  - [ ] Child two", result[2])
    }

    @Test
    fun toggleLineWithSubtasks_plugin_adds_completion_dates_to_subtasks() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Parent",
            "  - [ ] Child 📅 2024-03-10",
            "  - [x] Child done ✅ 2024-03-01",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(3, result.size)
        assertEquals("- [x] Parent ✅ 2024-03-15", result[0])
        assertEquals("  - [x] Child 📅 2024-03-10 ✅ 2024-03-15", result[1])
        assertEquals("  - [x] Child done ✅ 2024-03-01", result[2])
    }

    @Test
    fun toggleLineWithSubtasks_without_subtasks_toggles_only_target() {
        val lines = listOf(
            "Intro",
            "- [ ] Task",
            "Outro",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 1,
            enableTasksPlugin = false,
        )

        assertEquals(listOf("Intro", "- [x] Task", "Outro"), result)
    }

    @Test
    fun toggleLineWithSubtasks_completes_subtasks_when_notes_come_first() {
        val lines = listOf(
            "- [ ] Parent",
            "  A note for the parent",
            "  - [ ] Child one",
            "    A note for child one",
            "  - [ ] Child two",
            "- [ ] Sibling",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = false,
        )

        assertEquals(
            listOf(
                "- [x] Parent",
                "  A note for the parent",
                "  - [x] Child one",
                "    A note for child one",
                "  - [x] Child two",
                "- [ ] Sibling",
            ),
            result,
        )
    }

    @Test
    fun toggleLineWithSubtasks_skips_blank_lines_inside_the_block() {
        val lines = listOf(
            "- [ ] Parent",
            "  First paragraph",
            "",
            "  Second paragraph",
            "  - [ ] Child",
            "",
            "- [ ] Sibling",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = false,
        )

        assertEquals(
            listOf(
                "- [x] Parent",
                "  First paragraph",
                "",
                "  Second paragraph",
                "  - [x] Child",
                "",
                "- [ ] Sibling",
            ),
            result,
        )
    }

    @Test
    fun toggleLineWithSubtasks_stops_at_same_or_lower_indentation() {
        val lines = listOf(
            "- [ ] Parent",
            "  - [ ] Child",
            "- [ ] Sibling",
            "  - [ ] Sibling child",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = false,
        )

        assertEquals(
            listOf(
                "- [x] Parent",
                "  - [x] Child",
                "- [ ] Sibling",
                "  - [ ] Sibling child",
            ),
            result,
        )
    }

    @Test
    fun toggleLineWithSubtasks_recurring_inserts_new_occurrence_above_block() {
        val today = LocalDate.of(2024, 3, 15)
        val lines = listOf(
            "- [ ] Water plants 📅 2024-03-14 🔁 every day",
            "  - [ ] Get watering can",
            "  - [ ] Check soil",
        )

        val result = MarkdownParser.toggleLineWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = true,
            today = today,
        )

        assertEquals(4, result.size)
        assertEquals("- [ ] Water plants 📅 2024-03-15 🔁 every day", result[0])
        assertEquals("- [x] Water plants 📅 2024-03-14 🔁 every day ✅ 2024-03-15", result[1])
        assertEquals("  - [x] Get watering can ✅ 2024-03-15", result[2])
        assertEquals("  - [x] Check soil ✅ 2024-03-15", result[3])
    }

    @Test
    fun tryToggleTodoWithSubtasks_returns_null_for_non_todo_line() {
        val lines = listOf("# Heading")

        val result = MarkdownParser.tryToggleTodoWithSubtasks(
            lines = lines,
            lineIndex = 0,
            enableTasksPlugin = false,
        )

        assertEquals(null, result)
    }

    @Test
    fun hiddenSubtaskLineIndices_hides_completed_subtask_in_other_section() {
        val all = MarkdownParser.parse(listOf(
            "- [ ] A",
            "  - [x] B done",
            "  - [ ] C",
        ))
        val activeTodos = all.filter { !it.isDone }
        val completedTodos = all.filter { it.isDone }

        val hidden = MarkdownParser.hiddenSubtaskLineIndices(
            todos = activeTodos + completedTodos,
            collapsedTodoIds = setOf(all[0].id),
        )

        assertEquals(setOf(1, 2), hidden)
    }

    @Test
    fun hiddenSubtaskLineIndices_keeps_collapse_after_external_line_shift() {
        val before = MarkdownParser.parse(listOf(
            "- [ ] Parent",
            "  - [ ] Child",
        ))
        val after = MarkdownParser.parse(listOf(
            "# Added externally",
            "",
            "- [ ] Parent",
            "  - [ ] Child",
        ))

        val hidden = MarkdownParser.hiddenSubtaskLineIndices(
            todos = after,
            collapsedTodoIds = setOf(before[0].id),
        )

        assertEquals(setOf(after[1].lineIndex), hidden)
    }

    @Test
    fun parse_captures_indented_notes() {
        val lines = listOf(
            "- [ ] Task-Name",
            "  Everything that is indented is a note for this task",
            "  - list element 1",
            "  - list element 2",
            "  now there are subtasks",
            "  - [ ] subtask 1",
            "    Note belonging to the subtask",
            "  - [ ] subtask 2",
            "- [x] task with no notes",
        )

        val todos = MarkdownParser.parse(lines)

        assertEquals(4, todos.size)
        assertEquals(
            listOf(
                "  Everything that is indented is a note for this task",
                "  - list element 1",
                "  - list element 2",
                "  now there are subtasks",
            ),
            todos[0].notes,
        )
        assertEquals("subtask 1", todos[1].text)
        assertEquals(listOf("    Note belonging to the subtask"), todos[1].notes)
        assertEquals("subtask 2", todos[2].text)
        assertEquals(emptyList<String>(), todos[2].notes)
        assertEquals("task with no notes", todos[3].text)
        assertEquals(emptyList<String>(), todos[3].notes)
    }

    @Test
    fun parse_preserves_interior_blank_lines_in_notes() {
        val lines = listOf(
            "Intro paragraph",
            "",
            "- [ ] Task",
            "  note",
            "",
            "  detached paragraph",
            "  - [ ] Subtask",
            "    subtask note",
        )

        val todos = MarkdownParser.parse(lines)

        assertEquals(2, todos.size)
        assertEquals(listOf("  note", "", "  detached paragraph"), todos[0].notes)
        assertEquals("Subtask", todos[1].text)
        assertEquals(listOf("    subtask note"), todos[1].notes)
    }

    @Test
    fun editTodoNotes_replaces_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  old note",
            "  - [ ] Subtask",
            "- [ ] Other",
        )

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf("new note 1", "new note 2"),
        )

        assertEquals(
            listOf(
                "- [ ] Task",
                "  new note 1",
                "  new note 2",
                "  - [ ] Subtask",
                "- [ ] Other",
            ),
            result,
        )
    }

    @Test
    fun editTodoNotes_preserves_subtask_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  - [ ] Subtask",
            "    subtask note",
            "  task note after subtask",
        )

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf("replacement"),
        )

        assertEquals(
            listOf(
                "- [ ] Task",
                "  replacement",
                "  - [ ] Subtask",
                "    subtask note",
            ),
            result,
        )
    }

    @Test
    fun editTodoNotes_removes_all_notes_when_empty() {
        val lines = listOf("- [ ] Task", "  note one", "  note two")

        val result = MarkdownParser.editTodoNotes(lines = lines, lineIndex = 0, newNotes = emptyList())

        assertEquals(listOf("- [ ] Task"), result)
    }

    @Test
    fun editTodoNotes_preserves_interior_and_trailing_blank_lines() {
        val lines = listOf("- [ ] Task", "  old note")

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf("", "first line", "", "second line", ""),
        )

        assertEquals(
            listOf("- [ ] Task", "  first line", "", "  second line", ""),
            result,
        )
    }

    @Test
    fun editTodoNotes_is_noop_when_notes_unchanged() {
        val lines = listOf(
            "- [ ] Task",
            "  note with  extra  spaces",
            "  - [ ] Subtask",
            "  note after subtask",
        )

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf("note with  extra  spaces", "note after subtask"),
        )

        assertEquals(lines, result)
    }

    @Test
    fun deleteTodoWithSubtasks_removes_multiparagraph_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  note",
            "",
            "  detached",
            "- [ ] Next",
        )

        val result = MarkdownParser.tryDeleteTodoWithSubtasks(lines, 0)

        assertEquals(listOf("- [ ] Next"), result)
    }

    @Test
    fun tryEditTodoNotes_returns_null_for_non_todo_line() {
        val lines = listOf("# Heading", "  note")

        val result = MarkdownParser.tryEditTodoNotes(lines = lines, lineIndex = 0, newNotes = listOf("x"))

        assertEquals(null, result)
    }

    @Test
    fun deleteTodoWithSubtasks_removes_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  note",
            "  - [ ] Subtask",
            "    subtask note",
            "- [ ] Other",
        )

        val result = MarkdownParser.tryDeleteTodoWithSubtasks(lines, 0)

        assertEquals(listOf("- [ ] Other"), result)
    }

    @Test
    fun hasSubtasks_detects_subtasks_after_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  note",
            "  - [ ] Subtask",
        )

        assertEquals(true, MarkdownParser.hasSubtasks(lines, 0))
        assertEquals(false, MarkdownParser.hasSubtasks(lines, 2))
    }

    @Test
    fun moveTodoLine_moves_notes_with_task() {
        val source = listOf(
            "- [ ] Task",
            "  note",
            "- [ ] Other",
        )
        val target = listOf("# Target")

        val result = MarkdownParser.tryMoveTodoLine(
            sourceLines = source,
            lineIndex = 0,
            targetLines = target,
        )

        val (newSource, newTarget) = result ?: error("Expected move to succeed")
        assertEquals(listOf("- [ ] Other"), newSource)
        assertEquals(listOf("# Target", "- [ ] Task", "  note"), newTarget)
    }

    @Test
    fun toggleLine_preserves_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  note",
        )

        val result = MarkdownParser.toggleLine(lines = lines, lineIndex = 0, enableTasksPlugin = false)

        assertEquals(listOf("- [x] Task", "  note"), result)
    }

    @Test
    fun addSubTodo_inserts_after_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  note",
        )

        val result = MarkdownParser.addSubTodo(lines, 0, "New subtask")

        assertEquals(
            listOf("- [ ] Task", "  note", "  - [ ] New subtask"),
            result,
        )
    }

    @Test
    fun addSubTodo_inserts_after_multiparagraph_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  note one",
            "",
            "  note two",
        )

        val result = MarkdownParser.addSubTodo(lines, 0, "New subtask")

        assertEquals(
            listOf("- [ ] Task", "  note one", "", "  note two", "  - [ ] New subtask"),
            result,
        )
    }

    @Test
    fun notesToEditorText_strips_only_base_indent() {
        val notes = listOf(
            "  Outer paragraph",
            "    - nested list item",
            "  ",
            "  Last paragraph",
        )

        assertEquals(
            "Outer paragraph\n  - nested list item\n  \nLast paragraph",
            MarkdownParser.notesToEditorText(notes),
        )
    }

    @Test
    fun editorTextToNotes_keeps_relative_indent_blank_lines_and_trailing_blank() {
        val text = "Outer paragraph\n  - nested list item\n  \nLast paragraph\n"

        assertEquals(
            listOf("Outer paragraph", "  - nested list item", "  ", "Last paragraph", ""),
            MarkdownParser.editorTextToNotes(text),
        )
    }

    @Test
    fun parse_preserves_whitespace_only_lines_in_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  para one",
            "  ",
            "  para two",
        )

        val todos = MarkdownParser.parse(lines)

        assertEquals(listOf("  para one", "  ", "  para two"), todos[0].notes)
    }

    @Test
    fun editTodoNotes_preserves_relative_indentation() {
        val lines = listOf(
            "- [ ] Task",
            "  Outer paragraph",
            "  - inner list item",
            "    - nested item",
            "  ```",
            "  code here",
            "  ```",
        )

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf(
                "Changed paragraph",
                "- inner list item",
                "  - nested item",
                "```",
                "code here",
                "```",
            ),
        )

        assertEquals(
            listOf(
                "- [ ] Task",
                "  Changed paragraph",
                "  - inner list item",
                "    - nested item",
                "  ```",
                "  code here",
                "  ```",
            ),
            result,
        )
    }

    @Test
    fun editTodoNotes_is_noop_for_indented_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  Outer paragraph",
            "    - nested list item",
            "  ```",
            "  code here",
            "  ```",
        )

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf(
                "Outer paragraph",
                "  - nested list item",
                "```",
                "code here",
                "```",
            ),
        )

        assertEquals(lines, result)
    }

    @Test
    fun editTodoNotes_preserves_whitespace_only_lines() {
        val lines = listOf("- [ ] Task", "  para one", "  ", "  para two")

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf("changed", "  ", "para two"),
        )

        assertEquals(
            listOf("- [ ] Task", "  changed", "  ", "  para two"),
            result,
        )
    }

    @Test
    fun editTodoNotes_is_noop_when_whitespace_only_lines_unchanged() {
        val lines = listOf("- [ ] Task", "  para one", "  ", "  para two")

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf("para one", "  ", "para two"),
        )

        assertEquals(lines, result)
    }

    @Test
    fun deleteTodoWithSubtasks_moves_trailing_note_blank_with_task() {
        val lines = listOf(
            "- [ ] Task",
            "  note",
            "",
            "- [ ] Next",
        )

        val result = MarkdownParser.tryDeleteTodoWithSubtasks(lines, 0)

        assertEquals(listOf("- [ ] Next"), result)
    }

    @Test
    fun deleteTodoWithSubtasks_keeps_blank_separator_when_task_has_no_notes() {
        val lines = listOf(
            "- [ ] Task",
            "",
            "- [ ] Next",
        )

        val result = MarkdownParser.tryDeleteTodoWithSubtasks(lines, 0)

        assertEquals(listOf("", "- [ ] Next"), result)
    }

    @Test
    fun moveTodoLine_moves_multiparagraph_notes() {
        val source = listOf(
            "- [ ] Task",
            "  note one",
            "",
            "  note two",
            "- [ ] Other",
        )
        val target = listOf("# Target")

        val result = MarkdownParser.tryMoveTodoLine(
            sourceLines = source,
            lineIndex = 0,
            targetLines = target,
        )

        val (newSource, newTarget) = result ?: error("Expected move to succeed")
        assertEquals(listOf("- [ ] Other"), newSource)
        assertEquals(listOf("# Target", "- [ ] Task", "  note one", "", "  note two"), newTarget)
    }

    @Test
    fun hasSubtasks_detects_subtasks_across_blank_line() {
        val lines = listOf(
            "- [ ] Task",
            "  note",
            "",
            "  - [ ] Subtask",
        )

        assertEquals(true, MarkdownParser.hasSubtasks(lines, 0))
    }

    @Test
    fun parse_edit_roundtrip_preserves_notes_byte_for_byte() {
        val original = listOf(
            "- [ ] Task",
            "  Outer paragraph",
            "    - nested list",
            "  ",
            "  ```",
            "  code",
            "  ```",
            "- [ ] Other",
        )

        val todos = MarkdownParser.parse(original)
        val editorText = MarkdownParser.notesToEditorText(todos[0].notes)
        val edited = MarkdownParser.editTodoNotes(
            lines = original,
            lineIndex = 0,
            newNotes = MarkdownParser.editorTextToNotes(editorText),
        )

        assertEquals(original, edited)
    }

    @Test
    fun parse_preserves_trailing_blank_line_in_notes() {
        val lines = listOf(
            "- [ ] Task",
            "  para one",
            "",
            "- [ ] Other",
        )

        val todos = MarkdownParser.parse(lines)

        assertEquals(listOf("  para one", ""), todos[0].notes)
        assertEquals("para one\n", MarkdownParser.notesToEditorText(todos[0].notes))
    }

    @Test
    fun editTodoNotes_preserves_trailing_blank_line() {
        val lines = listOf("- [ ] Task", "  old note")

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf("para one", ""),
        )

        assertEquals(listOf("- [ ] Task", "  para one", ""), result)
    }

    @Test
    fun editTodoNotes_is_noop_with_trailing_blank_line() {
        val lines = listOf("- [ ] Task", "  para one", "", "- [ ] Other")

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf("para one", ""),
        )

        assertEquals(lines, result)
    }

    @Test
    fun editTodoNotes_drops_leading_blank_line() {
        val lines = listOf("- [ ] Task", "  old note")

        val result = MarkdownParser.editTodoNotes(
            lines = lines,
            lineIndex = 0,
            newNotes = listOf("", "para one", "", "para two"),
        )

        assertEquals(listOf("- [ ] Task", "  para one", "", "  para two"), result)
    }

    @Test
    fun trailing_blank_line_roundtrip_via_editor_helpers() {
        val lines = listOf("- [ ] Task")

        val editorText = "para one\n\npara two\n"
        val notes = MarkdownParser.editorTextToNotes(editorText)
        val saved = MarkdownParser.editTodoNotes(lines, 0, notes)
        val reopened = MarkdownParser.notesToEditorText(MarkdownParser.parse(saved)[0].notes)

        assertEquals(listOf("para one", "", "para two", ""), notes)
        assertEquals(editorText, reopened)
    }
}
