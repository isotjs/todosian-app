package com.isotjs.todosian.ui.widget

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.isotjs.todosian.data.model.Todo
import org.junit.Assert.assertEquals
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
}
