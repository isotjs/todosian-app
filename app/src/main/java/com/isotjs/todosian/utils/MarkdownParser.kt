package com.isotjs.todosian.utils

import com.isotjs.todosian.data.model.TasksPriority
import com.isotjs.todosian.data.model.Todo
import java.time.LocalDate
import java.util.UUID

object MarkdownParser {
    private val todoRegex = Regex("""^- \[(x| )\] (.*)$""")

    private val dueSuffixRegex = Regex("""\s📅\s(\d{4}-\d{2}-\d{2})\s*$""")
    private val startSuffixRegex = Regex("""\s🛫\s(\d{4}-\d{2}-\d{2})\s*$""")
    private val scheduledSuffixRegex = Regex("""\s⏳\s(\d{4}-\d{2}-\d{2})\s*$""")
    private val completionSuffixRegex = Regex("""\s✅\s(\d{4}-\d{2}-\d{2})\s*$""")
    private val createdSuffixRegex = Regex("""\s➕\s(\d{4}-\d{2}-\d{2})\s*$""")

    private val prioritySuffixRegex = Regex("""\s(🔺|⏫|🔼|🔽|⏬️?|⏬)\s*$""")
    private val recurrenceSuffixRegex = Regex("""\s🔁\s(.+?)\s*$""")

    private val completionAnywhereRegex = Regex("""\s✅\s\d{4}-\d{2}-\d{2}(?=\s|$)""")

    fun isTodoLine(line: String): Boolean = todoRegex.matches(line)

    fun parse(lines: List<String>): List<Todo> {
        return lines.mapIndexedNotNull { index, line ->
            val match = todoRegex.matchEntire(line) ?: return@mapIndexedNotNull null
            val isDone = match.groupValues[1] == "x"
            val remainder = match.groupValues[2]

            val parsed = parseRemainder(remainder)
            Todo(
                id = UUID.randomUUID().toString(),
                text = parsed.mainText,
                isDone = isDone,
                lineIndex = index,
                dueDate = parsed.meta.dueDate,
                startDate = parsed.meta.startDate,
                scheduledDate = parsed.meta.scheduledDate,
                completionDate = parsed.meta.completionDate,
                createdDate = parsed.meta.createdDate,
                priority = parsed.meta.priority,
                recurrence = parsed.meta.recurrence,
            )
        }
    }

    fun toggleLine(
        lines: List<String>,
        lineIndex: Int,
        enableTasksPlugin: Boolean,
    ): List<String> {
        if (lineIndex !in lines.indices) return lines

        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return lines

        val isDone = match.groupValues[1] == "x"
        val remainder = match.groupValues[2]
        val newMark = if (isDone) " " else "x"

        val newRemainder = if (!enableTasksPlugin) {
            remainder
        } else {
            val withoutDoneDate = removeCompletionDate(remainder)
            if (!isDone) {
                withoutDoneDate + " ✅ ${todayString()}"
            } else {
                withoutDoneDate
            }
        }

        val newLine = "- [$newMark] ${newRemainder.trimEnd()}"
        return lines.toMutableList().apply {
            this[lineIndex] = newLine
        }
    }

    fun tryToggleLine(
        lines: List<String>,
        lineIndex: Int,
        enableTasksPlugin: Boolean,
    ): List<String>? {
        if (lineIndex !in lines.indices) return null
        val line = lines[lineIndex]
        if (!isTodoLine(line)) return null
        return toggleLine(lines, lineIndex, enableTasksPlugin)
    }

    fun addTodo(
        lines: List<String>,
        text: String,
        meta: TasksMeta? = null,
        enableTasksPlugin: Boolean = false,
    ): List<String> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return lines

        val resolvedMeta = if (enableTasksPlugin) {
            (meta ?: TasksMeta()).let { current ->
                if (current.createdDate == null) current.copy(createdDate = todayString()) else current
            }
        } else {
            null
        }

        val newLine = buildString {
            append("- [ ] ")
            append(cleaned)
            val suffix = resolvedMeta?.toSuffixString().orEmpty()
            if (suffix.isNotEmpty()) append(suffix)
        }

