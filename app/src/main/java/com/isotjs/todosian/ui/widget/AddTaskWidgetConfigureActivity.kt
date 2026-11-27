package com.isotjs.todosian.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.isotjs.todosian.R
import com.isotjs.todosian.TodosianApplication
import com.isotjs.todosian.data.settings.AppSettings
import com.isotjs.todosian.data.settings.ThemeMode
import com.isotjs.todosian.ui.theme.TodosianTheme
import kotlinx.coroutines.launch

/**
 * Configuration screen for the "Add task" widget. It only edits appearance (background theme and
 * opacity); the widget itself is a single button with no list selection to make.
 */
class AddTaskWidgetConfigureActivity : ComponentActivity() {

    private val backgroundTheme = mutableStateOf(WidgetBackgroundTheme.SYSTEM)
    private val backgroundOpacity = mutableIntStateOf(100)

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

        val boundProvider = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)
            ?.provider
        if (boundProvider != null && boundProvider.packageName != packageName) {
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

        // Pre-fill the screen with the widget's current appearance (reconfigure case).
        lifecycleScope.launch {
            val state = runCatching {
                val glanceId = GlanceAppWidgetManager(this@AddTaskWidgetConfigureActivity)
                    .getGlanceIdBy(appWidgetId)
                getAppWidgetState(
                    this@AddTaskWidgetConfigureActivity,
                    PreferencesGlanceStateDefinition,
                    glanceId,
                )
            }.getOrDefault(null)
            val appearance = state?.let { WidgetAppearance.from(it) } ?: WidgetAppearance()
            backgroundTheme.value = appearance.theme
            backgroundOpacity.intValue = appearance.opacityPercent
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
                    AddTaskWidgetConfigureScreen(
                        backgroundTheme = backgroundTheme.value,
                        backgroundOpacity = backgroundOpacity.intValue,
                        onBackgroundThemeChange = { backgroundTheme.value = it },
                        onBackgroundOpacityChange = { backgroundOpacity.intValue = it },
                        onConfirm = { confirm(appWidgetId) },
                        onCancel = {
                            setResult(RESULT_CANCELED)
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun confirm(appWidgetId: Int) {
        val context = this
        lifecycleScope.launch {
            val result = runCatching {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) // validates the binding
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        WidgetAppearance(backgroundTheme.value, backgroundOpacity.intValue).writeTo(this)
                    }
                }
                AddTaskWidget().updateAll(context)
            }
            if (result.isSuccess) {
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            } else {
                setResult(RESULT_CANCELED)
            }
            finish()
        }
    }
}

@Composable
private fun AddTaskWidgetConfigureScreen(
    backgroundTheme: WidgetBackgroundTheme,
    backgroundOpacity: Int,
    onBackgroundThemeChange: (WidgetBackgroundTheme) -> Unit,
    onBackgroundOpacityChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WidgetConfigureScaffold(
        title = stringResource(R.string.widget_add_task_configure_title),
        onConfirm = onConfirm,
        onCancel = onCancel,
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item(key = APPEARANCE_KEY) {
                WidgetAppearanceSection(
                    theme = backgroundTheme,
                    opacityPercent = backgroundOpacity,
                    onThemeChange = onBackgroundThemeChange,
                    onOpacityChange = onBackgroundOpacityChange,
                    onOpacityChangeFinished = {},
                )
            }
        }
    }
}

private const val APPEARANCE_KEY = "appearance"
