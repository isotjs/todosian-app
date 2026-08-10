package com.isotjs.todosian.ui.category

import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.utils.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoDragReorderTest {

    private fun todo(
        text: String,
        lineIndex: Int,
        indentLevel: Int,
    ): Todo = Todo(
        id = "$text-$lineIndex",
        text = text,
        isDone = false,
        lineIndex = lineIndex,
        indentLevel = indentLevel,
    )

    private fun item(text: String, lineIndex: Int, indentLevel: Int): ReorderTodoItem {
        val t = todo(text, lineIndex, indentLevel)
        return ReorderTodoItem(todo = t, stableKey = "$text::$lineIndex")
    }

    /**
     * task1
     *   sub1
     *     subsub1
     * task2
     *   sub2
     *     subsub2
     * task3
     */
    private fun sampleItems(): List<ReorderTodoItem> = listOf(
        item("task1", 0, 0),
        item("sub1", 1, 1),
        item("subsub1", 2, 2),
        item("task2", 3, 0),
        item("sub2", 4, 1),
        item("subsub2", 5, 2),
        item("task3", 6, 0),
    )

    private fun boundsFor(items: List<ReorderTodoItem>, height: Float = 100f): List<VisibleItemBounds> =
        items.indices.map { index ->
            VisibleItemBounds(
                fullIndex = index,
                top = index * height,
                bottom = (index + 1) * height,
            )
        }

    @Test
    fun mapFullInsertToWithout_maps_hole_and_shifts_after_block() {
        // Block at index 1 with count 2 (sub1 + subsub1)
        assertEquals(0, mapFullInsertToWithout(0, blockStart = 1, blockCount = 2))
        assertEquals(1, mapFullInsertToWithout(1, blockStart = 1, blockCount = 2))
        assertEquals(1, mapFullInsertToWithout(2, blockStart = 1, blockCount = 2))
        assertEquals(1, mapFullInsertToWithout(3, blockStart = 1, blockCount = 2))
        assertEquals(2, mapFullInsertToWithout(4, blockStart = 1, blockCount = 2))
    }

    @Test
    fun resolveFullInsertBefore_uses_item_midpoints() {
        val bounds = boundsFor(sampleItems())
        assertEquals(1, resolveFullInsertBefore(bounds, fingerY = 120f, contentSlopPx = 20f))
        assertEquals(2, resolveFullInsertBefore(bounds, fingerY = 180f, contentSlopPx = 20f))
        assertEquals(7, resolveFullInsertBefore(bounds, fingerY = 680f, contentSlopPx = 20f))
        assertNull(resolveFullInsertBefore(bounds, fingerY = -50f, contentSlopPx = 20f))
    }

    @Test
    fun resolveValidDrop_subtask_same_parent_reorder() {
        val items = sampleItems()
        val result = resolveValidDropInsertBefore(
            items = items,
            draggedKey = items[4].stableKey,
            draggedLevel = 1,
            visibleBounds = boundsFor(items),
            fingerY = 180f,
            contentSlopPx = 20f,
        )
        assertEquals(2, result)
    }

    @Test
    fun resolveValidDrop_subtask_cross_parent() {
        val items = sampleItems()
        val result = resolveValidDropInsertBefore(
            items = items,
            draggedKey = items[1].stableKey,
            draggedLevel = 1,
            visibleBounds = boundsFor(items),
            fingerY = 380f,
            contentSlopPx = 20f,
        )
        assertEquals(2, result)
    }

    @Test
    fun resolveValidDrop_sub_subtask_rejects_between_task_and_subtask() {
        val items = sampleItems()
        val illegal = resolveValidDropInsertBefore(
            items = items,
            draggedKey = items[5].stableKey,
            draggedLevel = 2,
            visibleBounds = boundsFor(items),
            fingerY = 80f,
            contentSlopPx = 20f,
        )
        assertNull(illegal)
    }

    @Test
    fun resolveValidDrop_sub_subtask_accepts_under_other_subtask() {
        val items = sampleItems()
        val result = resolveValidDropInsertBefore(
            items = items,
            draggedKey = items[5].stableKey,
            draggedLevel = 2,
            visibleBounds = boundsFor(items),
            fingerY = 180f,
            contentSlopPx = 20f,
        )
        assertEquals(2, result)
    }

    @Test
    fun resolveValidDrop_original_hole_maps_to_dragged_index() {
        val items = sampleItems()
        val result = resolveValidDropInsertBefore(
            items = items,
            draggedKey = items[4].stableKey,
            draggedLevel = 1,
            visibleBounds = boundsFor(items),
            fingerY = 450f,
            contentSlopPx = 20f,
        )
        assertEquals(4, result)
    }

    @Test
    fun isValidInsertBefore_top_level_rejects_nested_gap() {
        val items = sampleItems()
        val without = removeTodoBlock(items, items[0].stableKey).first
        val nestedIndex = without.indexOfFirst { it.todo.indentLevel == 1 }
        assertEquals(false, isValidInsertBefore(without, nestedIndex, draggedLevel = 0))
    }

    @Test
    fun resolveTodoDragCommit_top_level_reorder() {
        val items = sampleItems()
        // After removing task1's block: [task2, sub2, subsub2, task3]; insert before task3.
        val commit = resolveTodoDragCommit(
            items = items,
            draggedKey = items[0].stableKey,
            insertBefore = 3,
        )
        assertTrue(commit is TodoDragCommit.ReorderTopLevel)
        val reorder = commit as TodoDragCommit.ReorderTopLevel
        assertEquals(listOf(0, 3, 6), reorder.orderedLineIndices)
        assertEquals(listOf(3, 0, 6), reorder.newOrderedLineIndices)
    }

    @Test
    fun resolveTodoDragCommit_move_subtask_under_other_parent() {
        val items = sampleItems()
        // Drag sub1; insert before sub2 (index 2 in without after removing sub1 block)
        val commit = resolveTodoDragCommit(
            items = items,
            draggedKey = items[1].stableKey,
            insertBefore = 2,
        )
        assertTrue(commit is TodoDragCommit.MoveUnderParent)
        val move = commit as TodoDragCommit.MoveUnderParent
        assertEquals(1, move.todoLineIndex)
        assertEquals(3, move.newParentLineIndex)
        assertEquals(4, move.beforeSiblingLineIndex)
    }

    @Test
    fun resolveTodoDragCommit_noop_when_same_place() {
        val items = sampleItems()
        assertNull(
            resolveTodoDragCommit(
                items = items,
                draggedKey = items[4].stableKey,
                insertBefore = 4,
            ),
        )
    }

    @Test
    fun toReorderItems_keeps_stable_keys_when_line_indices_shift() {
        val lines = listOf(
            "- [ ] Alpha",
            "- [ ] Beta",
            "- [ ] Gamma",
        )
        val before = toReorderItems(MarkdownParser.parse(lines), lines)
        val afterAlphaRemoved = listOf(
            "- [ ] Beta",
            "- [ ] Gamma",
        )
        val after = toReorderItems(MarkdownParser.parse(afterAlphaRemoved), afterAlphaRemoved)

        val betaBefore = before.first { it.todo.text == "Beta" }
        val betaAfter = after.first { it.todo.text == "Beta" }

        assertEquals(betaBefore.stableKey, betaAfter.stableKey)
        assertEquals(1, betaBefore.todo.lineIndex)
        assertEquals(0, betaAfter.todo.lineIndex)
    }
}