        if (lines.isEmpty()) return listOf(newLine)
        return lines.toMutableList().apply { add(newLine) }
    }

    fun deleteTodo(lines: List<String>, lineIndex: Int): List<String> {
        if (lineIndex !in lines.indices) return lines
        return lines.toMutableList().apply { removeAt(lineIndex) }
    }

    fun tryDeleteTodo(lines: List<String>, lineIndex: Int): List<String>? {
        if (lineIndex !in lines.indices) return null
        val line = lines[lineIndex]
        if (!isTodoLine(line)) return null
        return deleteTodo(lines, lineIndex)
    }

    fun editTodoText(
        lines: List<String>,
        lineIndex: Int,
        newText: String,
    ): List<String> {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return lines
        if (lineIndex !in lines.indices) return lines

        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return lines
        val mark = match.groupValues[1]
        val remainder = match.groupValues[2]
        val parsed = parseRemainder(remainder)

        val newRemainder = buildString {
            append(cleaned)
            if (parsed.suffixRaw.isNotEmpty()) append(parsed.suffixRaw)
        }

        val newLine = "- [$mark] ${newRemainder.trimEnd()}"
        return lines.toMutableList().apply { this[lineIndex] = newLine }
    }

    fun tryEditTodoText(
        lines: List<String>,
        lineIndex: Int,
        newText: String,
    ): List<String>? {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return null
        if (lineIndex !in lines.indices) return null

        val line = lines[lineIndex]
        if (!isTodoLine(line)) return null
        return editTodoText(lines, lineIndex, cleaned)
    }

    fun editTodo(
        lines: List<String>,
        lineIndex: Int,
        newText: String,
        meta: TasksMeta?,
        enableTasksPlugin: Boolean,
    ): List<String> {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return lines
        if (lineIndex !in lines.indices) return lines

        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return lines
        val mark = match.groupValues[1]

        val resolvedMeta = if (enableTasksPlugin) meta else null
        val newLine = buildString {
            append("- [")
            append(mark)
            append("] ")
            append(cleaned)
            val suffix = resolvedMeta?.toSuffixString().orEmpty()
            if (suffix.isNotEmpty()) append(suffix)
        }

        return lines.toMutableList().apply { this[lineIndex] = newLine }
    }

    private fun todayString(): String = LocalDate.now().toString()

    private fun removeCompletionDate(remainder: String): String {
        val removed = remainder.replace(completionAnywhereRegex, "")
        return removed.replace(Regex("""\s{2,}"""), " ").trimEnd()
    }

    private data class ParsedRemainder(
        val mainText: String,
        val suffixRaw: String,
        val meta: TasksMeta,
    )

    data class TasksMeta(
        val dueDate: String? = null,
        val startDate: String? = null,
        val scheduledDate: String? = null,
        val completionDate: String? = null,
        val createdDate: String? = null,
        val priority: TasksPriority? = null,
        val recurrence: String? = null,
    ) {
        fun toSuffixString(): String {
            val parts = ArrayList<String>(8)
            if (createdDate != null) parts.add("➕ $createdDate")
            if (startDate != null) parts.add("🛫 $startDate")
            if (scheduledDate != null) parts.add("⏳ $scheduledDate")
            if (dueDate != null) parts.add("📅 $dueDate")
            if (completionDate != null) parts.add("✅ $completionDate")
            if (!recurrence.isNullOrBlank()) parts.add("🔁 ${recurrence.trim()}")
            val prio = priority
            if (prio != null && prio != TasksPriority.NONE) {
                parts.add(priorityToEmoji(prio))
            }

            return if (parts.isEmpty()) {
                ""
            } else {
                " " + parts.joinToString(separator = " ")
            }
        }
    }

    private fun parseRemainder(remainder: String): ParsedRemainder {
        var working = remainder.trimEnd()

        val suffixParts = mutableListOf<String>()

        var dueDate: String? = null
        var startDate: String? = null
        var scheduledDate: String? = null
        var completionDate: String? = null
        var createdDate: String? = null
        var priority: TasksPriority? = null
        var recurrence: String? = null

        while (true) {
            val completion = completionSuffixRegex.find(working)
            if (completion != null) {
                completionDate = completion.groupValues[1]
                suffixParts.add(0, working.substring(completion.range.first))
                working = working.substring(0, completion.range.first).trimEnd()
                continue
            }

            val due = dueSuffixRegex.find(working)
            if (due != null) {
                dueDate = due.groupValues[1]
                suffixParts.add(0, working.substring(due.range.first))
                working = working.substring(0, due.range.first).trimEnd()
                continue
            }

            val start = startSuffixRegex.find(working)
            if (start != null) {
                startDate = start.groupValues[1]
                suffixParts.add(0, working.substring(start.range.first))
                working = working.substring(0, start.range.first).trimEnd()
                continue
            }

            val scheduled = scheduledSuffixRegex.find(working)
            if (scheduled != null) {
                scheduledDate = scheduled.groupValues[1]
                suffixParts.add(0, working.substring(scheduled.range.first))
                working = working.substring(0, scheduled.range.first).trimEnd()
                continue
            }

            val created = createdSuffixRegex.find(working)
            if (created != null) {
                createdDate = created.groupValues[1]
                suffixParts.add(0, working.substring(created.range.first))
                working = working.substring(0, created.range.first).trimEnd()
                continue
            }

            val prio = prioritySuffixRegex.find(working)
            if (prio != null) {
                priority = emojiToPriority(prio.groupValues[1])
                suffixParts.add(0, working.substring(prio.range.first))
                working = working.substring(0, prio.range.first).trimEnd()
                continue
            }

            val recur = recurrenceSuffixRegex.find(working)
            if (recur != null) {
                recurrence = recur.groupValues[1].trim()
                suffixParts.add(0, working.substring(recur.range.first))
                working = working.substring(0, recur.range.first).trimEnd()
                continue
            }

            break
        }

        return ParsedRemainder(
            mainText = working.trimEnd(),
            suffixRaw = suffixParts.joinToString(separator = ""),
            meta = TasksMeta(
                dueDate = dueDate,
                startDate = startDate,
                scheduledDate = scheduledDate,
                completionDate = completionDate,
                createdDate = createdDate,
                priority = priority,
                recurrence = recurrence,
            ),
        )
    }

    private fun priorityToEmoji(priority: TasksPriority): String {
        return when (priority) {
            TasksPriority.HIGHEST -> "🔺"
            TasksPriority.HIGH -> "⏫"
            TasksPriority.MEDIUM -> "🔼"
            TasksPriority.LOW -> "🔽"
            TasksPriority.LOWEST -> "⏬"
            TasksPriority.NONE -> ""
        }
    }

    private fun emojiToPriority(emoji: String): TasksPriority? {
        return when (emoji) {
            "🔺" -> TasksPriority.HIGHEST
            "⏫" -> TasksPriority.HIGH
            "🔼" -> TasksPriority.MEDIUM
            "🔽" -> TasksPriority.LOW
            "⏬", "⏬️" -> TasksPriority.LOWEST
            else -> null
        }
    }
}
