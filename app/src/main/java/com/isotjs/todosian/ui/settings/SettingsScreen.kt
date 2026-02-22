package com.isotjs.todosian.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isotjs.todosian.BuildConfig
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.settings.AppSettings
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.data.settings.CategorySort
import com.isotjs.todosian.data.settings.ThemeMode
import com.isotjs.todosian.data.settings.TodoGrouping
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    fileRepository: FileRepository,
    appSettingsRepository: AppSettingsRepository,
    onBack: () -> Unit,
    onRequireOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(fileRepository),
    )

    val settings by appSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val storageState by viewModel.storageState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsViewModel.Event.ShowMessage ->
                    snackbarHostState.showSnackbar(context.getString(event.messageResId))
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
    var showResetDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle(text = stringResource(R.string.settings_storage))
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    val folderText = storageState.folderDisplayName
                        ?: storageState.folderUri?.toString()
                        ?: stringResource(R.string.settings_no_folder)

                    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_folder)) },
                        supportingContent = {
                            Text(
                                text = folderText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = { Icon(imageVector = Icons.Filled.Folder, contentDescription = null) },
                        colors = itemColors,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_change_folder)) },
                        supportingContent = {
                            Text(text = stringResource(R.string.settings_change_folder_subtitle))
                        },
                        leadingContent = { Icon(imageVector = Icons.Filled.RestartAlt, contentDescription = null) },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { folderLauncher.launch(null) }
                            .padding(horizontal = 4.dp),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    val mdCount = storageState.markdownFileCount
                    val statusText = when {
                        storageState.folderUri == null -> stringResource(R.string.settings_status_not_set)
                        storageState.isChecking -> stringResource(R.string.settings_status_checking)
                        !storageState.hasPersistedPermission -> stringResource(R.string.settings_status_permission_lost)
                        mdCount == null -> stringResource(R.string.settings_status_unknown)
                        mdCount == 0 -> stringResource(R.string.settings_status_empty)
                        else -> stringResource(
                            R.string.settings_status_ok,
                            mdCount,
                        )
                    }

                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_status)) },
                        supportingContent = { Text(text = statusText) },
                        leadingContent = { Icon(imageVector = Icons.Filled.Refresh, contentDescription = null) },
                        trailingContent = {
                            if (storageState.isChecking) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        colors = itemColors,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_recheck_access)) },
                        supportingContent = { Text(text = stringResource(R.string.settings_status)) },
                        leadingContent = { Icon(imageVector = Icons.Filled.Refresh, contentDescription = null) },
                        colors = itemColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.refreshStorageStatus() }
                            .padding(horizontal = 4.dp),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.settings_reset_folder),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        supportingContent = { Text(text = stringResource(R.string.settings_reset_folder_body)) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = storageState.folderUri != null) { showResetDialog = true }
                            .padding(horizontal = 4.dp),
                    )
                }
            }

            item {
                SectionTitle(text = stringResource(R.string.settings_appearance))
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_theme_mode)) },
                        supportingContent = {
                            Text(text = themeModeLabel(settings.themeMode))
                        },
                        leadingContent = { Icon(imageVector = Icons.Filled.ColorLens, contentDescription = null) },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemeDialog = true }
                            .padding(horizontal = 4.dp),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    val dynamicEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_dynamic_color)) },
                        supportingContent = {
                            Text(
                                text = if (dynamicEnabled) {
                                    stringResource(R.string.settings_dynamic_color_subtitle)
                                } else {
                                    stringResource(R.string.settings_dynamic_color_unavailable)
                                },
                            )
                        },
                        leadingContent = { Icon(imageVector = Icons.Filled.ViewDay, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = settings.dynamicColorEnabled,
                                onCheckedChange = { appSettingsRepository.setDynamicColorEnabled(it) },
                                enabled = dynamicEnabled,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item {
                SectionTitle(text = stringResource(R.string.settings_behaviour))
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_show_daily_focus)) },
                        supportingContent = { Text(text = stringResource(R.string.settings_show_daily_focus_subtitle)) },
                        leadingContent = { Icon(imageVector = Icons.Filled.Info, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = settings.showDailyFocus,
                                onCheckedChange = { appSettingsRepository.setShowDailyFocus(it) },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_enable_tasks_plugin_support)) },
                        supportingContent = { Text(text = stringResource(R.string.settings_enable_tasks_plugin_support_subtitle)) },
                        leadingContent = { Icon(imageVector = Icons.Filled.Info, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = settings.enableTasksPluginSupport,
                                onCheckedChange = { appSettingsRepository.setEnableTasksPluginSupport(it) },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    if (settings.enableTasksPluginSupport) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.settings_tasks_plugin_ui_emojis)) },
                            supportingContent = { Text(text = stringResource(R.string.settings_tasks_plugin_ui_emojis_subtitle)) },
                            leadingContent = { Icon(imageVector = Icons.Filled.Info, contentDescription = null) },
                            trailingContent = {
                                Switch(
                                    checked = settings.tasksPluginUseEmojisInUi,
                                    onCheckedChange = { appSettingsRepository.setTasksPluginUseEmojisInUi(it) },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_category_sort)) },
                        supportingContent = {
                            Text(text = categorySortLabel(settings.categorySort))
                        },
                        leadingContent = { Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSortDialog = true }
                            .padding(horizontal = 4.dp),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_todo_grouping)) },
                        supportingContent = {
                            Text(text = todoGroupingLabel(settings.todoGrouping))
                        },
                        leadingContent = { Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGroupingDialog = true }
                            .padding(horizontal = 4.dp),
                    )
                }
            }

            item {
                SectionTitle(text = stringResource(R.string.settings_about))
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_version)) },
                        supportingContent = {
                            Text(text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        },
                        leadingContent = { Icon(imageVector = Icons.Filled.Info, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.settings_android_sdk)) },
                        supportingContent = { Text(text = Build.VERSION.SDK_INT.toString()) },
                        leadingContent = { Icon(imageVector = Icons.Filled.Info, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp),
    )
}

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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    val isSelected = option.value == selected
                    val bg = if (isSelected) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg, shape = MaterialTheme.shapes.medium)
                            .clickable {
                                onSelected(option.value)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelected(option.value)
                                onDismiss()
                            },
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .weight(1f),
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
