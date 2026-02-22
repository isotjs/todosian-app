package com.isotjs.todosian.ui.home

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.model.Category
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview

data class HomeUiState(
    val isLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
) {
    val remainingToday: Int = categories.sumOf { it.todoCount - it.doneCount }
}

class HomeViewModel(
    private val fileRepository: FileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    private val _dirtyVersion = MutableStateFlow(0L)
    val dirtyVersion: StateFlow<Long> = _dirtyVersion.asStateFlow()

    private var observeJob: Job? = null
    private var observedFolderUri: Uri? = null

    init {
        refresh()
    }

    private fun ensureFolderObserver(folderUri: Uri) {
        if (observedFolderUri == folderUri && observeJob?.isActive == true) return

        observeJob?.cancel()
        observedFolderUri = folderUri

        @OptIn(FlowPreview::class)
        observeJob = viewModelScope.launch {
            fileRepository.observeMarkdownFilesChanges(folderUri)
                .debounce(350)
                .catch {
                    // Thanks god app still works without live updates.
                }
                .collectLatest {
                    _dirtyVersion.value = System.currentTimeMillis()
                }
        }
    }

    fun refreshIfDirty() {
        val version = _dirtyVersion.value
        if (version <= 0L) return
        _dirtyVersion.value = 0L
        refresh(showLoading = false)
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            val folderUri = fileRepository.getFolderUri()
            if (folderUri == null) {
                _events.emit(Event.RequireOnboarding)
                _uiState.value = HomeUiState(isLoading = false)
                return@launch
            }

            ensureFolderObserver(folderUri)

            val shouldShowLoading = showLoading || _uiState.value.categories.isEmpty()
            if (shouldShowLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            val result = fileRepository.getCategories()
            if (result.isFailure) {
                fileRepository.clearFolderUri()
                _events.emit(Event.ShowMessage(R.string.error_folder_invalid))
                _events.emit(Event.RequireOnboarding)
                _uiState.value = HomeUiState(isLoading = false)
                return@launch
            }

            _uiState.value = HomeUiState(
                isLoading = false,
                categories = result.getOrThrow(),
            )
        }
    }

    fun changeFolder(uri: Uri) {
        viewModelScope.launch {
            val persisted = fileRepository.persistFolderUri(uri)
            if (persisted.isFailure) {
                fileRepository.clearFolderUri()
                _events.emit(Event.ShowMessage(R.string.error_folder_invalid))
                _events.emit(Event.RequireOnboarding)
                return@launch
            }
            refresh()
        }
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            val folderUri = fileRepository.getFolderUri() ?: run {
                _events.emit(Event.RequireOnboarding)
                return@launch
            }

            val created = fileRepository.createCategory(folderUri, name)
            if (created.isFailure) {
                _events.emit(Event.ShowMessage(R.string.error_write_failed))
                return@launch
            }
            refresh()
        }
    }

    fun renameCategory(categoryUri: Uri, newName: String) {
        viewModelScope.launch {
            val result = fileRepository.renameCategory(categoryUri, newName)
            if (result.isFailure) {
                _events.emit(Event.ShowMessage(R.string.error_write_failed))
                return@launch
            }
            refresh()
        }
    }

    fun deleteCategory(categoryUri: Uri) {
        viewModelScope.launch {
            val result = fileRepository.deleteCategory(categoryUri)
            if (result.isFailure) {
                _events.emit(Event.ShowMessage(R.string.error_write_failed))
                return@launch
            }
            refresh()
        }
    }

    sealed interface Event {
        data class ShowMessage(@param:StringRes val messageResId: Int) : Event
        data object RequireOnboarding : Event
    }
}
