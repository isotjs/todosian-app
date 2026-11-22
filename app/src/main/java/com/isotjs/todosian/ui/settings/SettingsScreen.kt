package com.isotjs.todosian.ui.settings

import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DensitySmall
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isotjs.todosian.BuildConfig
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.PreferencesManager
import com.isotjs.todosian.data.model.Category
import com.isotjs.todosian.data.settings.AppSettings
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.data.settings.CategoryLayout
import com.isotjs.todosian.data.settings.CategorySort
import com.isotjs.todosian.data.settings.DailyFocusMode
import com.isotjs.todosian.data.settings.NewTodoFilePosition
import com.isotjs.todosian.data.settings.ThemeMode
import com.isotjs.todosian.data.settings.TodoGrouping
import com.isotjs.todosian.data.settings.TodoSort
import com.isotjs.todosian.ui.components.ChangelogBottomSheet
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    fileRepository: FileRepository,
    appSettingsRepository: AppSettingsRepository,
    preferencesManager: PreferencesManager,
    onBack: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(fileRepository, appSettingsRepository),
    )
    val settings by appSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val storageState by viewModel.storageState.collectAsStateWithLifecycle()

    // The view model scans storage on creation; only re-scan when returning from a sub-screen.
    var hasResumedOnce by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (hasResumedOnce) {
            viewModel.refreshStorageStatus()
        } else {
            hasResumedOnce = true
        }
        onPauseOrDispose {}
    }

    var showChangelogSheet by remember { mutableStateOf(false) }
    var showLaunchCategorySheet by remember { mutableStateOf(false) }
    var showDailyFocusModeSheet by remember { mutableStateOf(false) }
    var showCategorySortSheet by remember { mutableStateOf(false) }
    var showTodoGroupingSheet by remember { mutableStateOf(false) }
    var showTodoSortSheet by remember { mutableStateOf(false) }
    var showNewTodoFilePositionSheet by remember { mutableStateOf(false) }
    var showDefaultAddTaskFileSheet by remember { mutableStateOf(false) }

    var launchCategoryUri by remember { mutableStateOf(preferencesManager.getDefaultLaunchCategoryUri()) }
    val launchCategoryName by produceState<String?>(initialValue = null, launchCategoryUri) {
        value = launchCategoryUri
            ?.let { fileRepository.getDisplayName(it).getOrNull()?.removeSuffix(".md") }
            ?.takeIf { it.isNotBlank() }
    }

    var defaultAddTaskFileUri by remember { mutableStateOf(preferencesManager.getDefaultAddTaskFileUri()) }
    val defaultAddTaskFileName by produceState<String?>(initialValue = null, defaultAddTaskFileUri) {
        value = defaultAddTaskFileUri?.let { fileRepository.getDisplayName(it).getOrNull()?.removeSuffix(".md") }
    }

    val reminderTimeLabel = remember(settings.reminderTimeHour, settings.reminderTimeMinute) {
        LocalTime.of(settings.reminderTimeHour, settings.reminderTimeMinute)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    if (showLaunchCategorySheet) {
        val categories by produceState(initialValue = emptyList<Category>()) {
            value = fileRepository.getCategories().getOrDefault(emptyList())
        }
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_launch_category),
            options = listOf(
                ChoiceOption<Uri?>(
                    value = null,
                    label = stringResource(R.string.settings_launch_category_overview),
                    icon = Icons.Filled.Home,
                ),
            ) + categories.map {
                ChoiceOption(value = it.uri, label = it.displayName)
            },
            selected = launchCategoryUri,
            onSelect = { uri ->
                if (uri == null) {
                    preferencesManager.clearDefaultLaunchCategoryUri()
                } else {
                    preferencesManager.saveDefaultLaunchCategoryUri(uri)
                }
                launchCategoryUri = uri
            },
            onDismiss = { showLaunchCategorySheet = false },
        )
    }

    if (showDailyFocusModeSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_daily_focus_mode),
            options = listOf(
                ChoiceOption(
                    DailyFocusMode.TODAY,
                    stringResource(R.string.settings_daily_focus_mode_today),
                ),
                ChoiceOption(
                    DailyFocusMode.OVERDUE,
                    stringResource(R.string.settings_daily_focus_mode_overdue),
                ),
                ChoiceOption(
                    DailyFocusMode.TODAY_AND_OVERDUE,
                    stringResource(R.string.settings_daily_focus_mode_today_overdue),
                ),
            ),
            selected = settings.dailyFocusMode,
            onSelect = { appSettingsRepository.setDailyFocusMode(it) },
            onDismiss = { showDailyFocusModeSheet = false },
        )
    }

    if (showCategorySortSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_category_sort),
            options = listOf(
                ChoiceOption(
                    CategorySort.A_Z,
                    stringResource(R.string.settings_sort_az),
                    Icons.AutoMirrored.Filled.Sort,
                ),
                ChoiceOption(
                    CategorySort.MOST_REMAINING,
                    stringResource(R.string.settings_sort_remaining),
                    Icons.Filled.LowPriority,
                ),
            ),
            selected = settings.categorySort,
            onSelect = { appSettingsRepository.setCategorySort(it) },
            onDismiss = { showCategorySortSheet = false },
        )
    }

    if (showTodoGroupingSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_todo_grouping),
            options = listOf(
                ChoiceOption(
                    TodoGrouping.GROUPED,
                    stringResource(R.string.settings_grouping_grouped),
                    Icons.Filled.Checklist,
                ),
                ChoiceOption(
                    TodoGrouping.FILE_ORDER,
                    stringResource(R.string.settings_grouping_file_order),
                    Icons.Filled.TaskAlt,
                ),
            ),
            selected = settings.todoGrouping,
            onSelect = { appSettingsRepository.setTodoGrouping(it) },
            onDismiss = { showTodoGroupingSheet = false },
        )
    }

    if (showTodoSortSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_todo_sort),
            options = listOf(
                ChoiceOption(
                    TodoSort.FILE_ORDER,
                    stringResource(R.string.settings_todo_sort_file_order),
                    Icons.Filled.TaskAlt,
                ),
                ChoiceOption(
                    TodoSort.PRIORITY_HIGH_TO_LOW,
                    stringResource(R.string.settings_todo_sort_priority_desc),
                    Icons.Filled.LowPriority,
                ),
                ChoiceOption(
                    TodoSort.CREATED_DATE_NEWEST_FIRST,
                    stringResource(R.string.settings_todo_sort_created_newest),
                    Icons.Filled.Schedule,
                ),
                ChoiceOption(
                    TodoSort.DUE_DATE_EARLIEST_FIRST,
                    stringResource(R.string.settings_todo_sort_due_earliest),
                    Icons.Filled.Schedule,
                ),
            ),
            selected = settings.todoSort,
            onSelect = { appSettingsRepository.setTodoSort(it) },
            onDismiss = { showTodoSortSheet = false },
        )
    }

    if (showNewTodoFilePositionSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_new_todo_file_position),
            options = listOf(
                ChoiceOption(
                    NewTodoFilePosition.TOP,
                    stringResource(R.string.settings_new_todo_file_position_top),
                    Icons.Filled.VerticalAlignTop,
                ),
                ChoiceOption(
                    NewTodoFilePosition.BOTTOM,
                    stringResource(R.string.settings_new_todo_file_position_bottom),
                    Icons.Filled.VerticalAlignTop,
                ),
            ),
            selected = settings.newTodoFilePosition,
            onSelect = { appSettingsRepository.setNewTodoFilePosition(it) },
            onDismiss = { showNewTodoFilePositionSheet = false },
        )
    }

    if (showDefaultAddTaskFileSheet) {
        val categories by produceState(initialValue = emptyList<Category>()) {
            value = fileRepository.getCategories().getOrDefault(emptyList())
        }
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_default_add_task_file),
            options = listOf(
                ChoiceOption<Uri?>(
                    value = null,
                    label = stringResource(R.string.settings_default_add_task_file_ask),
                    icon = Icons.Filled.Widgets,
                ),
            ) + categories.map {
                ChoiceOption(value = it.uri, label = it.displayName)
            },
            selected = defaultAddTaskFileUri,
            onSelect = { uri ->
                if (uri == null) {
                    preferencesManager.clearDefaultAddTaskFileUri()
                } else {
                    preferencesManager.saveDefaultAddTaskFileUri(uri)
                }
                defaultAddTaskFileUri = uri
            },
            onDismiss = { showDefaultAddTaskFileSheet = false },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    AppearanceSection(
                        settings = settings,
                        appSettingsRepository = appSettingsRepository,
                    )
                }

                item {
                    HomeSection(
                        settings = settings,
                        appSettingsRepository = appSettingsRepository,
                        launchCategoryName = launchCategoryName,
                        onOpenLaunchCategory = { showLaunchCategorySheet = true },
                        onOpenDailyFocusMode = { showDailyFocusModeSheet = true },
                    )
                }

                item {
                    CategoriesSection(
                        settings = settings,
                        appSettingsRepository = appSettingsRepository,
                        onOpenCategorySort = { showCategorySortSheet = true },
                    )
                }

                item {
                    TaskListSection(
                        settings = settings,
                        appSettingsRepository = appSettingsRepository,
                        onOpenTodoGrouping = { showTodoGroupingSheet = true },
                        onOpenTodoSort = { showTodoSortSheet = true },
                    )
                }

                item {
                    AddingTasksSection(
                        settings = settings,
                        appSettingsRepository = appSettingsRepository,
                        defaultAddTaskFileName = defaultAddTaskFileName,
                        onOpenNewTodoFilePosition = { showNewTodoFilePositionSheet = true },
                        onOpenDefaultAddTaskFile = { showDefaultAddTaskFileSheet = true },
                    )
                }

                item {
                    IntegrationsSection(
                        storageFolderName = storageState.folderDisplayName
                            ?: stringResource(R.string.settings_no_folder),
                        tasksEnabled = settings.enableTasksPluginSupport,
                        reminderTimeLabel = reminderTimeLabel,
                        onOpenStorage = onOpenStorage,
                        onOpenTasks = onOpenTasks,
                    )
                }

                item {
                    AboutSection(
                        preferencesManager = preferencesManager,
                        onOpenChangelog = { showChangelogSheet = true },
                    )
                }
            }
        }

        if (showChangelogSheet) {
            ChangelogBottomSheet(
                onDismissRequest = { showChangelogSheet = false },
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    settings: AppSettings,
    appSettingsRepository: AppSettingsRepository,
) {
    SettingsSection(
        title = stringResource(R.string.settings_appearance),
        icon = Icons.Filled.Palette,
        accent = SettingsAccent.PRIMARY,
    ) {
        val themeOptions = listOf(
            ChoiceOption(
                ThemeMode.SYSTEM,
                stringResource(R.string.settings_theme_system),
                Icons.Filled.BrightnessAuto,
            ),
            ChoiceOption(
                ThemeMode.LIGHT,
                stringResource(R.string.settings_theme_light),
                Icons.Filled.LightMode,
            ),
            ChoiceOption(
                ThemeMode.DARK,
                stringResource(R.string.settings_theme_dark),
                Icons.Filled.DarkMode,
            ),
        )
        SettingsSegmentedRow(
            icon = Icons.Filled.Tune,
            title = stringResource(R.string.settings_theme_mode),
            options = themeOptions,
            selected = settings.themeMode,
            onSelect = { appSettingsRepository.setThemeMode(it) },
        )

        SettingsDivider(startInset = 16.dp)

        val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        SettingsSwitchRow(
            icon = Icons.Filled.ViewDay,
            title = stringResource(R.string.settings_dynamic_color),
            subtitle = if (dynamicColorAvailable) {
                stringResource(R.string.settings_dynamic_color_subtitle)
            } else {
                stringResource(R.string.settings_dynamic_color_unavailable)
            },
            checked = settings.dynamicColorEnabled,
            enabled = dynamicColorAvailable,
            onCheckedChange = { appSettingsRepository.setDynamicColorEnabled(it) },
        )
    }
}

@Composable
private fun HomeSection(
    settings: AppSettings,
    appSettingsRepository: AppSettingsRepository,
    launchCategoryName: String?,
    onOpenLaunchCategory: () -> Unit,
    onOpenDailyFocusMode: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_home),
        icon = Icons.Filled.Home,
        accent = SettingsAccent.SECONDARY,
    ) {
        SettingsNavRow(
            icon = Icons.Filled.RocketLaunch,
            title = stringResource(R.string.settings_launch_category),
            subtitle = launchCategoryName
                ?: stringResource(R.string.settings_launch_category_overview),
            onClick = onOpenLaunchCategory,
        )

        SettingsDivider()

        SettingsSwitchRow(
            icon = Icons.Filled.WbSunny,
            title = stringResource(R.string.settings_show_daily_focus),
            subtitle = stringResource(R.string.settings_show_daily_focus_subtitle),
            checked = settings.showDailyFocus,
            onCheckedChange = { appSettingsRepository.setShowDailyFocus(it) },
        )

        AnimatedVisibility(
            visible = settings.showDailyFocus,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            Column {
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.settings_daily_focus_mode),
                    subtitle = dailyFocusModeLabel(settings.dailyFocusMode),
                    onClick = onOpenDailyFocusMode,
                )
            }
        }
    }
}

