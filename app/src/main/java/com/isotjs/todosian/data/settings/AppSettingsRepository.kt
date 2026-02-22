package com.isotjs.todosian.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    fun setThemeMode(themeMode: ThemeMode)
    fun setDynamicColorEnabled(enabled: Boolean)

    fun setShowDailyFocus(enabled: Boolean)
    fun setCategorySort(sort: CategorySort)
    fun setTodoGrouping(grouping: TodoGrouping)

    fun setEnableTasksPluginSupport(enabled: Boolean)

    fun setTasksPluginUseEmojisInUi(enabled: Boolean)
}

class SharedPrefsAppSettingsRepository(
    context: Context,
) : AppSettingsRepository {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val settings: Flow<AppSettings> = callbackFlow {
        fun sendCurrent() {
            trySend(readSettings()).isSuccess
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            sendCurrent()
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        sendCurrent()

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    override fun setThemeMode(themeMode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, themeMode.name).apply()
    }

    override fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    override fun setShowDailyFocus(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DAILY_FOCUS, enabled).apply()
    }

    override fun setCategorySort(sort: CategorySort) {
        prefs.edit().putString(KEY_CATEGORY_SORT, sort.name).apply()
    }

    override fun setTodoGrouping(grouping: TodoGrouping) {
        prefs.edit().putString(KEY_TODO_GROUPING, grouping.name).apply()
    }

    override fun setEnableTasksPluginSupport(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TASKS_PLUGIN, enabled).apply()
    }

    override fun setTasksPluginUseEmojisInUi(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TASKS_PLUGIN_UI_EMOJIS, enabled).apply()
    }

    private fun readSettings(): AppSettings {
        val themeMode = prefs.getString(KEY_THEME_MODE, null)?.let { raw ->
            runCatching { ThemeMode.valueOf(raw) }.getOrNull()
        } ?: ThemeMode.SYSTEM

        val categorySort = prefs.getString(KEY_CATEGORY_SORT, null)?.let { raw ->
            runCatching { CategorySort.valueOf(raw) }.getOrNull()
        } ?: CategorySort.A_Z

        val todoGrouping = prefs.getString(KEY_TODO_GROUPING, null)?.let { raw ->
            runCatching { TodoGrouping.valueOf(raw) }.getOrNull()
        } ?: TodoGrouping.GROUPED

        return AppSettings(
            themeMode = themeMode,
            dynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            showDailyFocus = prefs.getBoolean(KEY_SHOW_DAILY_FOCUS, true),
            categorySort = categorySort,
            todoGrouping = todoGrouping,
            enableTasksPluginSupport = prefs.getBoolean(KEY_TASKS_PLUGIN, false),
            tasksPluginUseEmojisInUi = prefs.getBoolean(KEY_TASKS_PLUGIN_UI_EMOJIS, false),
        )
    }

    private companion object {
        private const val PREFS_NAME = "todosian_settings"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_SHOW_DAILY_FOCUS = "show_daily_focus"
        private const val KEY_CATEGORY_SORT = "category_sort"
        private const val KEY_TODO_GROUPING = "todo_grouping"

        private const val KEY_TASKS_PLUGIN = "tasks_plugin_support"

        private const val KEY_TASKS_PLUGIN_UI_EMOJIS = "tasks_plugin_ui_emojis"
    }
}
