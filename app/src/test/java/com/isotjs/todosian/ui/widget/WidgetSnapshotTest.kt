package com.isotjs.todosian.ui.widget

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.isotjs.todosian.data.model.Todo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSnapshotTest {

    private fun todo(
        text: String,
        isDone: Boolean = false,
        lineIndex: Int = 0,
        dueDate: String? = null,
    ) = Todo(id = "", text = text, isDone = isDone, lineIndex = lineIndex, dueDate = dueDate)

    private fun item(
        text: String,
        isDone: Boolean = false,
        lineIndex: Int = 0,
        dueDate: String? = null,
        categoryUri: String = "content://category",
        categoryName: String = "Inbox",
    ) = WidgetTaskItem(
        categoryUri = categoryUri,
        categoryName = categoryName,
        todo = todo(text, isDone, lineIndex, dueDate),
    )

    @Test
    fun `round trip preserves items, order and fields`() {
        val snapshot = WidgetSnapshot(
            hasFolder = true,
            activeCount = 2,
            items = listOf(
                item("Active task", lineIndex = 3),
                item("Due task", lineIndex = 7, dueDate = "2026-10-12"),
                item("Done task", isDone = true, lineIndex = 1),
            ),
        )

        val restored = WidgetSnapshot.fromPreferences(snapshot.toPreferences())

        assertEquals(snapshot, restored)
    }

    @Test
    fun `round trip preserves empty snapshot`() {
        val snapshot = WidgetSnapshot(hasFolder = true, activeCount = 0, items = emptyList())

        val restored = WidgetSnapshot.fromPreferences(snapshot.toPreferences())

        assertEquals(snapshot, restored)
    }

    @Test
    fun `round trip preserves unicode, emoji and special characters`() {
        val snapshot = WidgetSnapshot(
            hasFolder = true,
            activeCount = 1,
            items = listOf(
                item("Ünïcodé 🎉 📅 – \"quoted\" \\backslash\\ € special", lineIndex = 42),
            ),
        )

        val restored = WidgetSnapshot.fromPreferences(snapshot.toPreferences())

        assertEquals(snapshot, restored)
    }

    @Test
    fun `round trip preserves empty text and empty category name`() {
        val snapshot = WidgetSnapshot(
            hasFolder = true,
            activeCount = 1,
            items = listOf(item("", categoryName = "", lineIndex = 0)),
        )

        val restored = WidgetSnapshot.fromPreferences(snapshot.toPreferences())

        assertEquals(snapshot, restored)
    }

    @Test
    fun `missing snapshot returns null`() {
        assertNull(WidgetSnapshot.fromPreferences(emptyPreferences()))
    }

    @Test
    fun `stale item keys beyond the new item count are ignored`() {
        val larger = WidgetSnapshot(
            hasFolder = true,
            activeCount = 2,
            items = listOf(item("First"), item("Second")),
        )
        val smaller = WidgetSnapshot(
            hasFolder = true,
            activeCount = 1,
            items = listOf(item("Only one now", lineIndex = 9)),
        )
        val prefs = larger.toPreferences().toMutablePreferences()

        smaller.writeTo(prefs)

        assertEquals(smaller, WidgetSnapshot.fromPreferences(prefs))
    }

    @Test
    fun `item with missing required key is skipped`() {
        // Simulate a corrupted snapshot: item 1 has no line_index key.
        val prefs = WidgetSnapshot(
            hasFolder = true,
            activeCount = 2,
            items = listOf(item("Kept", lineIndex = 0), item("Dropped", lineIndex = 1)),
        ).toPreferences().toMutablePreferences()
        prefs.remove(intPreferencesKey("widget_item_1.line_index"))

        val restored = WidgetSnapshot.fromPreferences(prefs)

        assertEquals(listOf(item("Kept", lineIndex = 0)), restored?.items)
        assertEquals(2, restored?.activeCount)
    }

    @Test
    fun `absent due date does not leak from the previous snapshot`() {
        val dated = WidgetSnapshot(
            hasFolder = true,
            activeCount = 1,
            items = listOf(item("Dated task", lineIndex = 0, dueDate = "2026-10-12")),
        )
        val undated = WidgetSnapshot(
            hasFolder = true,
            activeCount = 1,
            items = listOf(item("Undated task", lineIndex = 0)),
        )
        val prefs = dated.toPreferences().toMutablePreferences()

        undated.writeTo(prefs)

        val restored = WidgetSnapshot.fromPreferences(prefs)
        assertEquals(undated, restored)
        assertNull(restored?.items?.single()?.todo?.dueDate)
    }

    @Test
    fun `due date is cleared when the task moves to a different index`() {
        val before = WidgetSnapshot(
            hasFolder = true,
            activeCount = 1,
            items = listOf(item("Dated task", lineIndex = 0, dueDate = "2026-10-12"), item("Plain task", lineIndex = 1)),
        )
        val after = WidgetSnapshot(
            hasFolder = true,
            activeCount = 1,
            items = listOf(item("Plain task", lineIndex = 1), item("Dated task", lineIndex = 0, dueDate = "2026-10-12")),
        )
        val prefs = before.toPreferences().toMutablePreferences()

        after.writeTo(prefs)

        assertEquals(after, WidgetSnapshot.fromPreferences(prefs))
    }

    @Test
    fun `active count is preserved independently of the item list`() {
        // activeCount counts all active tasks, even when the item list was truncated to MAX_TASKS.
        val snapshot = WidgetSnapshot(hasFolder = true, activeCount = 99, items = emptyList())

        val restored = WidgetSnapshot.fromPreferences(snapshot.toPreferences())

        assertEquals(99, restored?.activeCount)
    }

    @Test
    fun `hasFolder false round trips`() {
        val snapshot = WidgetSnapshot(hasFolder = false, activeCount = 0, items = emptyList())

        val restored = WidgetSnapshot.fromPreferences(snapshot.toPreferences())

        assertEquals(snapshot, restored)
    }

    @Test
    fun `round trip preserves title, selected uri and missing list flag`() {
        val snapshot = WidgetSnapshot(
            hasFolder = true,
            activeCount = 1,
            items = listOf(item("Task", lineIndex = 0)),
            title = "Calendar",
            selectedCategoryUri = "content://note/Calendar",
            missingList = false,
        )

        val restored = WidgetSnapshot.fromPreferences(snapshot.toPreferences())

        assertEquals(snapshot, restored)
    }

    @Test
    fun `all lists snapshot has null title and selected uri`() {
        val prefs = WidgetSnapshot(hasFolder = true, activeCount = 0, items = emptyList())
            .toPreferences().toMutablePreferences()

        val restored = WidgetSnapshot.fromPreferences(prefs)

        assertNull(restored?.title)
        assertNull(restored?.selectedCategoryUri)
        assertFalse(restored?.missingList == true)
    }

    @Test
    fun `selection round trip preserves uri and name`() {
        val selection = WidgetSelection("content://note/Calendar", "Calendar")
        val prefs = emptyPreferences().toMutablePreferences()

        selection.writeTo(prefs)

        assertEquals(selection, WidgetSelection.from(prefs))
    }

    @Test
    fun `all lists selection removes stale keys`() {
        val prefs = WidgetSelection("content://note/Calendar", "Calendar")
            .let { emptyPreferences().toMutablePreferences().also(it::writeTo) }

        WidgetSelection.ALL.writeTo(prefs)

        assertEquals(WidgetSelection.ALL, WidgetSelection.from(prefs))
    }

    @Test
    fun `resolve matches by uri first`() {
        val keys = listOf(
            "content://note/a" to "Other name",
            "content://note/b" to "Calendar",
        )
        val selection = WidgetSelection("content://note/b", "Other name")

        assertEquals("content://note/b", resolveCategoryKey(keys, selection))
    }

    @Test
    fun `resolve falls back to stored name when uri is stale`() {
        val keys = listOf("content://note/new" to "Calendar renamed")
        val selection = WidgetSelection("content://note/old-uri", "Calendar renamed")

        assertEquals("content://note/new", resolveCategoryKey(keys, selection))
    }

    @Test
    fun `resolve name match is case insensitive`() {
        val keys = listOf("content://note/new" to "Calendar")
        val selection = WidgetSelection("content://gone", "cAlEnDaR")

        assertEquals("content://note/new", resolveCategoryKey(keys, selection))
    }

    @Test
    fun `resolve returns null for all lists selection`() {
        val keys = listOf("content://note/a" to "Calendar")

        assertNull(resolveCategoryKey(keys, WidgetSelection.ALL))
    }

    @Test
    fun `resolve returns null when nothing matches`() {
        val keys = listOf("content://note/a" to "Calendar")
        val selection = WidgetSelection("content://gone", "Deleted list")

        assertNull(resolveCategoryKey(keys, selection))
    }

    @Test
    fun `filter keeps all entries when show completed is on`() {
        val items = listOf(
            item("Active", lineIndex = 0),
            item("Done", isDone = true, lineIndex = 1),
        )

        assertEquals(items, filterCompletedItems(items, showCompleted = true))
    }

    @Test
    fun `filter drops checked entries when show completed is off`() {
        val items = listOf(
            item("Active", lineIndex = 0),
            item("Done", isDone = true, lineIndex = 1),
            item("Also done", isDone = true, lineIndex = 2),
        )

        assertEquals(listOf(item("Active", lineIndex = 0)), filterCompletedItems(items, showCompleted = false))
    }

    @Test
    fun `filter of empty list stays empty regardless of the flag`() {
        assertEquals(emptyList<WidgetTaskItem>(), filterCompletedItems(emptyList(), showCompleted = false))
        assertEquals(emptyList<WidgetTaskItem>(), filterCompletedItems(emptyList(), showCompleted = true))
    }
}
