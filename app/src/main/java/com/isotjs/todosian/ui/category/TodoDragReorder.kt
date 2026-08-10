package com.isotjs.todosian.ui.category

import com.isotjs.todosian.data.model.Todo

internal data class ReorderTodoItem(
    val todo: Todo,
    /**
     * Stable across line-index shifts for unique todo lines (raw markdown content).
     * Duplicate lines get an occurrence suffix so LazyColumn / reorder keys stay unique.
     */
    val stableKey: String,
)

internal enum class DragSection {
    All,
    Active,
    Completed,
}

/** Visible LazyColumn bounds for one section item, in viewport coordinates. */
internal data class VisibleItemBounds(
    val fullIndex: Int,
    val top: Float,
    val bottom: Float,
)

internal sealed interface TodoDragCommit {
    val insertBefore: Int

    data class ReorderTopLevel(
        val orderedLineIndices: List<Int>,
        val newOrderedLineIndices: List<Int>,
        override val insertBefore: Int,
    ) : TodoDragCommit

    data class MoveUnderParent(
        val todoLineIndex: Int,
        val newParentLineIndex: Int,
        val beforeSiblingLineIndex: Int?,
        override val insertBefore: Int,
    ) : TodoDragCommit
}

internal fun toReorderItems(todos: List<Todo>, lines: List<String>): List<ReorderTodoItem> {
    if (todos.isEmpty()) return emptyList()
    val ordered = todos.sortedBy { it.lineIndex }
    val keyOccurrence = HashMap<String, Int>()
    return ordered.map { todo ->
        val baseKey = lines.getOrNull(todo.lineIndex)
            ?: "${todo.text}:${todo.isDone}:${todo.indentLevel}"
        val occurrence = keyOccurrence[baseKey] ?: 0
        keyOccurrence[baseKey] = occurrence + 1
        ReorderTodoItem(todo = todo, stableKey = "$baseKey::$occurrence")
    }
}

internal fun todoBlockItemCount(items: List<ReorderTodoItem>, startIndex: Int): Int {
    if (startIndex !in items.indices) return 0
    val baseIndent = items[startIndex].todo.indentLevel
    var end = startIndex + 1
    while (end < items.size && items[end].todo.indentLevel > baseIndent) {
        end++
    }
    return end - startIndex
}

internal fun removeTodoBlock(
    items: List<ReorderTodoItem>,
    draggedKey: String,
): Pair<List<ReorderTodoItem>, ReorderTodoItem?> {
    val start = items.indexOfFirst { it.stableKey == draggedKey }
    if (start < 0) return items to null
    val count = todoBlockItemCount(items, start)
    val dragged = items[start]
    val without = items.filterIndexed { index, _ -> index < start || index >= start + count }
    return without to dragged
}

internal fun insertTodoBlock(
    items: List<ReorderTodoItem>,
    draggedKey: String,
    insertBefore: Int,
): List<ReorderTodoItem>? {
    val start = items.indexOfFirst { it.stableKey == draggedKey }
    if (start < 0) return null
    val count = todoBlockItemCount(items, start)
    if (count <= 0) return null
    val block = items.subList(start, start + count).toList()
    val without = items.filterIndexed { index, _ -> index < start || index >= start + count }
    val index = insertBefore.coerceIn(0, without.size)
    return without.toMutableList().apply { addAll(index, block) }
}

/**
 * Maps an insert index in the full (still-laid-out) list to an insert index in
 * the list without the dragged block. Positions inside the block's layout hole
 * map to the original hole ([blockStart]).
 */
internal fun mapFullInsertToWithout(
    fullInsertBefore: Int,
    blockStart: Int,
    blockCount: Int,
): Int {
    return when {
        fullInsertBefore <= blockStart -> fullInsertBefore
        fullInsertBefore >= blockStart + blockCount -> fullInsertBefore - blockCount
        else -> blockStart
    }
}

/** Full-list insert index from finger Y via item midpoints; null outside the content band. */
internal fun resolveFullInsertBefore(
    visibleBounds: List<VisibleItemBounds>,
    fingerY: Float,
    contentSlopPx: Float,
): Int? {
    if (visibleBounds.isEmpty()) return null
    val first = visibleBounds.first()
    val last = visibleBounds.last()
    if (fingerY < first.top - contentSlopPx || fingerY > last.bottom + contentSlopPx) {
        return null
    }
    for (bounds in visibleBounds) {
        val mid = (bounds.top + bounds.bottom) / 2f
        if (fingerY < mid) return bounds.fullIndex
    }
    return last.fullIndex + 1
}

internal fun isValidInsertBefore(
    itemsWithout: List<ReorderTodoItem>,
    insertBefore: Int,
    draggedLevel: Int,
): Boolean {
    if (insertBefore !in 0..itemsWithout.size) return false
    if (draggedLevel <= 0) {
        return insertBefore == itemsWithout.size ||
            itemsWithout[insertBefore].todo.indentLevel == 0
    }
    return findOwningParentBefore(itemsWithout, insertBefore, draggedLevel - 1) != null
}

