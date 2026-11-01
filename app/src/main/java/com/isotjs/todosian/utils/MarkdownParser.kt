package com.isotjs.todosian.utils

import com.isotjs.todosian.data.model.TasksPriority
import com.isotjs.todosian.data.model.Todo
import java.time.LocalDate

data class TaskMarkers(
    val created: String = "➕",
    val start: String = "🛫",
    val scheduled: String = "⏳",
    val due: String = "📅",
    val done: String = "✅",
    val recurrence: String = "🔁",
) {
    companion object {
        val Default = TaskMarkers()
    }
}

object MarkdownParser {
    private val todoRegex = Regex("""^([ \t]*)- \[(x| )\] (.*)$""")

    private val prioritySuffixRegex = Regex("""\s(🔺|⏫|🔼|🔽|⏬️?|⏬)\s*$""")

    private fun dateSuffixRegex(marker: String) =
        Regex("""\s${Regex.escape(marker)}\s*(\d{4}-\d{2}-\d{2})\s*$""")

    private fun recurrenceSuffixRegex(marker: String) =
        Regex("""\s${Regex.escape(marker)}\s*(.+?)\s*$""")

    private fun completionAnywhereRegex(marker: String) =
        Regex("""\s${Regex.escape(marker)}\s*\d{4}-\d{2}-\d{2}(?=\s|$)""")

    private fun isTodoLine(line: String): Boolean = todoRegex.matches(line)

