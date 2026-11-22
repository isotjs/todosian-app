package com.isotjs.todosian.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isotjs.todosian.R
import com.isotjs.todosian.data.settings.AppSettings
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.utils.TaskMarkers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsTasksScreen(
    appSettingsRepository: AppSettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by appSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showMarkersSheet by remember { mutableStateOf(false) }
    var showReminderTimeDialog by remember { mutableStateOf(false) }

    val reminderTimeLabel = remember(settings.reminderTimeHour, settings.reminderTimeMinute) {
        LocalTime.of(settings.reminderTimeHour, settings.reminderTimeMinute)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

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

    fun setTasksPluginEnabled(enabled: Boolean) {
        appSettingsRepository.setEnableTasksPluginSupport(enabled)
        if (!enabled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_section_tasks_plugin)) },
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TasksMasterSwitchCard(
                    enabled = settings.enableTasksPluginSupport,
                    onEnabledChange = ::setTasksPluginEnabled,
                )

                AnimatedVisibility(
                    visible = settings.enableTasksPluginSupport,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SettingsSection(
                            title = stringResource(R.string.settings_section_metadata),
                            icon = Icons.Filled.EditNote,
                            accent = SettingsAccent.SECONDARY,
                        ) {
                            SettingsSwitchRow(
                                icon = Icons.Filled.TaskAlt,
                                title = stringResource(R.string.settings_tasks_plugin_ui_emojis),
                                subtitle = stringResource(R.string.settings_tasks_plugin_ui_emojis_subtitle),
                                checked = settings.tasksPluginUseEmojisInUi,
                                onCheckedChange = {
                                    appSettingsRepository.setTasksPluginUseEmojisInUi(it)
                                },
                            )

                            SettingsDivider()

                            SettingsNavRow(
                                icon = Icons.Filled.EditNote,
                                title = stringResource(R.string.settings_markers_title),
                                subtitle = stringResource(R.string.settings_markers_subtitle),
                                onClick = { showMarkersSheet = true },
                            )
                        }

                        SettingsSection(
                            title = stringResource(R.string.settings_section_reminders),
                            icon = Icons.Filled.NotificationsActive,
                            accent = SettingsAccent.TERTIARY,
                        ) {
                            SettingsNavRow(
                                icon = Icons.Filled.Schedule,
                                title = stringResource(R.string.settings_reminder_time),
                                subtitle = stringResource(
                                    R.string.settings_reminder_time_subtitle,
                                    reminderTimeLabel,
                                ),
                                onClick = { showReminderTimeDialog = true },
                            )
                        }
                    }
                }

                if (!settings.enableTasksPluginSupport) {
                    TasksDisabledHint()
                }
            }
        }
    }

    if (showMarkersSheet) {
        TaskMarkersSheet(
            settings = settings,
            appSettingsRepository = appSettingsRepository,
            onDismiss = { showMarkersSheet = false },
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TasksMasterSwitchCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tasksMasterContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tasksMasterContent",
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = onEnabledChange,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.TaskAlt,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_enable_tasks_plugin_support),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_enable_tasks_plugin_support_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TasksDisabledHint() {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.settings_tasks_disabled_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TaskMarkersSheet(
    settings: AppSettings,
    appSettingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .windowInsetsPadding(WindowInsets.ime)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_markers_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.settings_markers_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsTextFieldRow(
                label = stringResource(R.string.settings_marker_created),
                value = settings.createdMarker.orEmpty(),
                placeholder = stringResource(R.string.settings_marker_example_created),
                supporting = stringResource(R.string.settings_marker_default_hint, TaskMarkers.Default.created),
                onValueChange = { appSettingsRepository.setCreatedMarker(it) },
            )
            SettingsTextFieldRow(
                label = stringResource(R.string.settings_marker_start),
                value = settings.startMarker.orEmpty(),
                placeholder = stringResource(R.string.settings_marker_example_start),
                supporting = stringResource(R.string.settings_marker_default_hint, TaskMarkers.Default.start),
                onValueChange = { appSettingsRepository.setStartMarker(it) },
            )
            SettingsTextFieldRow(
                label = stringResource(R.string.settings_marker_scheduled),
                value = settings.scheduledMarker.orEmpty(),
                placeholder = stringResource(R.string.settings_marker_example_scheduled),
                supporting = stringResource(R.string.settings_marker_default_hint, TaskMarkers.Default.scheduled),
                onValueChange = { appSettingsRepository.setScheduledMarker(it) },
            )
            SettingsTextFieldRow(
                label = stringResource(R.string.settings_marker_due),
                value = settings.dueMarker.orEmpty(),
                placeholder = stringResource(R.string.settings_marker_example_due),
                supporting = stringResource(R.string.settings_marker_default_hint, TaskMarkers.Default.due),
                onValueChange = { appSettingsRepository.setDueMarker(it) },
            )
            SettingsTextFieldRow(
                label = stringResource(R.string.settings_marker_done),
                value = settings.doneMarker.orEmpty(),
                placeholder = stringResource(R.string.settings_marker_example_done),
                supporting = stringResource(R.string.settings_marker_default_hint, TaskMarkers.Default.done),
                onValueChange = { appSettingsRepository.setDoneMarker(it) },
            )
            SettingsTextFieldRow(
                label = stringResource(R.string.settings_marker_recurrence),
                value = settings.recurrenceMarker.orEmpty(),
                placeholder = stringResource(R.string.settings_marker_example_recurrence),
                supporting = stringResource(R.string.settings_marker_default_hint, TaskMarkers.Default.recurrence),
                onValueChange = { appSettingsRepository.setRecurrenceMarker(it) },
            )
            SettingsTextFieldRow(
                label = stringResource(R.string.settings_custom_statuses),
                value = settings.customStatuses.orEmpty(),
                placeholder = stringResource(R.string.settings_custom_statuses_example),
                supporting = stringResource(R.string.settings_custom_statuses_hint),
                onValueChange = { appSettingsRepository.setCustomStatuses(it) },
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 24.dp),
            ) {
                Text(text = stringResource(R.string.action_ok))
            }
        }
    }
}

@Composable
private fun SettingsTextFieldRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(text = label) },
        placeholder = { Text(text = placeholder) },
        supportingText = if (supporting != null) {
            { Text(text = supporting) }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
