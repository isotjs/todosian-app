package com.isotjs.todosian.ui.widget

import android.content.Intent
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.currentState
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.isotjs.todosian.MainActivity
import com.isotjs.todosian.R
import com.isotjs.todosian.TodosianApplication
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.utils.MarkdownParser

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

private val KEY_LAST_UPDATE = longPreferencesKey("last_update_timestamp")

data class WidgetTaskItem(
    val categoryUri: String,
    val categoryName: String,
    val todo: Todo,
)

private data class WidgetData(
    val hasFolder: Boolean,
    val items: List<WidgetTaskItem>,
    val activeCount: Int,
)

object WidgetUpdater {
    private const val TAG = "WidgetUpdater"

    suspend fun updateAllWidgets(context: Context) {
        runCatching {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TodosianWidget::class.java)
            for (glanceId in glanceIds) {
                // Bumping the timestamp makes any still-alive Glance session reload its data.
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[KEY_LAST_UPDATE] = System.currentTimeMillis()
                    }
                }
            }
            // Start a session if none is currently running (e.g. the widget was idle).
            TodosianWidget().updateAll(context)
        }.onFailure { Log.e(TAG, "Failed to update widgets", it) }
    }
}

class TodosianWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    private companion object {
        const val MAX_TASKS = 50
        const val TAG = "TodosianWidget"
    }

    private suspend fun loadData(fileRepository: FileRepository?): WidgetData {
        val folderUri = fileRepository?.getFolderUri()
        val taskItems = mutableListOf<WidgetTaskItem>()

        if (folderUri != null) {
            val categories = fileRepository.getCategories().getOrDefault(emptyList())
            for (category in categories) {
                val lines = fileRepository.readLines(category.uri).getOrDefault(emptyList())
                val todos = MarkdownParser.parse(lines)
                for (todo in todos) {
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
        Log.d(TAG, "loadData: folder=${folderUri != null} tasks=${taskItems.size} " +
            "active=${activeTasks.size} completed=${completedTasks.size}")
        return WidgetData(
            hasFolder = folderUri != null,
            items = (activeTasks + completedTasks).take(MAX_TASKS),
            activeCount = activeTasks.size,
        )
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? TodosianApplication
        val fileRepository = app?.fileRepository

        // Load initial data before provideContent, as provideGlance times out shortly after.
        val initialData = loadData(fileRepository)
        val initialRefreshKey =
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_LAST_UPDATE] ?: 0L

        provideContent {
            // Observe the refresh timestamp so a manual refresh reloads data even while this
            // Glance session is still alive (update() alone would only recompose stale data).
            val refreshKey = currentState(KEY_LAST_UPDATE) ?: 0L
            val data by produceState(initialValue = initialData, refreshKey) {
                if (refreshKey != initialRefreshKey) {
                    value = loadData(fileRepository)
                }
            }

            GlanceTheme {
                WidgetContent(
                    hasFolder = data.hasFolder,
                    items = data.items,
                    activeCount = data.activeCount,
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        hasFolder: Boolean,
        items: List<WidgetTaskItem>,
        activeCount: Int,
    ) {
        val context = LocalContext.current
        val openAppAction = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        val openAddTaskAction = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_ADD_TASK, true)
            }
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp),
        ) {
            // Header
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(openAppAction),
                ) {
                    Text(
                        text = context.getString(R.string.app_name),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    if (hasFolder) {
                        Text(
                            text = "$activeCount active",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }

                Button(
                    text = "↻",
                    onClick = actionRunCallback<RefreshWidgetAction>(),
                    modifier = GlanceModifier.semantics {
                        contentDescription = context.getString(R.string.widget_refresh)
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = GlanceTheme.colors.primaryContainer,
                        contentColor = GlanceTheme.colors.onPrimaryContainer,
                    ),
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                Button(
                    text = "+",
                    onClick = openAddTaskAction,
                    modifier = GlanceModifier.semantics {
                        contentDescription = context.getString(R.string.widget_add_task)
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = GlanceTheme.colors.primaryContainer,
                        contentColor = GlanceTheme.colors.onPrimaryContainer,
                    ),
                )
            }

            // Body Content
            if (!hasFolder) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(openAppAction),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = context.getString(R.string.widget_no_folder),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 13.sp,
                        ),
                    )
                }
            } else if (items.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(openAppAction),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = context.getString(R.string.widget_no_tasks),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 14.sp,
                        ),
                    )
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier.fillMaxSize(),
                ) {
                    items(
                        items = items,
                        itemId = { item -> (item.categoryUri + ":" + item.todo.lineIndex + ":" + item.todo.text + ":" + item.todo.isDone).hashCode().toLong() },
                    ) { item ->
                        TaskRow(item)
                    }
                }
            }
        }
    }

    @Composable
    private fun TaskRow(item: WidgetTaskItem) {
        val todo = item.todo
        val isDone = todo.isDone

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val checkBoxAction = actionRunCallback<ToggleTaskAction>(
                actionParametersOf(
                    ToggleTaskAction.PARAM_CATEGORY_URI to item.categoryUri,
                    ToggleTaskAction.PARAM_LINE_INDEX to todo.lineIndex,
                    ToggleTaskAction.PARAM_TODO_TEXT to todo.text,
                )
            )

            CheckBox(
                checked = isDone,
                onCheckedChange = checkBoxAction,
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            Column(
                modifier = GlanceModifier
                    .defaultWeight(),
            ) {
                Text(
                    text = todo.text,
                    style = TextStyle(
                        color = if (isDone) GlanceTheme.colors.outline else GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    maxLines = 1,
                )

                val details = listOfNotNull(
                    item.categoryName.takeIf { it.isNotBlank() },
                    todo.dueDate?.let { "📅 $it" },
                ).joinToString(" • ")

                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
