package com.isotjs.todosian.utils

import com.isotjs.todosian.data.model.TasksPriority
import com.isotjs.todosian.data.model.Todo
import java.time.LocalDate
import java.util.UUID

object MarkdownParser {
    private val todoRegex = Regex("""^([ \t]*)- \[(x| )\] (.*)$""")

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
            val indentPrefix = match.groupValues[1]
            val isDone = match.groupValues[2] == "x"
            val remainder = match.groupValues[3]

            val parsed = parseRemainder(remainder)
            Todo(
                id = UUID.randomUUID().toString(),
                text = parsed.mainText,
                isDone = isDone,
                lineIndex = index,
                indentPrefix = indentPrefix,
                indentLevel = indentLevel(indentPrefix),
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
            val withoutDoneDate = removeCompletionDate(remainder)
            if (!isDone) {
                withoutDoneDate + " ✅ $todayStr"
            } else {
                withoutDoneDate
            }
        }

        val newLine = "$indentPrefix- [$newMark] ${newRemainder.trimEnd()}"
        val updatedLines = lines.toMutableList().apply {
            this[lineIndex] = newLine
        }
        
        if (!isDone && enableTasksPlugin) {
            val parsed = parseRemainder(remainder)
            val rule = parsed.meta.recurrence
            if (!rule.isNullOrBlank()) {
                val nextLine = buildRecurredTaskLine(
                    indentPrefix = indentPrefix,
                    taskText = parsed.mainText,
                    originalMeta = parsed.meta,
                    rule = rule,
                    today = today,
                )
                if (nextLine != null) {
                    return updatedLines.toMutableList().apply {
                        add(lineIndex, nextLine)
                    }
                }
            }
        }