@Composable
private fun CategoriesSection(
    settings: AppSettings,
    appSettingsRepository: AppSettingsRepository,
    onOpenCategorySort: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_categories),
        icon = Icons.Filled.GridView,
        accent = SettingsAccent.TERTIARY,
    ) {
        val layoutOptions = listOf(
            ChoiceOption(
                CategoryLayout.GRID,
                stringResource(R.string.settings_category_layout_grid),
                Icons.Filled.GridView,
            ),
            ChoiceOption(
                CategoryLayout.LIST,
                stringResource(R.string.settings_category_layout_list),
                Icons.AutoMirrored.Filled.ViewList,
            ),
        )
        SettingsSegmentedRow(
            icon = Icons.Filled.GridView,
            title = stringResource(R.string.settings_category_layout),
            options = layoutOptions,
            selected = settings.categoryLayout,
            onSelect = { appSettingsRepository.setCategoryLayout(it) },
        )

        SettingsDivider(startInset = 16.dp)

        SettingsNavRow(
            icon = Icons.AutoMirrored.Filled.Sort,
            title = stringResource(R.string.settings_category_sort),
            subtitle = categorySortLabel(settings.categorySort),
            onClick = onOpenCategorySort,
        )
    }
}

@Composable
private fun TaskListSection(
    settings: AppSettings,
    appSettingsRepository: AppSettingsRepository,
    onOpenTodoGrouping: () -> Unit,
    onOpenTodoSort: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_task_list),
        icon = Icons.Filled.Checklist,
        accent = SettingsAccent.PRIMARY,
    ) {
        SettingsNavRow(
            icon = Icons.Filled.Checklist,
            title = stringResource(R.string.settings_todo_grouping),
            subtitle = todoGroupingLabel(settings.todoGrouping),
            onClick = onOpenTodoGrouping,
        )

        SettingsDivider()

        SettingsNavRow(
            icon = Icons.AutoMirrored.Filled.Sort,
            title = stringResource(R.string.settings_todo_sort),
            subtitle = todoSortLabel(settings.todoSort),
            onClick = onOpenTodoSort,
        )

        SettingsDivider()

        SettingsSwitchRow(
            icon = Icons.AutoMirrored.Filled.StickyNote2,
            title = stringResource(R.string.settings_show_notes_in_task_list),
            subtitle = stringResource(R.string.settings_show_notes_in_task_list_subtitle),
            checked = settings.showNotesInTaskList,
            onCheckedChange = { appSettingsRepository.setShowNotesInTaskList(it) },
        )

        SettingsDivider()

        SettingsSwitchRow(
            icon = Icons.Filled.DensitySmall,
            title = stringResource(R.string.settings_compact_todo_entries),
            subtitle = stringResource(R.string.settings_compact_todo_entries_subtitle),
            checked = settings.compactTodoEntries,
            onCheckedChange = { appSettingsRepository.setCompactTodoEntries(it) },
        )
    }
}

