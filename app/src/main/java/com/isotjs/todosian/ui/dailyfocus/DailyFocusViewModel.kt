package com.isotjs.todosian.ui.dailyfocus

import android.net.Uri
import androidx.core.net.toUri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.model.TasksPriority
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.data.settings.DailyFocusMode
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class DailyFocusTask(
    val todo: Todo,
    val categoryUri: Uri,
    val categoryName: String,
)

data class DailyFocusUiState(
    val isLoading: Boolean = true,
    val tasks: List<DailyFocusTask> = emptyList(),

    internal val lineCache: Map<String, List<String>> = emptyMap(),
    internal val categoryNames: Map<String, String> = emptyMap(),
)

class DailyFocusViewModel(
    private val fileRepository: FileRepository,
    val mode: DailyFocusMode,
    val enableTasksPluginSupport: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyFocusUiState(isLoading = true))
    val uiState: StateFlow<DailyFocusUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    private val inFlightWrites = AtomicInteger(0)
    private val pendingRefreshFromObserver = AtomicBoolean(false)

    init {
        observeExternalChanges()
        refreshFromDisk(showLoading = true)
    }

    private fun observeExternalChanges() {
        val folderUri = fileRepository.getFolderUri() ?: return
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            fileRepository.observeMarkdownFilesChanges(folderUri)
                .debounce(350)
                .catch { /* best-effort */ }
                .collectLatest {
                    if (inFlightWrites.get() > 0) {
                        pendingRefreshFromObserver.set(true)
                        return@collectLatest
                    }
                    refreshFromDisk(showLoading = false)
                }
        }
    }

    fun refreshFromDisk(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            val today = LocalDate.now().toString()
            val categoriesResult = fileRepository.getCategories()
            if (categoriesResult.isFailure) {
                _uiState.value = DailyFocusUiState(isLoading = false)
                return@launch
            }

            val categories = categoriesResult.getOrThrow()
            val tasks = mutableListOf<DailyFocusTask>()
            val lineCache = mutableMapOf<String, List<String>>()
            val categoryNames = mutableMapOf<String, String>()

            for (category in categories) {
                val linesResult = fileRepository.readLines(category.uri)
                if (linesResult.isFailure) continue

                val lines = linesResult.getOrThrow()
                val uriKey = category.uri.toString()
                lineCache[uriKey] = lines
                categoryNames[uriKey] = category.displayName

                MarkdownParser.parse(lines)
                    .filter { !it.isDone }
                    .filter { todo -> matchesFocusMode(todo, mode, today) }
                    .forEach { todo ->
                        tasks.add(
                            DailyFocusTask(
                                todo = todo,
                                categoryUri = category.uri,
                                categoryName = category.displayName,
                            ),
                        )
                    }
            }

            _uiState.value = DailyFocusUiState(
                isLoading = false,
                tasks = sortTasks(tasks),
                lineCache = lineCache,
                categoryNames = categoryNames,
            )
        }
    }

    fun toggleTodo(task: DailyFocusTask) {
        viewModelScope.launch {
            val previousLines = currentLinesFor(task)
            val newLines = MarkdownParser.tryToggleLine(
                lines = previousLines,
                lineIndex = task.todo.lineIndex,
                enableTasksPlugin = enableTasksPluginSupport,
            )
            if (newLines == null) {
                _events.emit(Event.ShowMessage(R.string.error_read_failed))
                refreshFromDisk(showLoading = false)
                return@launch
            }

            applyLines(task.categoryUri, newLines)

            inFlightWrites.incrementAndGet()
            try {
                val write = fileRepository.writeLines(task.categoryUri, newLines)
                if (write.isFailure) {
                    if (currentLinesFor(task) == newLines) {
                        applyLines(task.categoryUri, previousLines)
                    }
                    _events.emit(Event.ShowMessage(R.string.error_write_failed))
                }
            } finally {
                onWriteFinishedMaybeRefresh()
            }
        }
    }

    fun editTodo(
        task: DailyFocusTask,
        newText: String,
        meta: MarkdownParser.TasksMeta?,
    ) {
        viewModelScope.launch {
            val previousLines = currentLinesFor(task)
            val newLines = if (enableTasksPluginSupport) {
                MarkdownParser.editTodo(
                    lines = previousLines,
                    lineIndex = task.todo.lineIndex,
                    newText = newText,
                    meta = meta,
                    enableTasksPlugin = true,
                )
            } else {
                val updated = MarkdownParser.tryEditTodoText(previousLines, task.todo.lineIndex, newText)
                if (updated == null) {
                    _events.emit(Event.ShowMessage(R.string.error_read_failed))
                    refreshFromDisk(showLoading = false)
                    return@launch
                }
                updated
            }

            applyLines(task.categoryUri, newLines)

            inFlightWrites.incrementAndGet()
            try {
                val write = fileRepository.writeLines(task.categoryUri, newLines)
                if (write.isFailure) {
                    if (currentLinesFor(task) == newLines) {
                        applyLines(task.categoryUri, previousLines)
                    }
                    _events.emit(Event.ShowMessage(R.string.error_write_failed))
                }
            } finally {
                onWriteFinishedMaybeRefresh()
            }
        }
    }

    fun deleteTodo(task: DailyFocusTask) {
        viewModelScope.launch {
            val previousLines = currentLinesFor(task)
            val newLines = MarkdownParser.tryDeleteTodoWithSubtasks(previousLines, task.todo.lineIndex)
            if (newLines == null) {
                _events.emit(Event.ShowMessage(R.string.error_read_failed))
                refreshFromDisk(showLoading = false)
                return@launch
            }

            applyLines(task.categoryUri, newLines)

            inFlightWrites.incrementAndGet()
            try {
                val write = fileRepository.writeLines(task.categoryUri, newLines)
                if (write.isFailure) {
                    if (currentLinesFor(task) == newLines) {
                        applyLines(task.categoryUri, previousLines)
                    }
                    _events.emit(Event.ShowMessage(R.string.error_write_failed))
                }
            } finally {
                onWriteFinishedMaybeRefresh()
            }
        }
    }

    sealed interface Event {
        data class ShowMessage(@param:StringRes val messageResId: Int) : Event
    }

    private fun currentLinesFor(task: DailyFocusTask): List<String> =
        _uiState.value.lineCache[task.categoryUri.toString()] ?: emptyList()

    // Replicates the logic from refreshFromDisk but only for a single file, used for optimistic updates after mutations.
    private fun applyLines(categoryUri: Uri, newLines: List<String>) {
        val today = LocalDate.now().toString()
        val current = _uiState.value
        val uriKey = categoryUri.toString()

        // Rebuild the cache with the updated file's lines.
        val newCache = current.lineCache.toMutableMap()
        newCache[uriKey] = newLines

        val tasks = mutableListOf<DailyFocusTask>()
        for ((uriString, lines) in newCache) {
            val uri = uriString.toUri()
            val categoryName = current.categoryNames[uriString] ?: continue

            MarkdownParser.parse(lines)
                .filter { !it.isDone }
                .filter { todo -> matchesFocusMode(todo, mode, today) }
                .forEach { todo ->
                    tasks.add(
                        DailyFocusTask(
                            todo = todo,
                            categoryUri = uri,
                            categoryName = categoryName,
                        ),
                    )
                }
        }

        _uiState.value = current.copy(
            tasks = sortTasks(tasks),
            lineCache = newCache,
        )
    }

    private fun onWriteFinishedMaybeRefresh() {
        val remaining = inFlightWrites.decrementAndGet().coerceAtLeast(0)
        if (remaining == 0 && pendingRefreshFromObserver.getAndSet(false)) {
            refreshFromDisk(showLoading = false)
        }
    }
}

