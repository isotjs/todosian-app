package com.isotjs.todosian.ui.widget

import android.content.Intent
import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.isotjs.todosian.MainActivity
import com.isotjs.todosian.R
import com.isotjs.todosian.data.model.Todo

data class WidgetTaskItem(
    val categoryUri: String,
    val categoryName: String,
    val todo: Todo,
)

class TodosianWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // A freshly placed widget has no snapshot yet; load one from the files before composing
        // (provideGlance has a generous time budget, unlike the live composition).
        if (!WidgetRefresher.hasSnapshot(context, id)) {
            WidgetRefresher.refreshWidgets(context)
        }

        provideContent {
            // Render purely from the Glance state: WidgetRefresher pushes a fresh snapshot after
            // every change, which triggers exactly one recomposition with fresh data. The content
            // never touches the file system itself.
            val snapshot = WidgetSnapshot.fromPreferences(currentState())

            GlanceTheme {
                WidgetContent(
                    hasFolder = snapshot?.hasFolder ?: false,
                    items = snapshot?.items.orEmpty(),
                    activeCount = snapshot?.activeCount ?: 0,
                    title = snapshot?.title,
                    selectedCategoryUri = snapshot?.selectedCategoryUri,
                    missingList = snapshot?.missingList ?: false,
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        hasFolder: Boolean,
        items: List<WidgetTaskItem>,
        activeCount: Int,
        title: String?,
        selectedCategoryUri: String?,
        missingList: Boolean,
    ) {
        val context = LocalContext.current
        val openAppAction = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        // A widget bound to a single note routes "+" straight into that note; unbound widgets
        // open the regular "pick a note" flow.
        val openAddTaskAction = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (selectedCategoryUri != null) {
                    putExtra(MainActivity.EXTRA_OPEN_ADD_TASK_CATEGORY, selectedCategoryUri)
                } else {
                    putExtra(MainActivity.EXTRA_OPEN_ADD_TASK, true)
                }
            }
        )
        val headerTitle = title ?: context.getString(R.string.widget_title_all_lists)

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
                        text = headerTitle,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    if (hasFolder && !missingList) {
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
            } else if (missingList) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(openAppAction),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = context.getString(R.string.widget_missing_list),
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