@Composable
private fun AddingTasksSection(
    settings: AppSettings,
    appSettingsRepository: AppSettingsRepository,
    defaultAddTaskFileName: String?,
    onOpenNewTodoFilePosition: () -> Unit,
    onOpenDefaultAddTaskFile: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_adding_tasks),
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        accent = SettingsAccent.SECONDARY,
    ) {
        SettingsNavRow(
            icon = Icons.Filled.VerticalAlignTop,
            title = stringResource(R.string.settings_new_todo_file_position),
            subtitle = newTodoFilePositionLabel(settings.newTodoFilePosition),
            onClick = onOpenNewTodoFilePosition,
        )

        SettingsDivider()

        SettingsNavRow(
            icon = Icons.Filled.Widgets,
            title = stringResource(R.string.settings_default_add_task_file),
            subtitle = defaultAddTaskFileName
                ?: stringResource(R.string.settings_default_add_task_file_ask),
            onClick = onOpenDefaultAddTaskFile,
        )

        SettingsDivider()

        SettingsSwitchRow(
            icon = Icons.Filled.AccountTree,
            title = stringResource(R.string.settings_enable_subtasks),
            subtitle = stringResource(R.string.settings_enable_subtasks_subtitle),
            checked = settings.enableSubtasks,
            onCheckedChange = { appSettingsRepository.setEnableSubtasks(it) },
        )
    }
}

