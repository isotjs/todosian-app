package com.isotjs.todosian.ui.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.isotjs.todosian.TodosianApplication
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val KEY_WIDGET_HAS_FOLDER = booleanPreferencesKey("widget_has_folder")
internal val KEY_WIDGET_ACTIVE_COUNT = intPreferencesKey("widget_active_count")
internal val KEY_WIDGET_ITEM_COUNT = intPreferencesKey("widget_item_count")
private const val ITEM_KEY_PREFIX = "widget_item_"

/**
 * Snapshot of the widget content, stored in the widget's Glance state (Preferences DataStore).
 *
 * The widget composition renders exclusively from this snapshot; it never reads the Markdown
 * files itself. This makes every refresh a single, atomic recomposition: Glance reliably pushes
 * new state into a live session, and a new session reads the state that was just written.
 */
internal data class WidgetSnapshot(
    val hasFolder: Boolean,
    val activeCount: Int,
    val items: List<WidgetTaskItem>,
) {
    /**
     * Writes all snapshot keys into [prefs], first removing the item keys of any previous
     * snapshot. Without this, optional fields that are absent in the new snapshot (currently
     * `due_date`) would keep stale values from whatever item previously occupied the same index.
     */
    fun writeTo(prefs: MutablePreferences) {
        val previousCount = prefs[KEY_WIDGET_ITEM_COUNT] ?: 0
        for (index in 0 until maxOf(previousCount, items.size)) {
            val prefix = "$ITEM_KEY_PREFIX$index."
            prefs.remove(stringPreferencesKey("${prefix}category_uri"))
            prefs.remove(stringPreferencesKey("${prefix}category_name"))
            prefs.remove(stringPreferencesKey("${prefix}text"))
            prefs.remove(stringPreferencesKey("${prefix}line_index"))
            prefs.remove(stringPreferencesKey("${prefix}is_done"))
            prefs.remove(stringPreferencesKey("${prefix}due_date"))
        }

        prefs[KEY_WIDGET_HAS_FOLDER] = hasFolder
        prefs[KEY_WIDGET_ACTIVE_COUNT] = activeCount
        prefs[KEY_WIDGET_ITEM_COUNT] = items.size
        items.forEachIndexed { index, item ->
            val prefix = "$ITEM_KEY_PREFIX$index."
            prefs[stringPreferencesKey("${prefix}category_uri")] = item.categoryUri
            prefs[stringPreferencesKey("${prefix}category_name")] = item.categoryName
            prefs[stringPreferencesKey("${prefix}text")] = item.todo.text
            prefs[intPreferencesKey("${prefix}line_index")] = item.todo.lineIndex
            prefs[booleanPreferencesKey("${prefix}is_done")] = item.todo.isDone
            item.todo.dueDate?.let { prefs[stringPreferencesKey("${prefix}due_date")] = it }
        }
    }

    fun toPreferences(): Preferences = mutablePreferencesOf().also { writeTo(it) }

    companion object {
        /** Returns null when no snapshot was written yet (e.g. a freshly placed widget). */
        fun fromPreferences(prefs: Preferences): WidgetSnapshot? {
            val hasFolder = prefs[KEY_WIDGET_HAS_FOLDER] ?: return null
            val itemCount = prefs[KEY_WIDGET_ITEM_COUNT] ?: 0
            val items = ArrayList<WidgetTaskItem>(itemCount)
            for (index in 0 until itemCount) {
                val prefix = "$ITEM_KEY_PREFIX$index."
                val categoryUri = prefs[stringPreferencesKey("${prefix}category_uri")] ?: continue
                val text = prefs[stringPreferencesKey("${prefix}text")] ?: continue
                val lineIndex = prefs[intPreferencesKey("${prefix}line_index")] ?: continue
                val isDone = prefs[booleanPreferencesKey("${prefix}is_done")] ?: false
                val dueDate = prefs[stringPreferencesKey("${prefix}due_date")]
                items.add(
                    WidgetTaskItem(
                        categoryUri = categoryUri,
                        categoryName = prefs[stringPreferencesKey("${prefix}category_name")] ?: "",
                        todo = Todo(
                            // `id` is a per-parse random UUID and is not part of the snapshot.
                            id = "",
                            text = text,
                            isDone = isDone,
                            lineIndex = lineIndex,
                            dueDate = dueDate,
                        ),
                    )
                )
            }
            return WidgetSnapshot(
                hasFolder = hasFolder,
                activeCount = prefs[KEY_WIDGET_ACTIVE_COUNT] ?: 0,
                items = items,
            )
        }
    }
}