/**
 * Nearest valid drop gap under the finger using the full list layout (including the
 * dragged block's layout hole). Invalid gaps yield null — no snap to a distant slot.
 */
internal fun resolveValidDropInsertBefore(
    items: List<ReorderTodoItem>,
    draggedKey: String,
    draggedLevel: Int,
    visibleBounds: List<VisibleItemBounds>,
    fingerY: Float,
    contentSlopPx: Float,
): Int? {
    val blockStart = items.indexOfFirst { it.stableKey == draggedKey }
    if (blockStart < 0) return null
    val blockCount = todoBlockItemCount(items, blockStart)
    if (blockCount <= 0) return null

    val fullInsert = resolveFullInsertBefore(visibleBounds, fingerY, contentSlopPx) ?: return null
    val without = removeTodoBlock(items, draggedKey).first
    val insertBefore = mapFullInsertToWithout(fullInsert, blockStart, blockCount)
        .coerceIn(0, without.size)
    return insertBefore.takeIf { isValidInsertBefore(without, it, draggedLevel) }
}

/**
 * Parent that would own a child inserted at [insertBefore], at exactly
 * [requiredParentLevel]. Null when that position is not under such a parent.
 */
internal fun findOwningParentBefore(
    items: List<ReorderTodoItem>,
    insertBefore: Int,
    requiredParentLevel: Int,
): ReorderTodoItem? {
    if (insertBefore !in 0..items.size || requiredParentLevel < 0) return null
    for (i in insertBefore - 1 downTo 0) {
        val itemLevel = items[i].todo.indentLevel
        if (itemLevel == requiredParentLevel) return items[i]
        if (itemLevel < requiredParentLevel) return null
    }
    return null
}

internal fun findBeforeSiblingItem(items: List<ReorderTodoItem>, index: Int): ReorderTodoItem? {
    if (index !in items.indices) return null
    return findBeforeSiblingAtInsert(items, index + 1, items[index].todo.indentLevel)
}

internal fun findBeforeSiblingAtInsert(
    itemsWithout: List<ReorderTodoItem>,
    insertBefore: Int,
    level: Int,
): ReorderTodoItem? {
    var i = insertBefore
    while (i < itemsWithout.size) {
        if (itemsWithout[i].todo.indentLevel < level) return null
        if (itemsWithout[i].todo.indentLevel == level) return itemsWithout[i]
        i++
    }
    return null
}

/** Resolves a committed drag into a persistence action, or null when nothing changed. */
internal fun resolveTodoDragCommit(
    items: List<ReorderTodoItem>,
    draggedKey: String,
    insertBefore: Int,
): TodoDragCommit? {
    val draggedIndex = items.indexOfFirst { it.stableKey == draggedKey }
    if (draggedIndex < 0) return null

    val (withoutBlock, draggedItem) = removeTodoBlock(items, draggedKey)
    if (draggedItem == null) return null
    // insertBefore is indexed in the list-without-block; the original root index matches.
    if (insertBefore == draggedIndex) return null

    val draggedLevel = draggedItem.todo.indentLevel
    if (draggedLevel <= 0) {
        val startOrder = items
            .filter { it.todo.indentLevel == 0 }
            .map { it.todo.lineIndex }
        val topsWithout = withoutBlock
            .filter { it.todo.indentLevel == 0 }
            .map { it.todo.lineIndex }
        val topsBefore = withoutBlock
            .take(insertBefore)
            .count { it.todo.indentLevel == 0 }
        val newOrder = topsWithout.toMutableList().apply {
            add(topsBefore.coerceIn(0, size), draggedItem.todo.lineIndex)
        }
        if (startOrder == newOrder) return null
        return TodoDragCommit.ReorderTopLevel(
            orderedLineIndices = startOrder,
            newOrderedLineIndices = newOrder,
            insertBefore = insertBefore,
        )
    }

    if (!isValidInsertBefore(withoutBlock, insertBefore, draggedLevel)) return null
    val parent = findOwningParentBefore(withoutBlock, insertBefore, draggedLevel - 1) ?: return null
    val beforeSibling = findBeforeSiblingAtInsert(withoutBlock, insertBefore, draggedLevel)
    val startParent = findOwningParentBefore(items, draggedIndex, draggedLevel - 1)
    val startBeforeSibling = findBeforeSiblingItem(items, draggedIndex)
    if (
        startParent?.todo?.lineIndex == parent.todo.lineIndex &&
        startBeforeSibling?.todo?.lineIndex == beforeSibling?.todo?.lineIndex
    ) {
        return null
    }
    return TodoDragCommit.MoveUnderParent(
        todoLineIndex = draggedItem.todo.lineIndex,
        newParentLineIndex = parent.todo.lineIndex,
        beforeSiblingLineIndex = beforeSibling?.todo?.lineIndex,
        insertBefore = insertBefore,
    )
}