        return updatedLines
    }

    fun tryToggleLine(
        lines: List<String>,
        lineIndex: Int,
        enableTasksPlugin: Boolean,
        today: LocalDate = LocalDate.now(),
    ): List<String>? {
        if (lineIndex !in lines.indices) return null
        val line = lines[lineIndex]
        if (!isTodoLine(line)) return null
        return toggleLine(lines, lineIndex, enableTasksPlugin, today)
    }

    fun addTodo(
        lines: List<String>,
        text: String,
        meta: TasksMeta? = null,
        enableTasksPlugin: Boolean = false,
        addAtStart: Boolean = false,
    ): List<String> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return lines

        val newLine = buildTodoLine(
            text = cleaned,
            meta = meta,
            enableTasksPlugin = enableTasksPlugin,
            indentPrefix = "",
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
    ): List<String>? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        if (parentLineIndex !in lines.indices) return null

        val parentLine = lines[parentLineIndex]
        val match = todoRegex.matchEntire(parentLine) ?: return null
        val parentIndentPrefix = match.groupValues[1]

        val indentUnit = inferIndentUnit(parentIndentPrefix)
        val newIndentPrefix = parentIndentPrefix + indentUnit

        val newLine = buildTodoLine(
            text = cleaned,
            meta = meta,
            enableTasksPlugin = enableTasksPlugin,
            indentPrefix = newIndentPrefix,
        )

        // Insert after the parent's full block (notes + nested todos) so ownership stays intact.
        val insertIndex = todoBlockEnd(lines, parentLineIndex) ?: return null
        return lines.toMutableList().apply { add(insertIndex, newLine) }
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

    fun tryDeleteTodoWithSubtasks(lines: List<String>, lineIndex: Int): List<String>? {
        if (lineIndex !in lines.indices) return null
        if (!isTodoLine(lines[lineIndex])) return null
        val endIndex = todoBlockEnd(lines, lineIndex) ?: return null

        return lines.toMutableList().apply {
            subList(lineIndex, endIndex).clear()
        }
    }

    /**
     * Deletes [todo] by resolving its current line first.
     * Prefers [Todo.lineIndex] when that line still matches; otherwise a unique
     * text/done/indent match. Returns null when the target cannot be located safely.
     */
    fun tryDeleteTodoWithSubtasks(lines: List<String>, todo: Todo): List<String>? {
        val lineIndex = resolveTodoLineIndex(lines, todo) ?: return null
        return tryDeleteTodoWithSubtasks(lines, lineIndex)
    }

    /**
     * Locates [todo] in [lines] without trusting a possibly stale [Todo.lineIndex].
     * Returns null when missing or when multiple lines match the same identity.
     */
    fun resolveTodoLineIndex(lines: List<String>, todo: Todo): Int? {
        fun matches(line: String): Boolean {
            val match = todoRegex.matchEntire(line) ?: return false
            val indentPrefix = match.groupValues[1]
            val isDone = match.groupValues[2] == "x"
            val mainText = parseRemainder(match.groupValues[3]).mainText
            return mainText == todo.text &&
                isDone == todo.isDone &&
                indentLevel(indentPrefix) == todo.indentLevel
        }

        val atIndex = lines.getOrNull(todo.lineIndex)
        if (atIndex != null && matches(atIndex)) return todo.lineIndex

        var found: Int? = null
        for (index in lines.indices) {
            if (!matches(lines[index])) continue
            if (found != null) return null
            found = index
        }
        return found
    }

    fun hasSubtasks(lines: List<String>, lineIndex: Int): Boolean {
        if (lineIndex !in lines.indices) return false
        val line = lines[lineIndex]
        val match = todoRegex.matchEntire(line) ?: return false
        val parentIndent = indentLevel(match.groupValues[1])

        var index = lineIndex + 1
        while (index < lines.size) {
            val nextMatch = todoRegex.matchEntire(lines[index])
            if (nextMatch == null) {
                index++
                continue
            }
            val nextIndent = indentLevel(nextMatch.groupValues[1])
            return nextIndent > parentIndent
        }
        return false
    }

    /**
     * Moves the todo block at [todoLineIndex] under [newParentLineIndex] as a direct child.
     * Cuts the block (notes + nested todos), re-indents it one level under the new parent,
     * and inserts before [beforeSiblingLineIndex] when that sibling is a direct child;
     * otherwise appends after the parent's nested content.
     * Returns null when the move is invalid.
     */
    fun tryMoveTodoUnderParent(
        lines: List<String>,
        todoLineIndex: Int,
        newParentLineIndex: Int,
        beforeSiblingLineIndex: Int? = null,
    ): List<String>? {
        if (todoLineIndex !in lines.indices || newParentLineIndex !in lines.indices) return null
        if (todoLineIndex == newParentLineIndex) return null

        val todoMatch = todoRegex.matchEntire(lines[todoLineIndex]) ?: return null
        val parentMatch = todoRegex.matchEntire(lines[newParentLineIndex]) ?: return null

        val parentIndentPrefix = parentMatch.groupValues[1]
        val oldRootPrefix = todoMatch.groupValues[1]
        val todoIndent = indentLevel(oldRootPrefix)
        if (todoIndent <= 0) return null

        val indentUnit = inferIndentUnit(parentIndentPrefix)
        val newRootPrefix = parentIndentPrefix + indentUnit
        val expectedChildIndent = indentLevel(newRootPrefix)
        // Reject promote/demote across hierarchy (e.g. sub-subtask under a top-level task).
        if (todoIndent != expectedChildIndent) return null

        val blockEnd = todoBlockEnd(lines, todoLineIndex) ?: return null
        if (newParentLineIndex in todoLineIndex until blockEnd) return null

        val effectiveBeforeSibling = beforeSiblingLineIndex?.takeIf { sibling ->
            sibling in lines.indices &&
                sibling != todoLineIndex &&
                isTodoLine(lines[sibling]) &&
                sibling !in todoLineIndex until blockEnd
        }

        val originalBlock = lines.subList(todoLineIndex, blockEnd).toList()
        val rewrittenBlock = rewriteBlockIndentPrefixes(
            block = originalBlock,
            oldRootPrefix = oldRootPrefix,
            newRootPrefix = newRootPrefix,
        )

        val withoutBlock = lines.toMutableList().apply {
            subList(todoLineIndex, blockEnd).clear()
        }

        fun adjustAfterRemoval(index: Int): Int {
            return if (index > todoLineIndex) index - originalBlock.size else index
        }

        val adjustedParent = adjustAfterRemoval(newParentLineIndex)
        if (adjustedParent !in withoutBlock.indices) return null
        if (!isTodoLine(withoutBlock[adjustedParent])) return null

        val parentBlockEnd = todoBlockEnd(withoutBlock, adjustedParent) ?: return null
        val insertIndex = if (effectiveBeforeSibling != null) {
            val adjustedSibling = adjustAfterRemoval(effectiveBeforeSibling)
            val siblingMatch = withoutBlock.getOrNull(adjustedSibling)?.let { todoRegex.matchEntire(it) }
            val canInsertBefore = siblingMatch != null &&
                adjustedSibling in (adjustedParent + 1) until parentBlockEnd &&
                indentLevel(siblingMatch.groupValues[1]) == expectedChildIndent
            if (canInsertBefore) adjustedSibling else parentBlockEnd
        } else {
            parentBlockEnd
        }

        return withoutBlock.apply {
            addAll(insertIndex, rewrittenBlock)
        }
    }

    /**
     * Reorders todo blocks among [orderedLineIndices] using list-move semantics
     * (`add(toIndex, removeAt(fromIndex))`).
     *
     * Each entry is the starting line of a todo; its block includes following non-todo
     * lines and deeper nested todos until the next same-or-shallower todo.
     * Blocks not listed keep their places in the file (e.g. completed items when
     * reordering only active ones).
     */
    fun tryReorderTodoBlocks(
        lines: List<String>,
        orderedLineIndices: List<Int>,
        fromIndex: Int,
        toIndex: Int,
    ): List<String>? {
        if (fromIndex == toIndex) return lines
        if (fromIndex !in orderedLineIndices.indices || toIndex !in orderedLineIndices.indices) {
            return null
        }
        val newOrder = orderedLineIndices.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        return tryApplyTodoBlockOrder(
            lines = lines,
            orderedLineIndices = orderedLineIndices,
            newOrderedLineIndices = newOrder,
        )
    }

    /**
     * Applies a full permutation of todo blocks.
     * [orderedLineIndices] is the current file order of the reorder set;
     * [newOrderedLineIndices] is the desired order (same indices, possibly permuted).
     */
    fun tryApplyTodoBlockOrder(
        lines: List<String>,
        orderedLineIndices: List<Int>,
        newOrderedLineIndices: List<Int>,
    ): List<String>? {
        if (orderedLineIndices == newOrderedLineIndices) return lines
        if (orderedLineIndices.size != newOrderedLineIndices.size) return null
        if (orderedLineIndices.size < 2) return lines
        if (orderedLineIndices.toSet() != newOrderedLineIndices.toSet()) return null

        val startsSet = HashSet<Int>(orderedLineIndices.size)
        val blockByStart = HashMap<Int, List<String>>(orderedLineIndices.size)
        val endByStart = HashMap<Int, Int>(orderedLineIndices.size)
        var previousStart = -1
        for (start in orderedLineIndices) {
            if (start !in lines.indices) return null
            if (start <= previousStart) return null
            if (!isTodoLine(lines[start])) return null
            if (!startsSet.add(start)) return null
            val end = todoBlockEnd(lines, start) ?: return null
            endByStart[start] = end
            blockByStart[start] = lines.subList(start, end).toList()
            previousStart = start
        }

        val result = ArrayList<String>(lines.size)
        var index = 0
        var emitIndex = 0
        while (index < lines.size) {
            if (index in startsSet) {
                index = endByStart.getValue(index)
                val emitStart = newOrderedLineIndices[emitIndex++]
                result.addAll(blockByStart.getValue(emitStart))
            } else {
                result.add(lines[index])
                index++
            }
        }
        if (emitIndex != newOrderedLineIndices.size) return null
        return result
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
        val indentPrefix = match.groupValues[1]
        val mark = match.groupValues[2]
        val remainder = match.groupValues[3]
        val parsed = parseRemainder(remainder)

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
    ): List<String>? {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return null
        if (lineIndex !in lines.indices) return null

        val line = lines[lineIndex]
        if (!isTodoLine(line)) return null
        return editTodoText(lines, lineIndex, cleaned)
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
        if (!isTodoLine(sourceLines[lineIndex])) return null
        val endIndex = todoBlockEnd(sourceLines, lineIndex) ?: return null

        val block = sourceLines.subList(lineIndex, endIndex).toList()
        val newSourceLines = sourceLines.toMutableList().apply {
            subList(lineIndex, endIndex).clear()
        }
        val newTargetLines = targetLines.toMutableList().apply { addAll(block) }
        return newSourceLines to newTargetLines
    }

    /**
     * End index (exclusive) of the todo block starting at [startIndex].
     * Includes following non-todo lines and deeper nested todos until the next
     * todo at the same or shallower indent (or EOF).
     */
    private fun todoBlockEnd(lines: List<String>, startIndex: Int): Int? {
        if (startIndex !in lines.indices) return null
        val match = todoRegex.matchEntire(lines[startIndex]) ?: return null
        val baseIndent = indentLevel(match.groupValues[1])

        var index = startIndex + 1
        while (index < lines.size) {
            val nextMatch = todoRegex.matchEntire(lines[index])
            if (nextMatch != null && indentLevel(nextMatch.groupValues[1]) <= baseIndent) {
                break
            }
            index++
        }
        return index
    }

    /**
     * Rewrites indent prefixes for todo lines in [block] by replacing [oldRootPrefix]
     * with [newRootPrefix] on the root and all deeper-nested todos.
     * Non-todo lines are preserved unchanged.
     */
    private fun rewriteBlockIndentPrefixes(
        block: List<String>,
        oldRootPrefix: String,
        newRootPrefix: String,
    ): List<String> {
        if (oldRootPrefix == newRootPrefix) return block
        return block.map { line ->
            val match = todoRegex.matchEntire(line) ?: return@map line
            val prefix = match.groupValues[1]
            if (!prefix.startsWith(oldRootPrefix)) return@map line
            val rewrittenPrefix = newRootPrefix + prefix.removePrefix(oldRootPrefix)
            rewrittenPrefix + line.substring(prefix.length)
        }
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
        val indentPrefix = match.groupValues[1]
        val mark = match.groupValues[2]

        val resolvedMeta = if (enableTasksPlugin) meta else null
        val newLine = buildString {
            append(indentPrefix)
            append("- [")
            append(mark)
            append("] ")
            append(cleaned)
            val suffix = resolvedMeta?.toSuffixString().orEmpty()
            if (suffix.isNotEmpty()) append(suffix)
        }

        return lines.toMutableList().apply { this[lineIndex] = newLine }
    }

    private fun todayString(today: LocalDate = LocalDate.now()): String = today.toString()

    private fun buildRecurredTaskLine(
        indentPrefix: String,
        taskText: String,
        originalMeta: TasksMeta,
        rule: String,
        today: LocalDate,
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
            val suffix = newMeta.toSuffixString()
            if (suffix.isNotEmpty()) append(suffix)
        }
    }

    private fun buildTodoLine(
        text: String,
        meta: TasksMeta?,
        enableTasksPlugin: Boolean,
        indentPrefix: String,
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
            val suffix = resolvedMeta?.toSuffixString().orEmpty()
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

    private fun inferIndentUnit(prefix: String): String {
        return if (prefix.contains('\t')) "\t" else "  "
    }

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
