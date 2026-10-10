package com.isotjs.todosian.ui.widget

import android.content.Context
import android.net.Uri
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
import com.isotjs.todosian.data.model.Category
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val KEY_WIDGET_HAS_FOLDER = booleanPreferencesKey("widget_has_folder")
internal val KEY_WIDGET_ACTIVE_COUNT = intPreferencesKey("widget_active_count")
internal val KEY_WIDGET_ITEM_COUNT = intPreferencesKey("widget_item_count")
internal val KEY_SELECTED_CATEGORY_URI = stringPreferencesKey("selected_category_uri")
internal val KEY_SELECTED_CATEGORY_NAME = stringPreferencesKey("selected_category_name")
private const val ITEM_KEY_PREFIX = "widget_item_"

/**
 * Which note a widget instance is bound to. Null [categoryUri] means "All lists" (the unbound,
 * backward-compatible mode). [categoryName] is stored alongside the URI so a stale URI (the note
 * was renamed, which changes its document URI) can still be resolved by display name.
 */
internal data class WidgetSelection(
    val categoryUri: String?,
    val categoryName: String?,
) {
    val isAllLists: Boolean get() = categoryUri == null

    fun writeTo(prefs: MutablePreferences) {
        if (categoryUri != null) prefs[KEY_SELECTED_CATEGORY_URI] = categoryUri
        else prefs.remove(KEY_SELECTED_CATEGORY_URI)
        if (categoryName != null) prefs[KEY_SELECTED_CATEGORY_NAME] = categoryName
        else prefs.remove(KEY_SELECTED_CATEGORY_NAME)
    }

    companion object {
        val ALL = WidgetSelection(null, null)

        fun from(prefs: Preferences): WidgetSelection =
            WidgetSelection(prefs[KEY_SELECTED_CATEGORY_URI], prefs[KEY_SELECTED_CATEGORY_NAME])
    }
}

/**
 * Pure core of the selection resolution, over (uri string, display name) pairs so it can be unit
 * tested without Android framework classes. Matches by URI first and falls back to the stored
 * display name (e.g. when the document URI changed). Returns the matched URI string, or null for
 * the "All lists" selection, or when the selected note no longer exists.
 */
internal fun resolveCategoryKey(
    categoryKeys: List<Pair<String, String>>,
    selection: WidgetSelection,
): String? {
    if (selection.isAllLists) return null
    categoryKeys.firstOrNull { it.first == selection.categoryUri }?.let { return it.first }
    val name = selection.categoryName?.takeIf { it.isNotBlank() } ?: return null
    return categoryKeys.firstOrNull { it.second.equals(name, ignoreCase = true) }?.first
}

/**
 * Resolves a widget's selection against the current category list. Matches by URI first and falls
 * back to the stored display name (e.g. when the document URI changed). Returns null for the
 * "All lists" selection, or when the selected note no longer exists.
 */
internal fun resolveSelectedCategory(
    categories: List<Category>,
    selection: WidgetSelection,
): Category? {
    if (selection.isAllLists) return null
    val resolvedKey = resolveCategoryKey(
        categoryKeys = categories.map { it.uri.toString() to it.displayName },
        selection = selection,
    ) ?: return null
    return categories.first { it.uri.toString() == resolvedKey }
}

/**
 * Snapshot of one widget instance's content, stored in that widget's Glance state (Preferences
 * DataStore). The widget composition renders exclusively from this snapshot; it never reads the
 * Markdown files itself. This makes every refresh a single, atomic recomposition: Glance reliably
 * pushes new state into a live session, and a new session reads the state that was just written.
 *
 * [title] is the bound note's display name, or null for "All lists". [selectedCategoryUri] lets
 * the widget route its "+" action straight into that note. [missingList] marks a selection whose
 * note no longer exists (renamed or deleted).
 */
internal data class WidgetSnapshot(
    val hasFolder: Boolean,
    val activeCount: Int,
    val items: List<WidgetTaskItem>,
    val title: String? = null,
    val selectedCategoryUri: String? = null,
    val missingList: Boolean = false,
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
        if (title != null) prefs[TITLE_KEY] = title else prefs.remove(TITLE_KEY)
        if (selectedCategoryUri != null) prefs[SELECTED_URI_KEY] = selectedCategoryUri
        else prefs.remove(SELECTED_URI_KEY)
        prefs[MISSING_LIST_KEY] = missingList
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
        private val TITLE_KEY = stringPreferencesKey("widget_title")
        private val SELECTED_URI_KEY = stringPreferencesKey("widget_selected_category_uri")
        private val MISSING_LIST_KEY = booleanPreferencesKey("widget_missing_list")

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
                title = prefs[TITLE_KEY],
                selectedCategoryUri = prefs[SELECTED_URI_KEY],
                missingList = prefs[MISSING_LIST_KEY] ?: false,
            )
        }
    }
}