/**
 * Central refresh point for the Todosian widget.
 *
 * After any change (a write from the app UI, a checkbox toggle in the widget, a manual refresh),
 * this re-reads the task list from the Markdown files (which remain the single source of truth),
 * stores it as a [WidgetSnapshot] in the Glance state of every placed widget, and pushes an
 * update to all of them.
 */
object WidgetRefresher {
    private const val TAG = "WidgetRefresher"
    private const val MAX_TASKS = 50

    private val refreshMutex = Mutex()

    /**
     * Reloads the task snapshot from the files and updates all placed [TodosianWidget] instances.
     * Concurrent calls are serialized so overlapping triggers cannot interleave state writes.
     */
    suspend fun refreshWidgets(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = runCatching { manager.getGlanceIds(TodosianWidget::class.java) }
            .getOrElse {
                Log.e(TAG, "Unable to query placed TodosianWidgets", it)
                return
            }
        if (glanceIds.isEmpty()) return

        val app = context.applicationContext as? TodosianApplication
        val fileRepository = app?.fileRepository
        if (fileRepository == null) {
            Log.w(TAG, "Unable to access file repository")
            return
        }

        refreshMutex.withLock {
            val snapshot = loadSnapshot(fileRepository)
            runCatching {
                for (glanceId in glanceIds) {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply { snapshot.writeTo(this) }
                    }
                }
                // Push the new state; this starts a session if none is currently running.
                TodosianWidget().updateAll(context)
                Log.d(
                    TAG,
                    "Widget refresh completed: tasks=${snapshot.items.size} " +
                        "active=${snapshot.activeCount}",
                )
            }.onFailure { Log.e(TAG, "Failed to update widgets", it) }
        }
    }

    /** True once a snapshot has been written for [id] (used for freshly placed widgets). */
    internal suspend fun hasSnapshot(context: Context, id: GlanceId): Boolean =
        runCatching {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
                .contains(KEY_WIDGET_HAS_FOLDER)
        }.getOrDefault(false)

    private suspend fun loadSnapshot(fileRepository: FileRepository): WidgetSnapshot {
        val folderUri = fileRepository.getFolderUri()
        val taskItems = mutableListOf<WidgetTaskItem>()

        if (folderUri != null) {
            val categories = fileRepository.getCategories().getOrDefault(emptyList())
            for (category in categories) {
                val lines = fileRepository.readLines(category.uri).getOrDefault(emptyList())
                for (todo in MarkdownParser.parse(lines)) {
                    taskItems.add(
                        WidgetTaskItem(
                            categoryUri = category.uri.toString(),
                            categoryName = category.displayName,
                            todo = todo,
                        )
                    )
                }
            }
        }

        val activeTasks = taskItems.filter { !it.todo.isDone }
        val completedTasks = taskItems.filter { it.todo.isDone }
        Log.d(
            TAG,
            "loadSnapshot: folder=${folderUri != null} tasks=${taskItems.size} " +
                "active=${activeTasks.size} completed=${completedTasks.size}",
        )
        return WidgetSnapshot(
            hasFolder = folderUri != null,
            items = (activeTasks + completedTasks).take(MAX_TASKS),
            activeCount = activeTasks.size,
        )
    }
}
