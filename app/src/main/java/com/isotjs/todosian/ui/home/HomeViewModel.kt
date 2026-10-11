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
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
) {
    fun remainingFor(mode: com.isotjs.todosian.data.settings.DailyFocusMode): Int {
        return when (mode) {
            com.isotjs.todosian.data.settings.DailyFocusMode.TODAY -> categories.sumOf { it.dueTodayCount }
            com.isotjs.todosian.data.settings.DailyFocusMode.OVERDUE -> categories.sumOf { it.overdueCount }
            com.isotjs.todosian.data.settings.DailyFocusMode.TODAY_AND_OVERDUE ->
                categories.sumOf { it.dueTodayCount + it.overdueCount }
        }
    }
}

class HomeViewModel(
    private val fileRepository: FileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            val folderUri = fileRepository.getFolderUri()
            if (folderUri == null) {
                _events.emit(Event.RequireOnboarding)
                _uiState.value = HomeUiState(isLoading = false)
                return@launch
            }

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
