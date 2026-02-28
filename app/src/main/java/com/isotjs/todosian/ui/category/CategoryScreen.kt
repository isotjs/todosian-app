package com.isotjs.todosian.ui.category

import android.app.DatePickerDialog
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.data.settings.TodoGrouping
import com.isotjs.todosian.data.model.TasksPriority
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.ui.components.TodosianDimens
import com.isotjs.todosian.ui.components.TodosianSectionHeader
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(
    fileRepository: FileRepository,
    appSettingsRepository: AppSettingsRepository,
    categoryUri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CategoryViewModel = viewModel(
        factory = CategoryViewModelFactory(
            fileRepository = fileRepository,
            categoryUri = categoryUri,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val settings by appSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = com.isotjs.todosian.data.settings.AppSettings(),
    )

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is CategoryViewModel.Event.ShowMessage -> {
                    snackbarHostState.showSnackbar(context.getString(event.messageResId))
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val todoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sheetMode by remember { mutableStateOf<TodoSheetMode?>(null) }
    var sheetText by remember { mutableStateOf("") }
    var sheetMeta by remember { mutableStateOf(MarkdownParser.TasksMeta()) }
    var deleteTodoTarget by remember { mutableStateOf<Todo?>(null) }

    if (sheetMode != null) {
        ModalBottomSheet(
            onDismissRequest = {
                sheetMode = null
                sheetText = ""
                sheetMeta = MarkdownParser.TasksMeta()
            },
            sheetState = todoSheetState,
        ) {
            val titleRes = when (sheetMode) {
                TodoSheetMode.Add -> R.string.category_add_todo_title
                is TodoSheetMode.Edit -> R.string.category_edit_todo_title
                null -> R.string.category_add_todo_title
            }

            val hintRes = when (sheetMode) {
                TodoSheetMode.Add -> R.string.category_add_todo_hint
                is TodoSheetMode.Edit -> R.string.category_edit_todo_hint
                null -> R.string.category_add_todo_hint
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.ime)
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = sheetText,
                    onValueChange = { sheetText = it },
                    singleLine = true,
                    label = { Text(text = stringResource(hintRes)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (settings.enableTasksPluginSupport) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TasksMetaEditor(
                        mode = sheetMode,
                        meta = sheetMeta,
                        onMetaChange = { sheetMeta = it },
                        useEmojisInUi = settings.tasksPluginUseEmojisInUi,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                todoSheetState.hide()
                                sheetMode = null
                                sheetText = ""
                                sheetMeta = MarkdownParser.TasksMeta()
                            }
                        },
                    ) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            when (val mode = sheetMode) {
                                TodoSheetMode.Add -> viewModel.addTodo(
                                    text = sheetText,
                                    meta = if (settings.enableTasksPluginSupport) sheetMeta else null,
                                    enableTasksPluginSupport = settings.enableTasksPluginSupport,
                                )

                                is TodoSheetMode.Edit -> viewModel.editTodo(
                                    todo = mode.todo,
                                    newText = sheetText,
                                    meta = if (settings.enableTasksPluginSupport) sheetMeta else null,
                                    enableTasksPluginSupport = settings.enableTasksPluginSupport,
                                )

                                null -> Unit
                            }
                            scope.launch {
                                todoSheetState.hide()
                                sheetMode = null
                                sheetText = ""
                                sheetMeta = MarkdownParser.TasksMeta()
                            }
                        },
                        enabled = sheetText.trim().isNotEmpty(),
                    ) {
                        Text(text = stringResource(R.string.action_save))
                    }
                }
            }
        }
    }

    if (deleteTodoTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTodoTarget = null },
            title = { Text(text = stringResource(R.string.category_delete_todo_title)) },
            text = { Text(text = stringResource(R.string.category_delete_todo_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteTodoTarget
                        if (target != null) {
                            viewModel.deleteTodo(target)
                        }
                        deleteTodoTarget = null
                    },
                ) {
                    Text(text = stringResource(R.string.category_delete_todo_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTodoTarget = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    sheetMode = TodoSheetMode.Add
                    sheetText = ""
                    sheetMeta = if (settings.enableTasksPluginSupport) {
                        MarkdownParser.TasksMeta(createdDate = LocalDate.now().toString())
                    } else {
                        MarkdownParser.TasksMeta()
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add_todo),
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val anyTodos = uiState.activeTodos.isNotEmpty() || uiState.completedTodos.isNotEmpty()
        if (!anyTodos) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.category_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        val fileOrderTodos = remember(uiState.activeTodos, uiState.completedTodos) {
            (uiState.activeTodos + uiState.completedTodos).sortedBy { it.lineIndex }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = TodosianDimens.ScreenHorizontalPadding),
        ) {
            if (settings.todoGrouping == TodoGrouping.FILE_ORDER) {
                items(
                    items = fileOrderTodos,
                    key = { it.id },
                ) { todo ->
                    TodoRow(
                        todo = todo,
                        enableTasksPluginSupport = settings.enableTasksPluginSupport,
                        useEmojisInUi = settings.tasksPluginUseEmojisInUi,
                        onToggle = { viewModel.toggleTodo(todo, settings.enableTasksPluginSupport) },
                        onEdit = {
                            sheetMode = TodoSheetMode.Edit(todo)
                            sheetText = todo.text
                            sheetMeta = MarkdownParser.TasksMeta(
                                dueDate = todo.dueDate,
                                startDate = todo.startDate,
                                scheduledDate = todo.scheduledDate,
                                completionDate = todo.completionDate,
                                createdDate = todo.createdDate,
                                priority = todo.priority,
                                recurrence = todo.recurrence,
                            )
                        },
                        onRequestDelete = { deleteTodoTarget = todo },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 180),
                            placementSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            fadeOutSpec = tween(durationMillis = 160),
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item { Spacer(modifier = Modifier.height(96.dp)) }
                return@LazyColumn
            }

            if (uiState.activeTodos.isNotEmpty()) {
                item { TodosianSectionHeader(text = stringResource(R.string.category_active)) }
                items(
                    items = uiState.activeTodos,
                    key = { it.id },
                ) { todo ->
                    TodoRow(
                        todo = todo,
                        enableTasksPluginSupport = settings.enableTasksPluginSupport,
                        useEmojisInUi = settings.tasksPluginUseEmojisInUi,
                        onToggle = { viewModel.toggleTodo(todo, settings.enableTasksPluginSupport) },
                        onEdit = {
                            sheetMode = TodoSheetMode.Edit(todo)
                            sheetText = todo.text
                            sheetMeta = MarkdownParser.TasksMeta(
                                dueDate = todo.dueDate,
                                startDate = todo.startDate,
                                scheduledDate = todo.scheduledDate,
                                completionDate = todo.completionDate,
                                createdDate = todo.createdDate,
                                priority = todo.priority,
                                recurrence = todo.recurrence,
                            )
                        },
                        onRequestDelete = { deleteTodoTarget = todo },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 180),
                            placementSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            fadeOutSpec = tween(durationMillis = 160),
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (uiState.completedTodos.isNotEmpty()) {
                item { TodosianSectionHeader(text = stringResource(R.string.category_completed)) }
                items(
                    items = uiState.completedTodos,
                    key = { it.id },
                ) { todo ->
                    TodoRow(
                        todo = todo,
                        enableTasksPluginSupport = settings.enableTasksPluginSupport,
                        useEmojisInUi = settings.tasksPluginUseEmojisInUi,
                        onToggle = { viewModel.toggleTodo(todo, settings.enableTasksPluginSupport) },
                        onEdit = {
                            sheetMode = TodoSheetMode.Edit(todo)
                            sheetText = todo.text
                            sheetMeta = MarkdownParser.TasksMeta(
                                dueDate = todo.dueDate,
                                startDate = todo.startDate,
                                scheduledDate = todo.scheduledDate,
                                completionDate = todo.completionDate,
                                createdDate = todo.createdDate,
                                priority = todo.priority,
                                recurrence = todo.recurrence,
                            )
                        },
                        onRequestDelete = { deleteTodoTarget = todo },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 180),
                            placementSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            fadeOutSpec = tween(durationMillis = 160),
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoRow(
    todo: Todo,
    enableTasksPluginSupport: Boolean,
    useEmojisInUi: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
                false
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        modifier = modifier,
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Checkbox(
                    checked = todo.isDone,
                    onCheckedChange = { onToggle() },
                )
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    val targetColor = if (todo.isDone) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    val textColor by animateColorAsState(
                        targetValue = targetColor,
                        animationSpec = tween(300),
                        label = "todo-text-color",
                    )
                    Text(
                        text = todo.text,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        color = textColor,
                    )

                    if (enableTasksPluginSupport) {
                        val chips = buildTasksMetaChips(todo = todo, useEmojisInUi = useEmojisInUi)
                        if (chips.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                chips.forEach { chip ->
                                    androidx.compose.material3.AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        leadingIcon = chip.icon?.let { icon ->
                                            {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = chip.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun TasksMetaEditor(
    mode: TodoSheetMode?,
    meta: MarkdownParser.TasksMeta,
    onMetaChange: (MarkdownParser.TasksMeta) -> Unit,
    useEmojisInUi: Boolean,
    modifier: Modifier = Modifier,
) {
    val isAdd = mode is TodoSheetMode.Add

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.category_tasks_metadata_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        DateMetaRow(
            label = if (useEmojisInUi) {
                stringResource(R.string.category_tasks_created)
            } else {
                stringResource(R.string.category_tasks_created_label)
            },
            icon = if (useEmojisInUi) null else Icons.Outlined.AddCircle,
            value = meta.createdDate,
            allowClear = !isAdd,
            onPick = { picked -> onMetaChange(meta.copy(createdDate = picked)) },
            onClear = { onMetaChange(meta.copy(createdDate = null)) },
        )

        DateMetaRow(
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_due) else stringResource(R.string.category_tasks_due_label),
            icon = if (useEmojisInUi) null else Icons.Outlined.Event,
            value = meta.dueDate,
            allowClear = true,
            onPick = { picked -> onMetaChange(meta.copy(dueDate = picked)) },
            onClear = { onMetaChange(meta.copy(dueDate = null)) },
        )

        DateMetaRow(
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_start) else stringResource(R.string.category_tasks_start_label),
            icon = if (useEmojisInUi) null else Icons.Outlined.FlightTakeoff,
            value = meta.startDate,
            allowClear = true,
            onPick = { picked -> onMetaChange(meta.copy(startDate = picked)) },
            onClear = { onMetaChange(meta.copy(startDate = null)) },
        )

        DateMetaRow(
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_scheduled) else stringResource(R.string.category_tasks_scheduled_label),
            icon = if (useEmojisInUi) null else Icons.Outlined.Schedule,
            value = meta.scheduledDate,
            allowClear = true,
            onPick = { picked -> onMetaChange(meta.copy(scheduledDate = picked)) },
            onClear = { onMetaChange(meta.copy(scheduledDate = null)) },
        )

        DateMetaRow(
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_done) else stringResource(R.string.category_tasks_done_label),
            icon = if (useEmojisInUi) null else Icons.Outlined.CheckCircle,
            value = meta.completionDate,
            allowClear = true,
            onPick = { picked -> onMetaChange(meta.copy(completionDate = picked)) },
            onClear = { onMetaChange(meta.copy(completionDate = null)) },
        )

        PriorityMetaRow(
            value = meta.priority,
            onChange = { onMetaChange(meta.copy(priority = it)) },
            useEmojisInUi = useEmojisInUi,
        )

        OutlinedTextField(
            value = meta.recurrence.orEmpty(),
            onValueChange = { value ->
                onMetaChange(meta.copy(recurrence = value.ifBlank { null }))
            },
            singleLine = true,
            label = {
                Text(
                    text = if (useEmojisInUi) {
                        stringResource(R.string.category_tasks_recurrence)
                    } else {
                        stringResource(R.string.category_tasks_recurrence_label)
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DateMetaRow(
    label: String,
    icon: ImageVector?,
    value: String?,
    allowClear: Boolean,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TextButton(
            onClick = {
                val initial = runCatching { value?.let(LocalDate::parse) }.getOrNull() ?: LocalDate.now()
                showDatePicker(
                    context = context,
                    initial = initial,
                    onPicked = onPick,
                )
            },
        ) {
            Text(text = value ?: stringResource(R.string.action_set))
        }

        if (allowClear && value != null) {
            TextButton(onClick = onClear) {
                Text(text = stringResource(R.string.action_clear))
            }
        }
    }
}

@Composable
private fun PriorityMetaRow(
    value: TasksPriority?,
    onChange: (TasksPriority?) -> Unit,
    useEmojisInUi: Boolean,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
    ) {
        Text(
            text = stringResource(R.string.category_tasks_priority),
            style = MaterialTheme.typography.bodyMedium,
        )

        PriorityChip(
            selected = value == null,
            label = stringResource(R.string.category_tasks_priority_none),
            onClick = { onChange(null) },
            icon = null,
        )
        PriorityChip(
            selected = value == TasksPriority.LOWEST,
            label = if (useEmojisInUi) {
                stringResource(R.string.category_tasks_priority_lowest)
            } else {
                stringResource(R.string.category_tasks_priority_lowest_label)
            },
            onClick = { onChange(TasksPriority.LOWEST) },
            icon = if (useEmojisInUi) null else Icons.Outlined.Star,
        )
        PriorityChip(
            selected = value == TasksPriority.LOW,
            label = if (useEmojisInUi) {
                stringResource(R.string.category_tasks_priority_low)
            } else {
                stringResource(R.string.category_tasks_priority_low_label)
            },
            onClick = { onChange(TasksPriority.LOW) },
            icon = if (useEmojisInUi) null else Icons.Outlined.Star,
        )
        PriorityChip(
            selected = value == TasksPriority.MEDIUM,
            label = if (useEmojisInUi) {
                stringResource(R.string.category_tasks_priority_medium)
            } else {
                stringResource(R.string.category_tasks_priority_medium_label)
            },
            onClick = { onChange(TasksPriority.MEDIUM) },
            icon = if (useEmojisInUi) null else Icons.Outlined.Star,
        )
        PriorityChip(
            selected = value == TasksPriority.HIGH,
            label = if (useEmojisInUi) {
                stringResource(R.string.category_tasks_priority_high)
            } else {
                stringResource(R.string.category_tasks_priority_high_label)
            },
            onClick = { onChange(TasksPriority.HIGH) },
            icon = if (useEmojisInUi) null else Icons.Outlined.Star,
        )
        PriorityChip(
            selected = value == TasksPriority.HIGHEST,
            label = if (useEmojisInUi) {
                stringResource(R.string.category_tasks_priority_highest)
            } else {
                stringResource(R.string.category_tasks_priority_highest_label)
            },
            onClick = { onChange(TasksPriority.HIGHEST) },
            icon = if (useEmojisInUi) null else Icons.Outlined.Star,
        )
    }
}

@Composable
private fun PriorityChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: ImageVector?,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = icon?.let { image ->
            {
                Icon(
                    imageVector = image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private fun showDatePicker(
    context: android.content.Context,
    initial: LocalDate,
    onPicked: (String) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val picked = LocalDate.of(year, month + 1, dayOfMonth)
            onPicked(picked.toString())
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

private data class TasksMetaChipUi(
    val label: String,
    val icon: ImageVector?,
)

@Composable
private fun buildTasksMetaChips(
    todo: Todo,
    useEmojisInUi: Boolean,
): List<TasksMetaChipUi> {
    val chips = ArrayList<TasksMetaChipUi>(8)

    todo.createdDate?.let { value ->
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_created, value)
        } else {
            stringResource(R.string.category_tasks_chip_created_label, value)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.AddCircle))
    }
    todo.startDate?.let { value ->
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_start, value)
        } else {
            stringResource(R.string.category_tasks_chip_start_label, value)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.FlightTakeoff))
    }
    todo.scheduledDate?.let { value ->
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_scheduled, value)
        } else {
            stringResource(R.string.category_tasks_chip_scheduled_label, value)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.Schedule))
    }
    todo.dueDate?.let { value ->
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_due, value)
        } else {
            stringResource(R.string.category_tasks_chip_due_label, value)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.Event))
    }
    todo.completionDate?.let { value ->
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_done, value)
        } else {
            stringResource(R.string.category_tasks_chip_done_label, value)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.CheckCircle))
    }

    if (!todo.recurrence.isNullOrBlank()) {
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_recurrence, todo.recurrence!!)
        } else {
            stringResource(R.string.category_tasks_chip_recurrence_label, todo.recurrence!!)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.Repeat))
    }

    val prio = todo.priority
    if (prio != null && prio != TasksPriority.NONE) {
        val label = when (prio) {
            TasksPriority.LOWEST -> if (useEmojisInUi) stringResource(R.string.category_tasks_priority_lowest) else stringResource(R.string.category_tasks_priority_lowest_label)
            TasksPriority.LOW -> if (useEmojisInUi) stringResource(R.string.category_tasks_priority_low) else stringResource(R.string.category_tasks_priority_low_label)
            TasksPriority.MEDIUM -> if (useEmojisInUi) stringResource(R.string.category_tasks_priority_medium) else stringResource(R.string.category_tasks_priority_medium_label)
            TasksPriority.HIGH -> if (useEmojisInUi) stringResource(R.string.category_tasks_priority_high) else stringResource(R.string.category_tasks_priority_high_label)
            TasksPriority.HIGHEST -> if (useEmojisInUi) stringResource(R.string.category_tasks_priority_highest) else stringResource(R.string.category_tasks_priority_highest_label)
            TasksPriority.NONE -> ""
        }
        chips.add(
            TasksMetaChipUi(
                label = label,
                icon = if (useEmojisInUi) null else Icons.Outlined.Star,
            ),
        )
    }

    return chips.filter { it.label.isNotBlank() }
}

private sealed interface TodoSheetMode {
    data object Add : TodoSheetMode

    data class Edit(val todo: Todo) : TodoSheetMode
}

private class CategoryViewModelFactory(
    private val fileRepository: FileRepository,
    private val categoryUri: Uri,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CategoryViewModel(fileRepository, categoryUri) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