private fun matchesFocusMode(todo: Todo, mode: DailyFocusMode, today: String): Boolean {
    return when (mode) {
        DailyFocusMode.TODAY -> todo.dueDate == today
        DailyFocusMode.OVERDUE -> todo.dueDate?.let { it < today } == true
        DailyFocusMode.TODAY_AND_OVERDUE -> {
            val due = todo.dueDate ?: return false
            due <= today
        }
    }
}

// Sort: due date earliest -> highest priority -> category name -> line index.
private fun sortTasks(tasks: List<DailyFocusTask>): List<DailyFocusTask> {
    return tasks.sortedWith(
        compareBy<DailyFocusTask> { it.todo.dueDate == null }
            .thenBy { it.todo.dueDate ?: "" }
            .thenByDescending { priorityRank(it.todo.priority) }
            .thenBy { it.categoryName.lowercase() }
            .thenBy { it.todo.lineIndex },
    )
}

private fun priorityRank(priority: TasksPriority?): Int {
    return when (priority) {
        TasksPriority.HIGHEST -> 5
        TasksPriority.HIGH -> 4
        TasksPriority.MEDIUM -> 3
        TasksPriority.LOW -> 2
        TasksPriority.LOWEST -> 1
        TasksPriority.NONE, null -> 0
    }
}
