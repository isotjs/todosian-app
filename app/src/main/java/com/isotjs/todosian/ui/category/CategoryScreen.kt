package com.isotjs.todosian.ui.category

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.model.TasksPriority
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.data.settings.TodoGrouping
import com.isotjs.todosian.data.settings.TodoSort
import com.isotjs.todosian.ui.components.TasksMetaEditor
import com.isotjs.todosian.ui.components.TodoRow
import com.isotjs.todosian.ui.components.TodoSheetMode
import com.isotjs.todosian.ui.components.TodosianDimens
import com.isotjs.todosian.ui.components.TodosianSectionHeader
import com.isotjs.todosian.ui.components.priorityRank
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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

    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is CategoryViewModel.Event.ShowMessage -> {
                    snackbarHostState.showSnackbar(resources.getString(event.messageResId))
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val todoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val moveSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sheetMode by remember { mutableStateOf<TodoSheetMode?>(null) }
    var sheetText by remember { mutableStateOf("") }
    var sheetMeta by remember { mutableStateOf(MarkdownParser.TasksMeta()) }
    var sheetParentTodo by remember { mutableStateOf<Todo?>(null) }
    var deleteTodoTarget by remember { mutableStateOf<Todo?>(null) }
    var deleteTodoHasSubtasks by remember { mutableStateOf(false) }
    var moveTodoTarget by remember { mutableStateOf<Todo?>(null) }
    var showCopyOption by remember { mutableStateOf(false) }

    if (sheetMode != null) {
        ModalBottomSheet(
            onDismissRequest = {
                sheetMode = null
                sheetText = ""
                sheetMeta = MarkdownParser.TasksMeta()
                sheetParentTodo = null
            },
            sheetState = todoSheetState,
        ) {
            val scrollState = rememberScrollState()
            val todoTextBringIntoView = remember { BringIntoViewRequester() }
            val recurrenceBringIntoView = remember { BringIntoViewRequester() }
            val titleRes = when (sheetMode) {
                TodoSheetMode.Add -> R.string.category_add_todo_title
                is TodoSheetMode.Edit -> R.string.category_edit_todo_title
                TodoSheetMode.AddSubtask -> R.string.category_add_subtask_title
                null -> R.string.category_add_todo_title
            }

            val hintRes = when (sheetMode) {
                TodoSheetMode.Add -> R.string.category_add_todo_hint
                is TodoSheetMode.Edit -> R.string.category_edit_todo_hint
                TodoSheetMode.AddSubtask -> R.string.category_add_subtask_hint
                null -> R.string.category_add_todo_hint
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.ime)
                    .padding(bottom = 16.dp)
                    .verticalScroll(scrollState),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(todoTextBringIntoView)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                scope.launch { todoTextBringIntoView.bringIntoView() }
                            }
                        },
                )

                if (settings.enableTasksPluginSupport) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TasksMetaEditor(
                        mode = sheetMode,
                        meta = sheetMeta,
                        onMetaChange = { sheetMeta = it },
                        useEmojisInUi = settings.tasksPluginUseEmojisInUi,
                        modifier = Modifier
                            .bringIntoViewRequester(recurrenceBringIntoView)
                            .onFocusEvent { focusState ->
                                if (focusState.isFocused) {
                                    scope.launch { recurrenceBringIntoView.bringIntoView() }
                                }
                            },
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
                                sheetParentTodo = null
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

                                TodoSheetMode.AddSubtask -> {
                                    val parent = sheetParentTodo
                                    if (parent != null) {
                                        viewModel.addSubTodo(
                                            parent = parent,
                                            text = sheetText,
                                            meta = if (settings.enableTasksPluginSupport) sheetMeta else null,
                                            enableTasksPluginSupport = settings.enableTasksPluginSupport,
                                        )
                                    }
                                }

                                null -> Unit
                            }
                            scope.launch {
                                todoSheetState.hide()
                                sheetMode = null
                                sheetText = ""
                                sheetMeta = MarkdownParser.TasksMeta()
                                sheetParentTodo = null
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
        val deleteBodyRes = if (deleteTodoHasSubtasks) {
            R.string.category_delete_todo_body_with_subtasks
        } else {
            R.string.category_delete_todo_body
        }
        AlertDialog(
            onDismissRequest = { deleteTodoTarget = null },
            title = { Text(text = stringResource(R.string.category_delete_todo_title)) },
            text = { Text(text = stringResource(deleteBodyRes)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteTodoTarget
                        if (target != null) {
                            viewModel.deleteTodo(target)
                        }
                        deleteTodoTarget = null
                        deleteTodoHasSubtasks = false
                    },
                ) {
                    Text(text = stringResource(R.string.category_delete_todo_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteTodoTarget = null
                    deleteTodoHasSubtasks = false
                }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (moveTodoTarget != null) {
        ModalBottomSheet(
            onDismissRequest = {
                moveTodoTarget = null
                showCopyOption = false
            },
            sheetState = moveSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.category_move_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.category_move_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                val targets = uiState.moveTargets
                if (targets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.category_move_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    targets.forEach { target ->
                        ListItem(
                            headlineContent = { Text(text = target.title) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                val todo = moveTodoTarget
                                if (todo != null) {
                                    viewModel.moveTodo(todo, target.uri)
                                }
                                moveTodoTarget = null
                                showCopyOption = false
                            },
                        )
                    }
                }

                if (targets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { showCopyOption = !showCopyOption }) {
                        Text(
                            text = if (showCopyOption) {
                                stringResource(R.string.category_copy_hide)
                            } else {
                                stringResource(R.string.category_copy_show)
                            },
                        )
                    }

                    if (showCopyOption) {
                        Spacer(modifier = Modifier.height(8.dp))
                        targets.forEach { target ->
                            ListItem(
                                headlineContent = { Text(text = target.title) },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    val todo = moveTodoTarget
                                    if (todo != null) {
                                        viewModel.copyTodo(todo, target.uri)
                                    }
                                    moveTodoTarget = null
                                    showCopyOption = false
                                },
                            )
                        }
                    }
                }
            }
        }
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

        val sortedActiveTodos = remember(uiState.activeTodos, settings.todoSort) {
            sortTodos(uiState.activeTodos, settings.todoSort)
        }
        val sortedCompletedTodos = remember(uiState.completedTodos, settings.todoSort) {
            sortTodos(uiState.completedTodos, settings.todoSort)
        }
        val sortedAllTodos = remember(uiState.activeTodos, uiState.completedTodos, settings.todoSort) {
            sortTodos(uiState.activeTodos + uiState.completedTodos, settings.todoSort)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = TodosianDimens.ScreenHorizontalPadding),
        ) {
            if (settings.todoGrouping == TodoGrouping.FILE_ORDER) {
                items(
                    items = sortedAllTodos,
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
                        onAddSubtask = {
                            sheetMode = TodoSheetMode.AddSubtask
                            sheetParentTodo = todo
                            sheetText = ""
                            sheetMeta = if (settings.enableTasksPluginSupport) {
                                MarkdownParser.TasksMeta(createdDate = LocalDate.now().toString())
                            } else {
                                MarkdownParser.TasksMeta()
                            }
                        },
                        onRequestDelete = {
                            deleteTodoTarget = todo
                            deleteTodoHasSubtasks = MarkdownParser.hasSubtasks(uiState.lines, todo.lineIndex)
                        },
                        onRequestMove = { moveTodoTarget = todo },
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

            if (sortedActiveTodos.isNotEmpty()) {
                item { TodosianSectionHeader(text = stringResource(R.string.category_active)) }
                items(
                    items = sortedActiveTodos,
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
                        onAddSubtask = {
                            sheetMode = TodoSheetMode.AddSubtask
                            sheetParentTodo = todo
                            sheetText = ""
                            sheetMeta = if (settings.enableTasksPluginSupport) {
                                MarkdownParser.TasksMeta(createdDate = LocalDate.now().toString())
                            } else {
                                MarkdownParser.TasksMeta()
                            }
                        },
                        onRequestDelete = {
                            deleteTodoTarget = todo
                            deleteTodoHasSubtasks = MarkdownParser.hasSubtasks(uiState.lines, todo.lineIndex)
                        },
                        onRequestMove = { moveTodoTarget = todo },
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

            if (sortedCompletedTodos.isNotEmpty()) {
                item { TodosianSectionHeader(text = stringResource(R.string.category_completed)) }
                items(
                    items = sortedCompletedTodos,
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
                        onAddSubtask = {
                            sheetMode = TodoSheetMode.AddSubtask
                            sheetParentTodo = todo
                            sheetText = ""
                            sheetMeta = if (settings.enableTasksPluginSupport) {
                                MarkdownParser.TasksMeta(createdDate = LocalDate.now().toString())
                            } else {
                                MarkdownParser.TasksMeta()
                            }
                        },
                        onRequestDelete = {
                            deleteTodoTarget = todo
                            deleteTodoHasSubtasks = MarkdownParser.hasSubtasks(uiState.lines, todo.lineIndex)
                        },
                        onRequestMove = { moveTodoTarget = todo },
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

private fun sortTodos(todos: List<Todo>, sort: TodoSort): List<Todo> {
    return when (sort) {
        TodoSort.FILE_ORDER -> todos.sortedBy { it.lineIndex }
        TodoSort.PRIORITY_HIGH_TO_LOW -> sortTodosByPriorityKeepingSubtasks(todos)
        TodoSort.CREATED_DATE_NEWEST_FIRST ->
            todos.sortedWith(
                compareByDescending<Todo> { it.createdDate != null }
                    .thenByDescending { it.createdDate ?: "" }
                    .thenBy { it.lineIndex },
            )
            
        TodoSort.DUE_DATE_EARLIEST_FIRST ->
            todos.sortedWith(
                compareBy<Todo> { it.dueDate == null }
                    .thenBy { it.dueDate ?: "" }
                    .thenBy { it.lineIndex },
            )
    }
}

private data class TodoGroup(
    val parent: Todo,
    val children: List<Todo>,
)

private fun sortTodosByPriorityKeepingSubtasks(todos: List<Todo>): List<Todo> {
    if (todos.isEmpty()) return todos

    val ordered = todos.sortedBy { it.lineIndex }
    val groups = ArrayList<TodoGroup>()

    var currentParent: Todo? = null
    var currentChildren = ArrayList<Todo>()

    fun flushGroup() {
        val parent = currentParent
        if (parent != null) {
            groups.add(TodoGroup(parent = parent, children = currentChildren.toList()))
        }
    }

    for (todo in ordered) {
        if (todo.indentLevel == 0 || currentParent == null) {
            flushGroup()
            currentParent = todo
            currentChildren = ArrayList()
        } else {
            currentChildren.add(todo)
        }
    }

    flushGroup()

    val sortedGroups = groups.sortedWith(
        compareByDescending<TodoGroup> { priorityRank(it.parent.priority) }
            .thenBy { it.parent.lineIndex },
    )

    val result = ArrayList<Todo>(ordered.size)
    for (group in sortedGroups) {
        result.add(group.parent)
        result.addAll(group.children)
    }

    return result
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