@Composable
private fun IntegrationsSection(
    storageFolderName: String,
    tasksEnabled: Boolean,
    reminderTimeLabel: String,
    onOpenStorage: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val tasksSubtitle = if (tasksEnabled) {
        "${stringResource(R.string.settings_status_enabled)} · $reminderTimeLabel"
    } else {
        stringResource(R.string.settings_status_disabled)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionHeader(
            title = stringResource(R.string.settings_section_integrations),
            icon = Icons.Filled.Sync,
            accent = SettingsAccent.TERTIARY,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            SettingsShortcutCard(
                title = stringResource(R.string.settings_storage),
                subtitle = storageFolderName,
                icon = Icons.Filled.Folder,
                accent = SettingsAccent.PRIMARY,
                onClick = onOpenStorage,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            SettingsShortcutCard(
                title = stringResource(R.string.settings_section_tasks_plugin),
                subtitle = tasksSubtitle,
                icon = Icons.Filled.TaskAlt,
                accent = SettingsAccent.SECONDARY,
                onClick = onOpenTasks,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun AboutSection(
    preferencesManager: PreferencesManager,
    onOpenChangelog: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_about),
        icon = Icons.Filled.Info,
        accent = SettingsAccent.TERTIARY,
    ) {
        SettingsNavRow(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(R.string.settings_whats_new),
            subtitle = stringResource(R.string.settings_whats_new_subtitle),
            onClick = {
                preferencesManager.saveLastSeenVersionCode(BuildConfig.VERSION_CODE)
                onOpenChangelog()
            },
        )

        SettingsDivider()

        SettingsInfoRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.settings_version),
            subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )

        SettingsDivider()

        SettingsInfoRow(
            icon = Icons.Filled.Storage,
            title = stringResource(R.string.settings_android_sdk),
            subtitle = Build.VERSION.SDK_INT.toString(),
        )
    }
}
