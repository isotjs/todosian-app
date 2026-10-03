package com.isotjs.todosian.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isotjs.todosian.BuildConfig
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.PreferencesManager
import com.isotjs.todosian.data.settings.AppSettings
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.data.settings.CategorySort
import com.isotjs.todosian.data.settings.DailyFocusMode
import com.isotjs.todosian.data.settings.NewTodoFilePosition
import com.isotjs.todosian.data.settings.ThemeMode
import com.isotjs.todosian.data.settings.TodoGrouping
import com.isotjs.todosian.data.settings.TodoSort
import com.isotjs.todosian.ui.components.ChangelogBottomSheet
import com.isotjs.todosian.ui.components.TodosianSectionHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
    onRequireOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(fileRepository),
    )

    var showChangelogSheet by remember { mutableStateOf(false) }

    val settings by appSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val storageState by viewModel.storageState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.settings_notification_permission_denied),
                )
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsViewModel.Event.ShowMessage ->
                    snackbarHostState.showSnackbar(resources.getString(event.messageResId))
                SettingsViewModel.Event.RequireOnboarding -> onRequireOnboarding()
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.changeFolder(uri)
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showGroupingDialog by remember { mutableStateOf(false) }
    var showTodoSortDialog by remember { mutableStateOf(false) }
    var showNewTodoFilePositionDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showDailyFocusModeDialog by remember { mutableStateOf(false) }
    var showReminderTimeDialog by remember { mutableStateOf(false) }

    val reminderTimeLabel = remember(settings.reminderTimeHour, settings.reminderTimeMinute) {
        LocalTime.of(settings.reminderTimeHour, settings.reminderTimeMinute)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme_mode),
            options = listOf(
                ChoiceOption(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system)),
                ChoiceOption(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light)),
                ChoiceOption(ThemeMode.DARK, stringResource(R.string.settings_theme_dark)),
            ),
            selected = settings.themeMode,
            onSelected = { appSettingsRepository.setThemeMode(it) },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showSortDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_category_sort),
            options = listOf(
                ChoiceOption(CategorySort.A_Z, stringResource(R.string.settings_sort_az)),
                ChoiceOption(CategorySort.MOST_REMAINING, stringResource(R.string.settings_sort_remaining)),
            ),
            selected = settings.categorySort,
            onSelected = { appSettingsRepository.setCategorySort(it) },
            onDismiss = { showSortDialog = false },
        )
    }

    if (showGroupingDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_todo_grouping),
            options = listOf(
                ChoiceOption(TodoGrouping.GROUPED, stringResource(R.string.settings_grouping_grouped)),
                ChoiceOption(TodoGrouping.FILE_ORDER, stringResource(R.string.settings_grouping_file_order)),
            ),
            selected = settings.todoGrouping,
            onSelected = { appSettingsRepository.setTodoGrouping(it) },
            onDismiss = { showGroupingDialog = false },
        )
    }

    if (showTodoSortDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_todo_sort),
            options = listOf(
                ChoiceOption(TodoSort.FILE_ORDER, stringResource(R.string.settings_todo_sort_file_order)),
                ChoiceOption(TodoSort.PRIORITY_HIGH_TO_LOW, stringResource(R.string.settings_todo_sort_priority_desc)),
                ChoiceOption(TodoSort.CREATED_DATE_NEWEST_FIRST, stringResource(R.string.settings_todo_sort_created_newest)),
                ChoiceOption(TodoSort.DUE_DATE_EARLIEST_FIRST, stringResource(R.string.settings_todo_sort_due_earliest)),
            ),
            selected = settings.todoSort,
            onSelected = { appSettingsRepository.setTodoSort(it) },
            onDismiss = { showTodoSortDialog = false },
        )
    }

    if (showNewTodoFilePositionDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_new_todo_file_position),
            options = listOf(
                ChoiceOption(NewTodoFilePosition.TOP, stringResource(R.string.settings_new_todo_file_position_top)),
                ChoiceOption(NewTodoFilePosition.BOTTOM, stringResource(R.string.settings_new_todo_file_position_bottom)),
            ),
            selected = settings.newTodoFilePosition,
            onSelected = { appSettingsRepository.setNewTodoFilePosition(it) },
            onDismiss = { showNewTodoFilePositionDialog = false },
        )
    }

    if (showDailyFocusModeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_daily_focus_mode),
            options = listOf(
                ChoiceOption(DailyFocusMode.TODAY, stringResource(R.string.settings_daily_focus_mode_today)),
                ChoiceOption(DailyFocusMode.OVERDUE, stringResource(R.string.settings_daily_focus_mode_overdue)),
                ChoiceOption(DailyFocusMode.TODAY_AND_OVERDUE, stringResource(R.string.settings_daily_focus_mode_today_overdue)),
            ),
            selected = settings.dailyFocusMode,
            onSelected = { appSettingsRepository.setDailyFocusMode(it) },
            onDismiss = { showDailyFocusModeDialog = false },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = stringResource(R.string.settings_reset_folder_title)) },
            text = { Text(text = stringResource(R.string.settings_reset_folder_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetFolder()
                    },
                ) {
                    Text(text = stringResource(R.string.settings_reset_folder_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showReminderTimeDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = settings.reminderTimeHour,
            initialMinute = settings.reminderTimeMinute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showReminderTimeDialog = false },
            title = { Text(text = stringResource(R.string.settings_reminder_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        appSettingsRepository.setReminderTime(
                            timePickerState.hour,
                            timePickerState.minute,
                        )
                        showReminderTimeDialog = false
                    },
                ) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReminderTimeDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    SettingsGroup(title = stringResource(R.string.settings_storage)) {
                        val folderText = storageState.folderDisplayName
                            ?: storageState.folderUri?.toString()
                            ?: stringResource(R.string.settings_no_folder)

                        SettingsInfoRow(
                            icon = Icons.Filled.Folder,
                            title = stringResource(R.string.settings_folder),
                            subtitle = folderText,
                            subtitleMaxLines = 2,
                        )

                        SettingsDivider()

                        SettingsNavRow(
                            icon = Icons.Filled.RestartAlt,
                            title = stringResource(R.string.settings_change_folder),
                            subtitle = stringResource(R.string.settings_change_folder_subtitle),
                            onClick = { folderLauncher.launch(null) },
                        )

                        SettingsDivider()

                        val mdCount = storageState.markdownFileCount
                        val statusText = when {
                            storageState.folderUri == null -> stringResource(R.string.settings_status_not_set)
                            storageState.isChecking -> stringResource(R.string.settings_status_checking)
                            !storageState.hasPersistedPermission -> stringResource(R.string.settings_status_permission_lost)
                            mdCount == null -> stringResource(R.string.settings_status_unknown)
                            mdCount == 0 -> stringResource(R.string.settings_status_empty)
                            else -> pluralStringResource(R.plurals.settings_status_ok, mdCount, mdCount)
                        }

                        SettingsInfoRow(
                            icon = Icons.Filled.Refresh,
                            title = stringResource(R.string.settings_status),
                            subtitle = statusText,
                            trailing = if (storageState.isChecking) {
                                {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )

                        SettingsDivider()

                        SettingsNavRow(
                            icon = Icons.Filled.Refresh,
                            title = stringResource(R.string.settings_recheck_access),
                            subtitle = stringResource(R.string.settings_recheck_access_subtitle),
                            showChevron = false,
                            onClick = { viewModel.refreshStorageStatus() },
                        )

                        SettingsDivider()

                        SettingsNavRow(
                            icon = Icons.Filled.RestartAlt,
                            title = stringResource(R.string.settings_reset_folder),
                            subtitle = stringResource(R.string.settings_reset_folder_body),
                            contentColor = MaterialTheme.colorScheme.error,
                            enabled = storageState.folderUri != null,
                            showChevron = false,
                            onClick = { showResetDialog = true },
                        )
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.settings_appearance)) {
                        SettingsNavRow(
                            icon = Icons.Filled.ColorLens,
                            title = stringResource(R.string.settings_theme_mode),
                            subtitle = themeModeLabel(settings.themeMode),
                            onClick = { showThemeDialog = true },
                        )

                        SettingsDivider()

                        val dynamicEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        SettingsSwitchRow(
                            icon = Icons.Filled.ViewDay,
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = if (dynamicEnabled) {
                                stringResource(R.string.settings_dynamic_color_subtitle)
                            } else {
                                stringResource(R.string.settings_dynamic_color_unavailable)
                            },
                            checked = settings.dynamicColorEnabled,
                            enabled = dynamicEnabled,
                            onCheckedChange = { appSettingsRepository.setDynamicColorEnabled(it) },
                        )
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.settings_behaviour)) {
                        SettingsSwitchRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_show_daily_focus),
                            subtitle = stringResource(R.string.settings_show_daily_focus_subtitle),
                            checked = settings.showDailyFocus,
                            onCheckedChange = { appSettingsRepository.setShowDailyFocus(it) },
                        )

                        AnimatedVisibility(
                            visible = settings.showDailyFocus,
                            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
                        ) {
                            Column {
                                SettingsDivider()
                                SettingsNavRow(
                                    icon = Icons.Filled.Info,
                                    title = stringResource(R.string.settings_daily_focus_mode),
                                    subtitle = dailyFocusModeLabel(settings.dailyFocusMode),
                                    onClick = { showDailyFocusModeDialog = true },
                                )
                            }
                        }

                        SettingsDivider()

                        SettingsSwitchRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_enable_tasks_plugin_support),
                            subtitle = stringResource(R.string.settings_enable_tasks_plugin_support_subtitle),
                            checked = settings.enableTasksPluginSupport,
                            onCheckedChange = { enabled ->
                                appSettingsRepository.setEnableTasksPluginSupport(enabled)

                                if (!enabled) return@SettingsSwitchRow
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@SettingsSwitchRow

                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                        )

                        AnimatedVisibility(
                            visible = settings.enableTasksPluginSupport,
                            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
                        ) {
                            Column {
                                SettingsDivider()
                                SettingsSwitchRow(
                                    icon = Icons.Filled.Info,
                                    title = stringResource(R.string.settings_tasks_plugin_ui_emojis),
                                    subtitle = stringResource(R.string.settings_tasks_plugin_ui_emojis_subtitle),
                                    checked = settings.tasksPluginUseEmojisInUi,
                                    onCheckedChange = { appSettingsRepository.setTasksPluginUseEmojisInUi(it) },
                                )

                                SettingsDivider()

                                SettingsNavRow(
                                    icon = Icons.Filled.Schedule,
                                    title = stringResource(R.string.settings_reminder_time),
                                    subtitle = stringResource(R.string.settings_reminder_time_subtitle, reminderTimeLabel),
                                    onClick = { showReminderTimeDialog = true },
                                )
                            }
                        }

                        SettingsDivider()

                        SettingsNavRow(
                            icon = Icons.AutoMirrored.Filled.Sort,
                            title = stringResource(R.string.settings_category_sort),
                            subtitle = categorySortLabel(settings.categorySort),
                            onClick = { showSortDialog = true },
                        )

                        SettingsDivider()

                        SettingsNavRow(
                            icon = Icons.AutoMirrored.Filled.Sort,
                            title = stringResource(R.string.settings_todo_grouping),
                            subtitle = todoGroupingLabel(settings.todoGrouping),
                            onClick = { showGroupingDialog = true },
                        )

                        SettingsDivider()

                        SettingsNavRow(
                            icon = Icons.AutoMirrored.Filled.Sort,
                            title = stringResource(R.string.settings_todo_sort),
                            subtitle = todoSortLabel(settings.todoSort),
                            onClick = { showTodoSortDialog = true },
                        )

                        SettingsDivider()

                        SettingsNavRow(
                            icon = Icons.Filled.VerticalAlignTop,
                            title = stringResource(R.string.settings_new_todo_file_position),
                            subtitle = newTodoFilePositionLabel(settings.newTodoFilePosition),
                            onClick = { showNewTodoFilePositionDialog = true },
                        )
                    }
                }

                item {
                    SettingsGroup(title = null) {
                        SettingsSwitchRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_enable_subtasks),
                            subtitle = stringResource(R.string.settings_enable_subtasks_subtitle),
                            checked = settings.enableSubtasks,
                            onCheckedChange = { appSettingsRepository.setEnableSubtasks(it) },
                        )
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.settings_about)) {
                        SettingsNavRow(
                            icon = Icons.Filled.AutoAwesome,
                            title = stringResource(R.string.settings_whats_new),
                            subtitle = stringResource(R.string.settings_whats_new_subtitle),
                            onClick = {
                                showChangelogSheet = true
                                preferencesManager.saveLastSeenVersionCode(BuildConfig.VERSION_CODE)
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
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_android_sdk),
                            subtitle = Build.VERSION.SDK_INT.toString(),
                        )
                    }
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
private fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            TodosianSectionHeader(text = title)
        }
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = if (subtitle != null) {
            { Text(text = subtitle) }
        } else {
            null
        },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        colors = transparentListItemColors(),
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    contentColor: Color? = null,
    showChevron: Boolean = true,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = contentColor ?: Color.Unspecified,
            )
        },
        supportingContent = if (subtitle != null) {
            { Text(text = subtitle) }
        } else {
            null
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = if (showChevron) {
            {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        colors = transparentListItemColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = if (subtitle != null) {
            {
                Text(
                    text = subtitle,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        trailingContent = trailing,
        colors = transparentListItemColors(),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun transparentListItemColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = Color.Transparent)

private data class ChoiceOption<T>(
    val value: T,
    val label: String,
)

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                options.forEach { option ->
                    val isSelected = option.value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelected(option.value)
                                    onDismiss()
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String {
    return when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }
}

@Composable
private fun categorySortLabel(sort: CategorySort): String {
    return when (sort) {
        CategorySort.A_Z -> stringResource(R.string.settings_sort_az)
        CategorySort.MOST_REMAINING -> stringResource(R.string.settings_sort_remaining)
    }
}

@Composable
private fun todoGroupingLabel(grouping: TodoGrouping): String {
    return when (grouping) {
        TodoGrouping.GROUPED -> stringResource(R.string.settings_grouping_grouped)
        TodoGrouping.FILE_ORDER -> stringResource(R.string.settings_grouping_file_order)
    }
}

@Composable
private fun todoSortLabel(sort: TodoSort): String {
    return when (sort) {
        TodoSort.FILE_ORDER -> stringResource(R.string.settings_todo_sort_file_order)
        TodoSort.PRIORITY_HIGH_TO_LOW -> stringResource(R.string.settings_todo_sort_priority_desc)
        TodoSort.CREATED_DATE_NEWEST_FIRST -> stringResource(R.string.settings_todo_sort_created_newest)
        TodoSort.DUE_DATE_EARLIEST_FIRST -> stringResource(R.string.settings_todo_sort_due_earliest)
    }
}

@Composable
private fun newTodoFilePositionLabel(position: NewTodoFilePosition): String {
    return when (position) {
        NewTodoFilePosition.TOP -> stringResource(R.string.settings_new_todo_file_position_top)
        NewTodoFilePosition.BOTTOM -> stringResource(R.string.settings_new_todo_file_position_bottom)
    }
}

@Composable
private fun dailyFocusModeLabel(mode: DailyFocusMode): String {
    return when (mode) {
        DailyFocusMode.TODAY -> stringResource(R.string.settings_daily_focus_mode_today)
        DailyFocusMode.OVERDUE -> stringResource(R.string.settings_daily_focus_mode_overdue)
        DailyFocusMode.TODAY_AND_OVERDUE -> stringResource(R.string.settings_daily_focus_mode_today_overdue)
    }
}

private class SettingsViewModelFactory(
    private val fileRepository: FileRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(fileRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
