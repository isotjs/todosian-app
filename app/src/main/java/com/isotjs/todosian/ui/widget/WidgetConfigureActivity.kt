package com.isotjs.todosian.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.isotjs.todosian.R
import com.isotjs.todosian.TodosianApplication
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.model.Category
import com.isotjs.todosian.data.settings.AppSettings
import com.isotjs.todosian.data.settings.ThemeMode
import com.isotjs.todosian.ui.components.TodosianDimens
import com.isotjs.todosian.ui.components.TodosianEmptyState
import com.isotjs.todosian.ui.components.TodosianLoadingIndicator
import com.isotjs.todosian.ui.theme.TodosianTheme
import kotlinx.coroutines.launch

/**
 * Configuration screen shown when the user places (or long-press → Edit reconfigures) the
 * Todosian widget. Lets them bind the widget instance to a single note, or keep it showing all
 * notes. The selection is persisted into that widget instance's Glance state.
 */
class WidgetConfigureActivity : ComponentActivity() {

    // Hoisted "show completed" preference; pre-filled asynchronously from the widget state so
    // reconfiguring shows the widget's current value.
    private val showCompleted = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Not launched through the widget configuration protocol; cancel the placement.
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val app = application as? TodosianApplication
        if (app == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        // Pre-fill the screen with the widget's current preferences (reconfigure case); the
        // state read is suspending, so it lands in the hoisted state asynchronously.
        lifecycleScope.launch {
            showCompleted.value = runCatching {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigureActivity)
                    .getGlanceIdBy(appWidgetId)
                getAppWidgetState(
                    this@WidgetConfigureActivity,
                    PreferencesGlanceStateDefinition,
                    glanceId,
                )[KEY_SHOW_COMPLETED] ?: true
            }.getOrDefault(true)
        }

        val initialSettings = app.appSettingsRepository.settings
        setContent {
            val settings by initialSettings.collectAsStateWithLifecycle(
                initialValue = AppSettings(),
            )
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            TodosianTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColorEnabled,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    WidgetConfigureScreen(
                        fileRepository = app.fileRepository,
                        showCompleted = showCompleted.value,
                        onShowCompletedChange = { checked ->
                            showCompleted.value = checked
                            persistPreferences(appWidgetId, selection = null, showCompleted = checked)
                        },
                        onConfirm = { selection ->
                            persistPreferences(appWidgetId, selection, showCompleted.value)
                        },
                        onCancel = {
                            setResult(RESULT_CANCELED)
                            finish()
                        },
                    )
                }
            }
        }
    }

    /**
     * Persists widget preferences and rebuilds the snapshots so changes show immediately.
     *
     * A non-null [selection] is the placement-confirming write (finishes with RESULT_OK);
     * a null selection only persists the "show completed" toggle, leaving the activity open
     * and the stored selection untouched.
     */
    private fun persistPreferences(appWidgetId: Int, selection: WidgetSelection?, showCompleted: Boolean) {
        val context = this
        lifecycleScope.launch {
            val result = runCatching {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) // validates the binding
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        selection?.writeTo(this)
                        this[KEY_SHOW_COMPLETED] = showCompleted
                    }
                }
                // Rebuild the snapshots now so the widget shows the new preferences immediately.
                // A snapshot may already exist (a placement-time update can race the picker),
                // so writing the state alone would leave the old content on screen until
                // the next file change.
                WidgetRefresher.refreshWidgets(context)
            }
            if (selection != null) {
                if (result.isSuccess) {
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                } else {
                    setResult(RESULT_CANCELED)
                }
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigureScreen(
    fileRepository: FileRepository?,
    showCompleted: Boolean,
    onShowCompletedChange: (Boolean) -> Unit,
    onConfirm: (WidgetSelection) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember { mutableStateOf(fileRepository != null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    LaunchedEffect(fileRepository) {
        if (fileRepository == null) {
            isLoading = false
            return@LaunchedEffect
        }
        categories = fileRepository.getCategories().getOrDefault(emptyList())
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.widget_configure_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    TodosianLoadingIndicator()
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = TodosianDimens.ScreenHorizontalPadding),
                ) {
                    item(key = SHOW_COMPLETED_KEY) {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(R.string.widget_configure_show_completed))
                            },
                            trailingContent = {
                                Switch(checked = showCompleted, onCheckedChange = onShowCompletedChange)
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                    item(key = ALL_LISTS_KEY) {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(R.string.widget_title_all_lists))
                            },
                            supportingContent = {
                                Text(text = stringResource(R.string.widget_configure_all_lists_hint))
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                            modifier = Modifier.clickable {
                                onConfirm(WidgetSelection.ALL)
                            },
                        )
                    }
                    items(
                        items = categories,
                        key = { it.uri.toString() },
                    ) { category ->
                        ListItem(
                            headlineContent = { Text(text = category.displayName) },
                            supportingContent = {
                                Text(text = "${category.todoCount} tasks")
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                            modifier = Modifier.clickable {
                                onConfirm(WidgetSelection(category.uri.toString(), category.displayName))
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val ALL_LISTS_KEY = "all_lists"
private const val SHOW_COMPLETED_KEY = "show_completed"
