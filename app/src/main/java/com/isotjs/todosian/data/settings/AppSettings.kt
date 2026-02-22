package com.isotjs.todosian.data.settings

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val showDailyFocus: Boolean = true,
    val categorySort: CategorySort = CategorySort.A_Z,
    val todoGrouping: TodoGrouping = TodoGrouping.GROUPED,
    val enableTasksPluginSupport: Boolean = false,
    val tasksPluginUseEmojisInUi: Boolean = false,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class CategorySort {
    A_Z,
    MOST_REMAINING,
}

enum class TodoGrouping {
    GROUPED,
    FILE_ORDER,
}