/**
 * Central refresh point for the Todosian widget.
 *
 * After any change (a write from the app UI, a checkbox toggle in the widget, a manual refresh),
 * this re-reads the task list from the Markdown files (which remain the single source of truth),
 * stores a per-widget [WidgetSnapshot] in each placed widget's Glance state, and pushes an update
 * to all of them. Each widget shows only its bound note (see [WidgetSelection]); widgets without a
 * selection show all notes, matching the pre-selection behavior.
 */
object WidgetRefresher {
    private const val TAG = "WidgetRefresher"
    private const val MAX_TASKS = 50

    private val refreshMutex = Mutex()

    /**
     * Reloads the task snapshots from the files and updates all placed [TodosianWidget] instances.
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
            val categories = fileRepository.getCategories().getOrDefault(emptyList())
            val selections = glanceIds.map { glanceId ->
                runCatching {
                    WidgetSelection.from(
                        getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
                    )
                }.getOrElse {
                    Log.w(TAG, "Unable to read widget state for $glanceId", it)
                    WidgetSelection.ALL
                }
            }

            // Read each needed file exactly once per refresh, no matter how many widgets share it.
            val neededUris = buildList {
                if (selections.any { it.isAllLists }) categories.forEach { add(it.uri) }
                selections.forEach { selection ->
                    resolveSelectedCategory(categories, selection)?.let { add(it.uri) }
                }
            }.toSet()
            val linesByUri = neededUris.associateWith { uri ->
                fileRepository.readLines(uri).getOrDefault(emptyList())
            }

            val hasFolder = fileRepository.getFolderUri() != null
            for ((glanceId, selection) in glanceIds.zip(selections)) {
                val snapshot = buildSnapshot(hasFolder, categories, linesByUri, selection)
                runCatching {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply { snapshot.writeTo(this) }
                    }
                }.onFailure { Log.e(TAG, "Failed to update widget state for $glanceId", it) }
            }
            // Push the new state; this starts a session if none is currently running.
            TodosianWidget().updateAll(context)
            Log.d(TAG, "Widget refresh completed: widgets=${glanceIds.size}")
        }
    }

    /** True once a snapshot has been written for [id] (used for freshly placed widgets). */
    internal suspend fun hasSnapshot(context: Context, id: GlanceId): Boolean =
        runCatching {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
                .contains(KEY_WIDGET_HAS_FOLDER)
        }.getOrDefault(false)

    /**
     * Builds the snapshot for one widget instance. For "All lists" the items are aggregated across
     * every note (active first, then completed), capped at [MAX_TASKS]; for a bound note only that
     * note's tasks are used.
     */
    private fun buildSnapshot(
        hasFolder: Boolean,
        categories: List<Category>,
        linesByUri: Map<Uri, List<String>>,
        selection: WidgetSelection,
    ): WidgetSnapshot {
        val resolved = resolveSelectedCategory(categories, selection)

        if (selection.categoryUri != null && resolved == null) {
            // The bound note was renamed or deleted; keep the title so the user knows which
            // selection to redo, and surface a recoverable state instead of an empty list.
            Log.d(TAG, "Selection not found: uri=${selection.categoryUri}")
            return WidgetSnapshot(
                hasFolder = true,
                activeCount = 0,
                items = emptyList(),
                title = selection.categoryName ?: "",
                selectedCategoryUri = null,
                missingList = true,
            )
        }

        val scopedCategories = if (resolved != null) listOf(resolved) else categories
        val taskItems = scopedCategories.flatMap { category ->
            val lines = linesByUri[category.uri].orEmpty()
            MarkdownParser.parse(lines).map { todo ->
                WidgetTaskItem(
                    categoryUri = category.uri.toString(),
                    categoryName = category.displayName,
                    todo = todo,
                )
            }
        }
        val activeTasks = taskItems.filter { !it.todo.isDone }
        val completedTasks = taskItems.filter { it.todo.isDone }
        Log.d(
            TAG,
            "loadSnapshot: allLists=${resolved == null} tasks=${taskItems.size} " +
                "active=${activeTasks.size} completed=${completedTasks.size}",
        )
        return WidgetSnapshot(
            hasFolder = hasFolder,
            activeCount = activeTasks.size,
            items = (activeTasks + completedTasks).take(MAX_TASKS),
            title = resolved?.displayName,
            selectedCategoryUri = resolved?.uri?.toString(),
            missingList = false,
        )
    }
}
