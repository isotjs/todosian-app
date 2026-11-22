package com.isotjs.todosian.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.settings.AppSettings
import com.isotjs.todosian.data.settings.AppSettingsRepository
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsStorageScreen(
    fileRepository: FileRepository,
    appSettingsRepository: AppSettingsRepository,
    onBack: () -> Unit,
    onRequireOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(fileRepository, appSettingsRepository),
    )
    val settings by appSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val storageState by viewModel.storageState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showResetDialog by remember { mutableStateOf(false) }

    val resources = LocalResources.current
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_storage)) },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    StorageHeroCard(state = storageState)
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { folderLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DriveFileRenameOutline,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = stringResource(R.string.settings_change_folder))
                        }
                        OutlinedButton(
                            onClick = { viewModel.refreshStorageStatus() },
                            enabled = !storageState.isChecking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (storageState.isChecking) {
                                LoadingIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                )
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = stringResource(R.string.settings_recheck_access))
                        }
                    }
                }

                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_folder_access),
                        icon = Icons.Filled.SubdirectoryArrowRight,
                        accent = SettingsAccent.SECONDARY,
                    ) {
                        SettingsSwitchRow(
                            icon = Icons.Filled.Folder,
                            title = stringResource(R.string.settings_include_subfolders),
                            subtitle = stringResource(R.string.settings_include_subfolders_subtitle),
                            checked = settings.includeSubfolders,
                            onCheckedChange = {
                                appSettingsRepository.setIncludeSubfolders(it)
                                viewModel.refreshStorageStatus()
                            },
                        )
                    }
                }

                item {
                    SettingsDangerZone(
                        enabled = storageState.folderUri != null,
                        onReset = { showResetDialog = true },
                    )
                }
            }
        }
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StorageHeroCard(state: StorageUiState) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialShapes.Clover4Leaf.toShape())
                    .background(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_folder),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = state.folderDisplayName
                    ?: state.folderUri?.toString()
                    ?: stringResource(R.string.settings_no_folder),
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 2,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.isChecking -> LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    state.folderUri == null -> Icon(
                        imageVector = Icons.Filled.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    !state.hasPersistedPermission -> Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    else -> Icon(
                        imageVector = Icons.Filled.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = storageStatusText(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun SettingsDangerZone(
    enabled: Boolean,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionHeader(
            title = stringResource(R.string.settings_danger_zone),
            icon = Icons.Filled.RestartAlt,
            accent = SettingsAccent.PRIMARY,
        )
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.settings_reset_folder),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_reset_folder_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onReset,
                    enabled = enabled,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = stringResource(R.string.settings_reset_folder_confirm))
                }
            }
        }
    }
}
