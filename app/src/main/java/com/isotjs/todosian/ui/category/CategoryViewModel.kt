package com.isotjs.todosian.ui.category

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class CategoryUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val activeTodos: List<Todo> = emptyList(),
    val completedTodos: List<Todo> = emptyList(),
    val lines: List<String> = emptyList(),
)

class CategoryViewModel(
    private val fileRepository: FileRepository,
    private val categoryUri: Uri,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState(isLoading = true))
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    private val inFlightWrites = AtomicInteger(0)
    private val pendingRefreshFromObserver = AtomicBoolean(false)

    init {
        observeExternalChanges()
        refreshFromDisk(showLoading = true)
    }

    private fun observeExternalChanges() {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            fileRepository.observeDocumentChanges(categoryUri)
                .debounce(250)
                .catch {
                    // TODO: Pull to refresh.
                }
                .collectLatest {
                    if (inFlightWrites.get() > 0) {
                        pendingRefreshFromObserver.set(true)
                        return@collectLatest
                    }
                    refreshFromDisk(showLoading = false)
                }
        }
    }

    fun load() {
        refreshFromDisk(showLoading = true)
    }

    fun refreshFromDisk(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            val nameResult = fileRepository.getDisplayName(categoryUri)
            val title = nameResult.getOrNull()?.removeSuffix(".md").orEmpty()

            val linesResult = fileRepository.readLines(categoryUri)
            if (linesResult.isFailure) {
                _events.emit(Event.ShowMessage(R.string.error_read_failed))
                _uiState.value = _uiState.value.copy(isLoading = false, title = title)
                return@launch
            }

            val lines = linesResult.getOrThrow()
            if (!showLoading && lines == _uiState.value.lines) return@launch

            val todos = MarkdownParser.parse(lines)
            val (completed, active) = todos.partition { it.isDone }

            _uiState.value = CategoryUiState(
                isLoading = false,
                title = title,
                activeTodos = active.sortedBy { it.lineIndex },
                completedTodos = completed.sortedBy { it.lineIndex },
                lines = lines,
            )
        }
    }

    fun toggleTodo(todo: Todo, enableTasksPluginSupport: Boolean) {
        viewModelScope.launch {
            val previousLines = _uiState.value.lines
            val newLines = MarkdownParser.tryToggleLine(
                lines = previousLines,
                lineIndex = todo.lineIndex,
                enableTasksPlugin = enableTasksPluginSupport,
            )
            if (newLines == null) {
                _events.emit(Event.ShowMessage(R.string.error_read_failed))
                refreshFromDisk(showLoading = false)
                return@launch
            }

            applyLines(newLines)

            inFlightWrites.incrementAndGet()
            try {
                val write = fileRepository.writeLines(categoryUri, newLines)
                if (write.isFailure) {
                    if (_uiState.value.lines == newLines) {
                        applyLines(previousLines)
                    }
                    _events.emit(Event.ShowMessage(R.string.error_write_failed))
                }
            } finally {
                onWriteFinishedMaybeRefresh()
            }
        }
    }

    fun addTodo(
        text: String,
        meta: MarkdownParser.TasksMeta?,
        enableTasksPluginSupport: Boolean,
    ) {
        viewModelScope.launch {
            val previousLines = _uiState.value.lines
            val newLines = MarkdownParser.addTodo(
                lines = previousLines,
                text = text,
                meta = meta,
                enableTasksPlugin = enableTasksPluginSupport,
            )
            if (newLines == previousLines) return@launch

            applyLines(newLines)

            inFlightWrites.incrementAndGet()
            try {
                val write = fileRepository.writeLines(categoryUri, newLines)
                if (write.isFailure) {
                    if (_uiState.value.lines == newLines) {
                        applyLines(previousLines)
                    }
                    _events.emit(Event.ShowMessage(R.string.error_write_failed))
                }
            } finally {
                onWriteFinishedMaybeRefresh()
            }
        }
    }

    fun editTodo(
        todo: Todo,
        newText: String,
        meta: MarkdownParser.TasksMeta?,
        enableTasksPluginSupport: Boolean,
    ) {
        viewModelScope.launch {
            val previousLines = _uiState.value.lines
            val newLines = if (enableTasksPluginSupport) {
                MarkdownParser.editTodo(
                    lines = previousLines,
                    lineIndex = todo.lineIndex,
                    newText = newText,
                    meta = meta,
                    enableTasksPlugin = true,
                )
            } else {
                val updated = MarkdownParser.tryEditTodoText(previousLines, todo.lineIndex, newText)
                if (updated == null) {
                    _events.emit(Event.ShowMessage(R.string.error_read_failed))
                    refreshFromDisk(showLoading = false)
                    return@launch
                }
                updated
            }

            applyLines(newLines)

            inFlightWrites.incrementAndGet()
            try {
                val write = fileRepository.writeLines(categoryUri, newLines)
                if (write.isFailure) {
                    if (_uiState.value.lines == newLines) {
                        applyLines(previousLines)
                    }
                    _events.emit(Event.ShowMessage(R.string.error_write_failed))
                }
            } finally {
                onWriteFinishedMaybeRefresh()
            }
        }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch {
            val previousLines = _uiState.value.lines
            val newLines = MarkdownParser.tryDeleteTodo(previousLines, todo.lineIndex)
            if (newLines == null) {
                _events.emit(Event.ShowMessage(R.string.error_read_failed))
                refreshFromDisk(showLoading = false)
                return@launch
            }

            applyLines(newLines)

            inFlightWrites.incrementAndGet()
            try {
                val write = fileRepository.writeLines(categoryUri, newLines)
                if (write.isFailure) {
                    if (_uiState.value.lines == newLines) {
                        applyLines(previousLines)
                    }
                    _events.emit(Event.ShowMessage(R.string.error_write_failed))
                }
            } finally {
                onWriteFinishedMaybeRefresh()
            }
        }
    }

    private fun onWriteFinishedMaybeRefresh() {
        val remaining = inFlightWrites.decrementAndGet().coerceAtLeast(0)
        if (remaining == 0 && pendingRefreshFromObserver.getAndSet(false)) {
            refreshFromDisk(showLoading = false)
        }
    }

    private fun applyLines(lines: List<String>) {
        val todos = MarkdownParser.parse(lines)
        val (completed, active) = todos.partition { it.isDone }
        _uiState.value = _uiState.value.copy(
            activeTodos = active.sortedBy { it.lineIndex },
            completedTodos = completed.sortedBy { it.lineIndex },
            lines = lines,
        )
    }

    sealed interface Event {
        data class ShowMessage(@param:StringRes val messageResId: Int) : Event
    }
}
