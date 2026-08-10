package com.isotjs.todosian.ui.category

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.data.model.priorityRank
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.data.settings.NewTodoFilePosition
import com.isotjs.todosian.data.settings.TodoGrouping
import com.isotjs.todosian.data.settings.TodoSort
import com.isotjs.todosian.ui.components.TasksMetaEditor
import com.isotjs.todosian.ui.components.TodoRow
import com.isotjs.todosian.ui.components.TodoSheetMode
import com.isotjs.todosian.ui.components.TodosianDimens
import com.isotjs.todosian.ui.components.TodosianSectionHeader
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(
    fileRepository: FileRepository,
    appSettingsRepository: AppSettingsRepository,
    categoryUri: Uri,
    onBack: () -> Unit,
    openAddTodo: Boolean = false,
    addRequestId: Long = 0L,
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

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshFromDisk(showLoading = false)
        }
    }

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
    var addTodoRequestHandled by rememberSaveable(categoryUri, addRequestId) { mutableStateOf(false) }

    LaunchedEffect(openAddTodo, categoryUri, addRequestId) {
        if (!openAddTodo) {
            addTodoRequestHandled = false
            return@LaunchedEffect
        }
        if (addTodoRequestHandled) return@LaunchedEffect
        addTodoRequestHandled = true
        sheetMode = TodoSheetMode.Add
        sheetText = ""
        sheetMeta = MarkdownParser.TasksMeta()
        sheetParentTodo = null
    }

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
            val todoTextFocusRequester = remember { FocusRequester() }
            val keyboardController = LocalSoftwareKeyboardController.current
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

            val shouldFocusTodoText = sheetMode == TodoSheetMode.Add ||
                sheetMode == TodoSheetMode.AddSubtask
            LaunchedEffect(sheetMode) {
                if (!shouldFocusTodoText) return@LaunchedEffect
                // Wait for the sheet enter animation so focus/IME stick.
                delay(350)
                todoTextFocusRequester.requestFocus()
                keyboardController?.show()
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
                        .focusRequester(todoTextFocusRequester)
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
                                    addAtStart = settings.newTodoFilePosition == NewTodoFilePosition.TOP,
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

        val canReorder = settings.todoSort == TodoSort.FILE_ORDER
        val sortedActiveTodos = remember(uiState.activeTodos, settings.todoSort) {
            sortTodos(uiState.activeTodos, settings.todoSort)
        }
        val sortedCompletedTodos = remember(uiState.completedTodos, settings.todoSort) {
            sortTodos(uiState.completedTodos, settings.todoSort)
        }
        val sortedAllTodos = remember(uiState.activeTodos, uiState.completedTodos, settings.todoSort) {
            sortTodos(uiState.activeTodos + uiState.completedTodos, settings.todoSort)
        }

        val haptic = LocalHapticFeedback.current
        val density = LocalDensity.current
        val lazyListState = rememberLazyListState()

        val allItemsState = remember { mutableStateOf<List<ReorderTodoItem>?>(null) }
        val activeItemsState = remember { mutableStateOf<List<ReorderTodoItem>?>(null) }
        val completedItemsState = remember { mutableStateOf<List<ReorderTodoItem>?>(null) }
        val dragSectionState = remember { mutableStateOf<DragSection?>(null) }
        val draggedKeyState = remember { mutableStateOf<String?>(null) }
        val draggedLevelState = remember { mutableIntStateOf(0) }
        val dragOffsetYState = remember { mutableFloatStateOf(0f) }
        val dropInsertBeforeState = remember { mutableStateOf<Int?>(null) }
        val dragFingerYState = remember { mutableFloatStateOf(0f) }
        val dragSessionState = remember { mutableIntStateOf(0) }

        fun setSectionItems(section: DragSection, items: List<ReorderTodoItem>) {
            when (section) {
                DragSection.All -> allItemsState.value = items
                DragSection.Active -> activeItemsState.value = items
                DragSection.Completed -> completedItemsState.value = items
            }
        }

        fun fallbackItems(section: DragSection): List<ReorderTodoItem> = when (section) {
            DragSection.All -> toReorderItems(sortedAllTodos, uiState.lines)
            DragSection.Active -> toReorderItems(sortedActiveTodos, uiState.lines)
            DragSection.Completed -> toReorderItems(sortedCompletedTodos, uiState.lines)
        }

        fun sectionItems(section: DragSection): List<ReorderTodoItem> =
            when (section) {
                DragSection.All -> allItemsState.value
                DragSection.Active -> activeItemsState.value
                DragSection.Completed -> completedItemsState.value
            } ?: fallbackItems(section)

        LaunchedEffect(
            sortedAllTodos,
            sortedActiveTodos,
            sortedCompletedTodos,
            uiState.lines,
            canReorder,
        ) {
            if (dragSectionState.value != null) return@LaunchedEffect
            if (!canReorder) {
                allItemsState.value = emptyList()
                activeItemsState.value = emptyList()
                completedItemsState.value = emptyList()
                return@LaunchedEffect
            }
            allItemsState.value = toReorderItems(sortedAllTodos, uiState.lines)
            activeItemsState.value = toReorderItems(sortedActiveTodos, uiState.lines)
            completedItemsState.value = toReorderItems(sortedCompletedTodos, uiState.lines)
        }

        val allItems = allItemsState.value
            ?: if (canReorder) toReorderItems(sortedAllTodos, uiState.lines) else emptyList()
        val activeItems = activeItemsState.value
            ?: if (canReorder) toReorderItems(sortedActiveTodos, uiState.lines) else emptyList()
        val completedItems = completedItemsState.value
            ?: if (canReorder) toReorderItems(sortedCompletedTodos, uiState.lines) else emptyList()

        fun clearDragState() {
            dragSectionState.value = null
            draggedKeyState.value = null
            draggedLevelState.intValue = 0
            dragOffsetYState.floatValue = 0f
            dropInsertBeforeState.value = null
            dragFingerYState.floatValue = 0f
            dragSessionState.intValue += 1
        }

        fun applyLocalInsert(
            section: DragSection,
            items: List<ReorderTodoItem>,
            draggedKey: String,
            insertBefore: Int,
        ) {
            val next = insertTodoBlock(items, draggedKey, insertBefore) ?: return
            setSectionItems(section, next)
        }

        fun updateDropTarget(section: DragSection) {
            val draggedKey = draggedKeyState.value ?: return
            val items = sectionItems(section)
            val draggedIndex = items.indexOfFirst { it.stableKey == draggedKey }
            if (draggedIndex < 0) {
                dropInsertBeforeState.value = null
                return
            }
            val layoutByKey = lazyListState.layoutInfo.visibleItemsInfo.associateBy { it.key }
            val visibleBounds = items.mapIndexedNotNull { index, item ->
                val info = layoutByKey[item.stableKey] ?: return@mapIndexedNotNull null
                VisibleItemBounds(
                    fullIndex = index,
                    top = info.offset.toFloat(),
                    bottom = (info.offset + info.size).toFloat(),
                )
            }
            val contentSlopPx = with(density) { 48.dp.toPx() }
            val nextInsert = resolveValidDropInsertBefore(
                items = items,
                draggedKey = draggedKey,
                draggedLevel = items[draggedIndex].todo.indentLevel,
                visibleBounds = visibleBounds,
                fingerY = dragFingerYState.floatValue,
                contentSlopPx = contentSlopPx,
            )
            if (nextInsert != dropInsertBeforeState.value) {
                dropInsertBeforeState.value = nextInsert
                if (nextInsert != null && nextInsert != draggedIndex) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            }
        }

        fun commitDragIfNeeded(section: DragSection) {
            val draggedKey = draggedKeyState.value ?: run {
                clearDragState()
                return
            }
            val insertBefore = dropInsertBeforeState.value
            if (insertBefore == null) {
                clearDragState()
                return
            }
            val items = sectionItems(section)
            val commit = resolveTodoDragCommit(items, draggedKey, insertBefore)
            if (commit == null) {
                clearDragState()
                return
            }
            applyLocalInsert(section, items, draggedKey, commit.insertBefore)
            clearDragState()
            when (commit) {
                is TodoDragCommit.ReorderTopLevel -> viewModel.applyTodoOrder(
                    orderedLineIndices = commit.orderedLineIndices,
                    newOrderedLineIndices = commit.newOrderedLineIndices,
                )
                is TodoDragCommit.MoveUnderParent -> viewModel.moveTodoUnderParent(
                    todoLineIndex = commit.todoLineIndex,
                    newParentLineIndex = commit.newParentLineIndex,
                    beforeSiblingLineIndex = commit.beforeSiblingLineIndex,
                )
            }
        }

        val onDragStartedForSection: (DragSection, ReorderTodoItem) -> Unit =
            fun(section: DragSection, item: ReorderTodoItem) {
                if (draggedKeyState.value != null) return
                dragSectionState.value = section
                draggedKeyState.value = item.stableKey
                draggedLevelState.intValue = item.todo.indentLevel
                dragOffsetYState.floatValue = 0f
                dropInsertBeforeState.value = null
                val layoutInfo = lazyListState.layoutInfo
                val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.stableKey }
                dragFingerYState.floatValue = if (itemInfo != null) {
                    itemInfo.offset + itemInfo.size / 2f
                } else {
                    0f
                }
                updateDropTarget(section)
            }

        val onDragDeltaForSection: (DragSection, Float) -> Unit =
            fun(section: DragSection, deltaY: Float) {
                if (dragSectionState.value != section) return
                dragOffsetYState.floatValue += deltaY
                dragFingerYState.floatValue += deltaY
                updateDropTarget(section)
            }

        val onDragStoppedForSection: (DragSection) -> Unit = { section ->
            if (dragSectionState.value == section) {
                commitDragIfNeeded(section)
            } else {
                clearDragState()
            }
        }

        // Auto-scroll near viewport edges while dragging.
        // Key only on draggedKey so finger movement does not restart the loop.
        LaunchedEffect(draggedKeyState.value) {
            if (draggedKeyState.value == null) return@LaunchedEffect
            val edgePx = with(density) { 56.dp.toPx() }
            val scrollStep = with(density) { 12.dp.toPx() }
            while (isActive && draggedKeyState.value != null) {
                val layoutInfo = lazyListState.layoutInfo
                val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
                val fingerY = dragFingerYState.floatValue
                val section = dragSectionState.value
                val delta = when {
                    fingerY < viewportStart + edgePx -> -scrollStep
                    fingerY > viewportEnd - edgePx -> scrollStep
                    else -> 0f
                }
                if (delta == 0f || section == null) {
                    delay(16)
                    continue
                }
                lazyListState.scrollBy(delta)
                updateDropTarget(section)
                delay(16)
            }
        }

        val todoActions = TodoListActions(
            enableTasksPluginSupport = settings.enableTasksPluginSupport,
            useEmojisInUi = settings.tasksPluginUseEmojisInUi,
            onToggle = { todo -> viewModel.toggleTodo(todo, settings.enableTasksPluginSupport) },
            onEdit = { todo ->
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
            onAddSubtask = { todo ->
                sheetMode = TodoSheetMode.AddSubtask
                sheetParentTodo = todo
                sheetText = ""
                sheetMeta = if (settings.enableTasksPluginSupport) {
                    MarkdownParser.TasksMeta(createdDate = LocalDate.now().toString())
                } else {
                    MarkdownParser.TasksMeta()
                }
            },
            onRequestDelete = { todo ->
                deleteTodoTarget = todo
                val resolvedIndex = MarkdownParser.resolveTodoLineIndex(uiState.lines, todo)
                    ?: todo.lineIndex
                deleteTodoHasSubtasks = MarkdownParser.hasSubtasks(uiState.lines, resolvedIndex)
            },
            onRequestMove = { todo -> moveTodoTarget = todo },
        )

        val draggedKey = draggedKeyState.value
        val dropInsertBefore = dropInsertBeforeState.value
        val dragOffsetY = dragOffsetYState.floatValue
        val dragSection = dragSectionState.value
        val draggedLevel = draggedLevelState.intValue
        val dragSession = dragSessionState.intValue

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = TodosianDimens.ScreenHorizontalPadding),
        ) {
            if (settings.todoGrouping == TodoGrouping.FILE_ORDER) {
                if (canReorder) {
                    draggableTodoItems(
                        items = allItems,
                        actions = todoActions,
                        draggedKey = draggedKey,
                        dragOffsetY = dragOffsetY,
                        dropInsertBefore = if (dragSection == DragSection.All) dropInsertBefore else null,
                        draggedIndentLevel = draggedLevel,
                        dragSession = dragSession,
                        onDragStarted = { item -> onDragStartedForSection(DragSection.All, item) },
                        onDragDelta = { delta -> onDragDeltaForSection(DragSection.All, delta) },
                        onDragStopped = { onDragStoppedForSection(DragSection.All) },
                    )
                } else {
                    flatTodoItems(
                        todos = sortedAllTodos,
                        actions = todoActions,
                    )
                }
                item { Spacer(modifier = Modifier.height(96.dp)) }
                return@LazyColumn
            }

            if (canReorder) {
                if (activeItems.isNotEmpty()) {
                    item(key = "header-active") {
                        TodosianSectionHeader(text = stringResource(R.string.category_active))
                    }
                    draggableTodoItems(
                        items = activeItems,
                        actions = todoActions,
                        draggedKey = draggedKey,
                        dragOffsetY = dragOffsetY,
                        dropInsertBefore = if (dragSection == DragSection.Active) {
                            dropInsertBefore
                        } else {
                            null
                        },
                        draggedIndentLevel = draggedLevel,
                        dragSession = dragSession,
                        onDragStarted = { item -> onDragStartedForSection(DragSection.Active, item) },
                        onDragDelta = { delta -> onDragDeltaForSection(DragSection.Active, delta) },
                        onDragStopped = { onDragStoppedForSection(DragSection.Active) },
                    )
                }
                if (completedItems.isNotEmpty()) {
                    item(key = "header-completed") {
                        TodosianSectionHeader(text = stringResource(R.string.category_completed))
                    }
                    draggableTodoItems(
                        items = completedItems,
                        actions = todoActions,
                        draggedKey = draggedKey,
                        dragOffsetY = dragOffsetY,
                        dropInsertBefore = if (dragSection == DragSection.Completed) {
                            dropInsertBefore
                        } else {
                            null
                        },
                        draggedIndentLevel = draggedLevel,
                        dragSession = dragSession,
                        onDragStarted = { item ->
                            onDragStartedForSection(DragSection.Completed, item)
                        },
                        onDragDelta = { delta ->
                            onDragDeltaForSection(DragSection.Completed, delta)
                        },
                        onDragStopped = { onDragStoppedForSection(DragSection.Completed) },
                    )
                }
            } else {
                if (sortedActiveTodos.isNotEmpty()) {
                    item { TodosianSectionHeader(text = stringResource(R.string.category_active)) }
                    flatTodoItems(
                        todos = sortedActiveTodos,
                        actions = todoActions,
                    )
                }
                if (sortedCompletedTodos.isNotEmpty()) {
                    item { TodosianSectionHeader(text = stringResource(R.string.category_completed)) }
                    flatTodoItems(
                        todos = sortedCompletedTodos,
                        actions = todoActions,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

private data class TodoListActions(
    val enableTasksPluginSupport: Boolean,
    val useEmojisInUi: Boolean,
    val onToggle: (Todo) -> Unit,
    val onEdit: (Todo) -> Unit,
    val onAddSubtask: (Todo) -> Unit,
    val onRequestDelete: (Todo) -> Unit,
    val onRequestMove: (Todo) -> Unit,
)

private fun LazyListScope.flatTodoItems(
    todos: List<Todo>,
    actions: TodoListActions,
) {
    items(
        items = todos,
        key = { it.id },
    ) { todo ->
        CategoryTodoRow(
            todo = todo,
            actions = actions,
            dragHandleModifier = null,
            modifier = Modifier.animateItem(
                fadeInSpec = tween(durationMillis = 180),
                placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                fadeOutSpec = tween(durationMillis = 160),
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun LazyListScope.draggableTodoItems(
    items: List<ReorderTodoItem>,
    actions: TodoListActions,
    draggedKey: String?,
    dragOffsetY: Float,
    dropInsertBefore: Int?,
    draggedIndentLevel: Int,
    dragSession: Int,
    onDragStarted: (ReorderTodoItem) -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragStopped: () -> Unit,
) {
    val draggedBlockRange = if (draggedKey != null) {
        val start = items.indexOfFirst { it.stableKey == draggedKey }
        if (start >= 0) {
            val end = start + todoBlockItemCount(items, start)
            start until end
        } else {
            null
        }
    } else {
        null
    }
    val itemsWithout = if (draggedKey != null) {
        removeTodoBlock(items, draggedKey).first
    } else {
        items
    }
    val fullIndexByKey = items.withIndex().associate { (index, item) -> item.stableKey to index }
    val withoutIndexByKey = itemsWithout.withIndex()
        .associate { (index, item) -> item.stableKey to index }

    items(
        items = items,
        key = { it.stableKey },
    ) { item ->
        val indexInFull = fullIndexByKey[item.stableKey] ?: -1
        val indexInWithout = withoutIndexByKey[item.stableKey] ?: -1
        val inDraggedBlock = draggedBlockRange != null && indexInFull in draggedBlockRange
        val showLineAbove = dropInsertBefore != null &&
            indexInWithout >= 0 &&
            dropInsertBefore == indexInWithout
        val showLineBelow = indexInWithout >= 0 &&
            indexInWithout == itemsWithout.lastIndex &&
            dropInsertBefore == itemsWithout.size

        val currentOnDragStarted by rememberUpdatedState(onDragStarted)
        val currentOnDragDelta by rememberUpdatedState(onDragDelta)
        val currentOnDragStopped by rememberUpdatedState(onDragStopped)

        // Animate placement only when not mid-drag; during drag, custom translation owns motion.
        Box(
            modifier = Modifier
                .animateItem(
                    fadeInSpec = tween(durationMillis = 180),
                    placementSpec = if (draggedKey == null) {
                        spring(stiffness = Spring.StiffnessMediumLow)
                    } else {
                        null
                    },
                    fadeOutSpec = tween(durationMillis = 160),
                )
                .zIndex(if (inDraggedBlock) 1f else 0f)
                .graphicsLayer(
                    translationY = if (inDraggedBlock) dragOffsetY else 0f,
                    alpha = if (inDraggedBlock) 0.92f else 1f,
                ),
        ) {
            Column {
                CategoryTodoRow(
                    todo = item.todo,
                    actions = actions,
                    // Long-press then drag so a normal swipe on the handle still scrolls.
                    dragHandleModifier = Modifier.pointerInput(item.stableKey, dragSession) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { currentOnDragStarted(item) },
                            onDragEnd = { currentOnDragStopped() },
                            onDragCancel = { currentOnDragStopped() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDragDelta(dragAmount.y)
                            },
                        )
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Overlay so the indicator does not open a layout gap.
            if (showLineAbove) {
                DropIndicatorLine(
                    indentLevel = draggedIndentLevel,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            if (showLineBelow) {
                DropIndicatorLine(
                    indentLevel = draggedIndentLevel,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}

@Composable
private fun DropIndicatorLine(
    indentLevel: Int,
    modifier: Modifier = Modifier,
) {
    val startPadding = (indentLevel * 12).coerceAtMost(48).dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .height(3.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.5.dp)),
    )
}

@Composable
private fun CategoryTodoRow(
    todo: Todo,
    actions: TodoListActions,
    dragHandleModifier: Modifier?,
    modifier: Modifier = Modifier,
) {
    TodoRow(
        todo = todo,
        enableTasksPluginSupport = actions.enableTasksPluginSupport,
        useEmojisInUi = actions.useEmojisInUi,
        onToggle = { actions.onToggle(todo) },
        onEdit = { actions.onEdit(todo) },
        onAddSubtask = { actions.onAddSubtask(todo) },
        onRequestDelete = { actions.onRequestDelete(todo) },
        onRequestMove = { actions.onRequestMove(todo) },
        // Keep swipe enabled with the reorder handle: horizontal dismiss vs long-press vertical drag.
        dragHandleModifier = dragHandleModifier,
        modifier = modifier,
    )
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
        compareByDescending<TodoGroup> { it.parent.priority.priorityRank() }
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