    fun parse(lines: List<String>, markers: TaskMarkers = TaskMarkers.Default): List<Todo> {
        val idOccurrences = HashMap<String, Int>()

        return lines.mapIndexedNotNull { index, line ->
            val match = todoRegex.matchEntire(line) ?: return@mapIndexedNotNull null
            val indentPrefix = match.groupValues[1]
            val isDone = match.groupValues[2] == "x"
            val remainder = match.groupValues[3]

            val parsed = parseRemainder(remainder, markers)
            val todoIndentLevel = indentLevel(indentPrefix)
            val baseId = stableTodoId(
                indentLevel = todoIndentLevel,
                text = parsed.mainText,
                meta = parsed.meta,
            )
            val occurrence = idOccurrences.getOrDefault(baseId, 0)
            idOccurrences[baseId] = occurrence + 1

            Todo(
                id = if (occurrence == 0) baseId else "$baseId\u001F$occurrence",
                text = parsed.mainText,
                isDone = isDone,
                lineIndex = index,
                indentLevel = todoIndentLevel,
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
        today: LocalDate = LocalDate.now(),
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String> {
        if (lineIndex !in lines.indices) return lines

        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return lines
        val indentPrefix = match.groupValues[1]
        val isDone = match.groupValues[2] == "x"
        val remainder = match.groupValues[3]
        val newMark = if (isDone) " " else "x"

        val todayStr = today.toString()

        val newRemainder = if (!enableTasksPlugin) {
            remainder
        } else {
            val withoutDoneDate = removeCompletionDate(remainder, markers)
            if (!isDone) {
                withoutDoneDate + " ${markers.done} $todayStr"
            } else {
                withoutDoneDate
            }
        }

        val newLine = "$indentPrefix- [$newMark] ${newRemainder.trimEnd()}"
        val updatedLines = lines.toMutableList().apply {
            this[lineIndex] = newLine
        }

        if (!isDone) {
            insertRecurredOccurrenceIfAny(updatedLines, match, lineIndex, enableTasksPlugin, today, markers)?.let {
                return it
            }
        }

        return updatedLines
    }

    fun tryToggleLine(
        lines: List<String>,
        lineIndex: Int,
        enableTasksPlugin: Boolean,
        today: LocalDate = LocalDate.now(),
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String>? {
        if (lineIndex !in lines.indices) return null
        val line = lines[lineIndex]
        if (!isTodoLine(line)) return null
        return toggleLine(lines, lineIndex, enableTasksPlugin, today, markers)
    }

    /**
     * Toggles a todo line and, when the todo is being marked as completed, also marks all of its
     * sub-todos as completed. The todo's block includes indented non-todo lines (for example
     * notes) and interior blank lines, so those do not stop the cascade. The block ends at the
     * first non-blank line with the same or a lower indentation. Sub-todos that are already
     * completed are left untouched, including their metadata. Re-opening a todo never touches
     * its sub-todos.
     */
    fun toggleLineWithSubtasks(
        lines: List<String>,
        lineIndex: Int,
        enableTasksPlugin: Boolean,
        today: LocalDate = LocalDate.now(),
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String> {
        if (lineIndex !in lines.indices) return lines

        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return lines
        val parentIndent = indentLevel(match.groupValues[1])
        val isDone = match.groupValues[2] == "x"

        val endIndex = blockContentEndIndex(lines, lineIndex, parentIndent)

        val updatedLines = lines.toMutableList()
        updatedLines[lineIndex] = toggledLine(updatedLines[lineIndex], enableTasksPlugin, today, markers)

        if (!isDone) {
            for (i in lineIndex + 1 until endIndex) {
                if (isTodoLine(updatedLines[i])) {
                    updatedLines[i] = setLineDone(updatedLines[i], enableTasksPlugin, today, markers)
                }
            }
        }

        if (!isDone) {
            insertRecurredOccurrenceIfAny(updatedLines, match, lineIndex, enableTasksPlugin, today, markers)?.let {
                return it
            }
        }

        return updatedLines
    }

    fun tryToggleTodoWithSubtasks(
        lines: List<String>,
        lineIndex: Int,
        enableTasksPlugin: Boolean,
        today: LocalDate = LocalDate.now(),
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String>? {
        if (lineIndex !in lines.indices) return null
        val line = lines[lineIndex]
        if (!isTodoLine(line)) return null
        return toggleLineWithSubtasks(lines, lineIndex, enableTasksPlugin, today, markers)
    }

    /**
     * Computes the line indices of all todos that are sub-todos of a collapsed todo. A todo is
     * considered collapsed when its stable [Todo.id] is contained in [collapsedTodoIds]. Stable
     * ids survive external file edits that shift line indices. Descendants of hidden sub-todos
     * are counted as hidden as well.
     */
    fun hiddenSubtaskLineIndices(
        todos: List<Todo>,
        collapsedTodoIds: Set<String>,
    ): Set<Int> {
        if (collapsedTodoIds.isEmpty() || todos.isEmpty()) return emptySet()

        val ordered = todos.sortedBy { it.lineIndex }
        val hiddenLineIndices = HashSet<Int>()

        for (i in 1 until ordered.size) {
            val todo = ordered[i]

            var j = i - 1
            var ancestor: Todo? = null
            while (j >= 0) {
                val candidate = ordered[j]
                if (candidate.indentLevel < todo.indentLevel) {
                    ancestor = candidate
                    break
                }
                j--
            }

            val isHidden = ancestor != null && (
                ancestor.id in collapsedTodoIds ||
                    ancestor.lineIndex in hiddenLineIndices
                )
            if (isHidden) {
                hiddenLineIndices.add(todo.lineIndex)
            }
        }

        return hiddenLineIndices
    }

    fun addTodo(
        lines: List<String>,
        text: String,
        meta: TasksMeta? = null,
        enableTasksPlugin: Boolean = false,
        addAtStart: Boolean = false,
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return lines

        val newLine = buildTodoLine(
            text = cleaned,
            meta = meta,
            enableTasksPlugin = enableTasksPlugin,
            indentPrefix = "",
            markers = markers,
        )

        if (lines.isEmpty()) return listOf(newLine)
        return lines.toMutableList().apply {
            if (addAtStart) add(frontmatterEndIndex(this), newLine) else add(newLine)
        }
    }

    private fun frontmatterEndIndex(lines: List<String>): Int {
        if (lines.firstOrNull() != "---") return 0
        for (i in 1 until lines.size) {
            if (lines[i] == "---") return i + 1
        }
        return 0
    }

    fun addSubTodo(
        lines: List<String>,
        parentLineIndex: Int,
        text: String,
        meta: TasksMeta? = null,
        enableTasksPlugin: Boolean = false,
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String>? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        if (parentLineIndex !in lines.indices) return null

        val parentLine = lines[parentLineIndex]
        val match = todoRegex.matchEntire(parentLine) ?: return null
        val parentIndentPrefix = match.groupValues[1]
        val parentIndentLevel = indentLevel(parentIndentPrefix)

        val indentUnit = inferIndentUnit(parentIndentPrefix)
        val newIndentPrefix = parentIndentPrefix + indentUnit

        val newLine = buildTodoLine(
            text = cleaned,
            meta = meta,
            enableTasksPlugin = enableTasksPlugin,
            indentPrefix = newIndentPrefix,
            markers = markers,
        )

        val insertIndex = blockContentEndIndex(lines, parentLineIndex, parentIndentLevel)

        return lines.toMutableList().apply { add(insertIndex, newLine) }
    }

    fun tryDeleteTodoWithSubtasks(lines: List<String>, lineIndex: Int): List<String>? {
        if (lineIndex !in lines.indices) return null
        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return null
        val parentIndent = indentLevel(match.groupValues[1])

        val endIndex = blockContentEndIndex(lines, lineIndex, parentIndent)

        return lines.toMutableList().apply {
            subList(lineIndex, endIndex).clear()
        }
    }

    fun hasSubtasks(lines: List<String>, lineIndex: Int): Boolean {
        if (lineIndex !in lines.indices) return false
        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return false
        val parentIndent = indentLevel(match.groupValues[1])

        val endIndex = blockContentEndIndex(lines, lineIndex, parentIndent)
        for (i in lineIndex + 1 until endIndex) {
            val nextMatch = todoRegex.matchEntire(lines[i]) ?: continue
            if (indentLevel(nextMatch.groupValues[1]) > parentIndent) return true
        }
        return false
    }

    fun editTodoText(
        lines: List<String>,
        lineIndex: Int,
        newText: String,
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String> {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return lines
        if (lineIndex !in lines.indices) return lines

        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return lines
        val indentPrefix = match.groupValues[1]
        val mark = match.groupValues[2]
        val remainder = match.groupValues[3]
        val parsed = parseRemainder(remainder, markers)

        val newRemainder = buildString {
            append(cleaned)
            if (parsed.suffixRaw.isNotEmpty()) append(parsed.suffixRaw)
        }

        val newLine = "$indentPrefix- [$mark] ${newRemainder.trimEnd()}"
        return lines.toMutableList().apply { this[lineIndex] = newLine }
    }

    fun tryEditTodoText(
        lines: List<String>,
        lineIndex: Int,
        newText: String,
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String>? {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return null
        if (lineIndex !in lines.indices) return null

        val line = lines[lineIndex]
        if (!isTodoLine(line)) return null
        return editTodoText(lines, lineIndex, cleaned, markers)
    }

    fun tryCopyTodoLine(
        sourceLines: List<String>,
        lineIndex: Int,
        targetLines: List<String>,
    ): Pair<List<String>, List<String>>? {
        if (lineIndex !in sourceLines.indices) return null
        val line = sourceLines[lineIndex]
        if (!isTodoLine(line)) return null

        val newTargetLines = targetLines.toMutableList().apply { add(line) }
        return sourceLines to newTargetLines
    }

    fun tryMoveTodoLine(
        sourceLines: List<String>,
        lineIndex: Int,
        targetLines: List<String>,
    ): Pair<List<String>, List<String>>? {
        if (lineIndex !in sourceLines.indices) return null
        val line = sourceLines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return null
        val parentIndent = indentLevel(match.groupValues[1])

        val endIndex = blockContentEndIndex(sourceLines, lineIndex, parentIndent)

        val block = sourceLines.subList(lineIndex, endIndex)
        val newSourceLines = sourceLines.toMutableList().apply {
            subList(lineIndex, endIndex).clear()
        }
        val newTargetLines = targetLines.toMutableList().apply { addAll(block) }
        return newSourceLines to newTargetLines
    }

    fun editTodo(
        lines: List<String>,
        lineIndex: Int,
        newText: String,
        meta: TasksMeta?,
        enableTasksPlugin: Boolean,
        markers: TaskMarkers = TaskMarkers.Default,
    ): List<String> {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return lines
        if (lineIndex !in lines.indices) return lines

        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return lines
        val indentPrefix = match.groupValues[1]
        val mark = match.groupValues[2]

        val resolvedMeta = if (enableTasksPlugin) meta else null
        val newLine = buildString {
            append(indentPrefix)
            append("- [")
            append(mark)
            append("] ")
            append(cleaned)
            val suffix = resolvedMeta?.toSuffixString(markers).orEmpty()
            if (suffix.isNotEmpty()) append(suffix)
        }

        return lines.toMutableList().apply { this[lineIndex] = newLine }
    }

    private fun todayString(today: LocalDate = LocalDate.now()): String = today.toString()

    private fun toggledLine(
        line: String,
        enableTasksPlugin: Boolean,
        today: LocalDate,
        markers: TaskMarkers,
    ): String {
        val match = todoRegex.matchEntire(line) ?: return line
        val indentPrefix = match.groupValues[1]
        val isDone = match.groupValues[2] == "x"
        val remainder = match.groupValues[3]
        val newMark = if (isDone) " " else "x"

        val newRemainder = if (!enableTasksPlugin) {
            remainder
        } else {
            val withoutDoneDate = removeCompletionDate(remainder, markers)
            if (!isDone) {
                withoutDoneDate + " ${markers.done} ${today.toString()}"
            } else {
                withoutDoneDate
            }
        }

        return "$indentPrefix- [$newMark] ${newRemainder.trimEnd()}"
    }

    private fun setLineDone(
        line: String,
        enableTasksPlugin: Boolean,
        today: LocalDate,
        markers: TaskMarkers,
    ): String {
        val match = todoRegex.matchEntire(line) ?: return line
        if (match.groupValues[2] == "x") return line

        val indentPrefix = match.groupValues[1]
        val remainder = match.groupValues[3]
        val newRemainder = if (!enableTasksPlugin) {
            remainder
        } else {
            removeCompletionDate(remainder, markers) + " ${markers.done} ${today.toString()}"
        }

        return "$indentPrefix- [x] ${newRemainder.trimEnd()}"
    }

    /**
     * When the tasks plugin is enabled and the toggled todo has a recurrence rule, inserts a new
     * occurrence of that todo at [lineIndex] (above the already-toggled todo and its sub-todos).
     * Returns the updated lines, or null when no occurrence should be inserted.
     */
    private fun insertRecurredOccurrenceIfAny(
        updatedLines: MutableList<String>,
        match: MatchResult,
        lineIndex: Int,
        enableTasksPlugin: Boolean,
        today: LocalDate,
        markers: TaskMarkers,
    ): List<String>? {
        if (!enableTasksPlugin) return null

        val parsed = parseRemainder(match.groupValues[3], markers)
        val rule = parsed.meta.recurrence
        if (rule.isNullOrBlank()) return null

        val nextLine = buildRecurredTaskLine(
            indentPrefix = match.groupValues[1],
            taskText = parsed.mainText,
            originalMeta = parsed.meta,
            rule = rule,
            today = today,
            markers = markers,
        ) ?: return null

        return updatedLines.toMutableList().apply {
            add(lineIndex, nextLine)
        }
    }

    private fun buildRecurredTaskLine(
        indentPrefix: String,
        taskText: String,
        originalMeta: TasksMeta,
        rule: String,
        today: LocalDate,
        markers: TaskMarkers,
    ): String? {
        val next = RecurrenceEngine.nextOccurrence(
            ruleText = rule,
            dueDate = originalMeta.dueDate,
            scheduledDate = originalMeta.scheduledDate,
            startDate = originalMeta.startDate,
            today = today,
        ) ?: return null

        val newMeta = TasksMeta(
            dueDate = next.dueDate?.toString(),
            scheduledDate = next.scheduledDate?.toString(),
            startDate = next.startDate?.toString(),
            completionDate = null,
            createdDate = if (originalMeta.createdDate != null) today.toString() else null,
            priority = originalMeta.priority,
            recurrence = originalMeta.recurrence,
        )

        return buildString {
            append(indentPrefix)
            append("- [ ] ")
            append(taskText)
            val suffix = newMeta.toSuffixString(markers)
            if (suffix.isNotEmpty()) append(suffix)
        }
    }

    private fun buildTodoLine(
        text: String,
        meta: TasksMeta?,
        enableTasksPlugin: Boolean,
        indentPrefix: String,
        markers: TaskMarkers,
    ): String {
        val resolvedMeta = if (enableTasksPlugin) {
            (meta ?: TasksMeta()).let { current ->
                if (current.createdDate == null) current.copy(createdDate = todayString()) else current
            }
        } else {
            null
        }

        return buildString {
            append(indentPrefix)
            append("- [ ] ")
            append(text)
            val suffix = resolvedMeta?.toSuffixString(markers).orEmpty()
            if (suffix.isNotEmpty()) append(suffix)
        }
    }

    private fun indentLevel(prefix: String): Int {
        if (prefix.isEmpty()) return 0
        var width = 0
        for (char in prefix) {
            width += if (char == '\t') 4 else 1
        }
        return (width / 2).coerceAtLeast(0)
    }

    private fun leadingWhitespace(line: String): String {
        var end = 0
        while (end < line.length && (line[end] == ' ' || line[end] == '\t')) end++
        return line.substring(0, end)
    }

    private fun lineIndent(line: String): Int = indentLevel(leadingWhitespace(line))

    /**
     * Returns the index of the first line after [startIndex] that is not part of the todo's
     * block (exclusive end). The block contains all non-blank lines deeper than [parentIndent],
     * notes and other non-todo content included, while interior blank lines are skipped.
     * A non-blank line at the same or a lower indentation ends the block.
     */
    private fun blockContentEndIndex(lines: List<String>, startIndex: Int, parentIndent: Int): Int {
        var index = startIndex + 1
        var lastContent = startIndex
        while (index < lines.size) {
            val nextLine = lines[index]
            if (nextLine.isBlank()) {
                index++
                continue
            }
            if (lineIndent(nextLine) <= parentIndent) break
            lastContent = index
            index++
        }
        return lastContent + 1
    }

    /**
     * Builds an id that is stable across reloads and independent of the todo's line number.
     * The done state, completion date, and notes are deliberately excluded so that completing
     * a todo does not change its identity. [parse] appends an occurrence suffix for duplicates.
     */
    private fun stableTodoId(
        indentLevel: Int,
        text: String,
        meta: TasksMeta,
    ): String = listOf(
        indentLevel.toString(),
        text,
        meta.dueDate,
        meta.startDate,
        meta.scheduledDate,
        meta.createdDate,
        meta.priority?.name,
        meta.recurrence,
    ).joinToString(separator = "\u001F") { it ?: "" }

    private fun inferIndentUnit(prefix: String): String {
        return if (prefix.contains('\t')) "\t" else "  "
    }

    private fun removeCompletionDate(remainder: String, markers: TaskMarkers): String {
        val removed = remainder.replace(completionAnywhereRegex(markers.done), "")
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
        fun toSuffixString(markers: TaskMarkers = TaskMarkers.Default): String {
            val parts = ArrayList<String>(8)
            if (createdDate != null) parts.add("${markers.created} $createdDate")
            if (startDate != null) parts.add("${markers.start} $startDate")
            if (scheduledDate != null) parts.add("${markers.scheduled} $scheduledDate")
            if (dueDate != null) parts.add("${markers.due} $dueDate")
            if (completionDate != null) parts.add("${markers.done} $completionDate")
            if (!recurrence.isNullOrBlank()) parts.add("${markers.recurrence} ${recurrence.trim()}")
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

    private fun parseRemainder(remainder: String, markers: TaskMarkers): ParsedRemainder {
        var working = remainder.trimEnd()

        val completionRegex = dateSuffixRegex(markers.done)
        val dueRegex = dateSuffixRegex(markers.due)
        val startRegex = dateSuffixRegex(markers.start)
        val scheduledRegex = dateSuffixRegex(markers.scheduled)
        val createdRegex = dateSuffixRegex(markers.created)
        val recurrenceRegex = recurrenceSuffixRegex(markers.recurrence)

        val suffixParts = mutableListOf<String>()

        var dueDate: String? = null
        var startDate: String? = null
        var scheduledDate: String? = null
        var completionDate: String? = null
        var createdDate: String? = null
        var priority: TasksPriority? = null
        var recurrence: String? = null

        while (true) {
            val completion = completionRegex.find(working)
            if (completion != null) {
                completionDate = completion.groupValues[1]
                suffixParts.add(0, working.substring(completion.range.first))
                working = working.substring(0, completion.range.first).trimEnd()
                continue
            }

            val due = dueRegex.find(working)
            if (due != null) {
                dueDate = due.groupValues[1]
                suffixParts.add(0, working.substring(due.range.first))
                working = working.substring(0, due.range.first).trimEnd()
                continue
            }

            val start = startRegex.find(working)
            if (start != null) {
                startDate = start.groupValues[1]
                suffixParts.add(0, working.substring(start.range.first))
                working = working.substring(0, start.range.first).trimEnd()
                continue
            }

            val scheduled = scheduledRegex.find(working)
            if (scheduled != null) {
                scheduledDate = scheduled.groupValues[1]
                suffixParts.add(0, working.substring(scheduled.range.first))
                working = working.substring(0, scheduled.range.first).trimEnd()
                continue
            }

            val created = createdRegex.find(working)
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

            val recur = recurrenceRegex.find(working)
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
