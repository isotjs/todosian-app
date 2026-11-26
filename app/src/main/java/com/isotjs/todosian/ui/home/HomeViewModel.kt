package com.isotjs.todosian.ui.home

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.PreferencesManager
import com.isotjs.todosian.data.model.Category
import com.isotjs.todosian.data.settings.NewTodoFilePosition
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.ui.components.awaitMinimumPullRefreshDuration
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val categories: List<Category> = emptyList(),
    val launchCategoryUri: Uri? = null,
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
    private val appSettingsRepository: AppSettingsRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    private var launchCategoryChecked = false

    init {
        refresh()
    }

    fun refresh(showLoading: Boolean = true, showRefreshing: Boolean = false) {
        if (showRefreshing && _uiState.value.isRefreshing) return
        viewModelScope.launch {
            val isLaunchRefresh = !launchCategoryChecked
            launchCategoryChecked = true
            val startedAt = SystemClock.elapsedRealtime()
            if (showRefreshing) {
                _uiState.value = _uiState.value.copy(isRefreshing = true)
            }
            try {
                val folderUri = fileRepository.getFolderUri()
                if (folderUri == null) {
                    _events.emit(Event.RequireOnboarding)
                    _uiState.value = HomeUiState(isLoading = false)
                    return@launch
                }

                val shouldShowLoading =
                    !showRefreshing && (showLoading || _uiState.value.categories.isEmpty())
                if (shouldShowLoading) {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
                val markers = appSettingsRepository.settings.first().toTaskMarkers()
                val result = fileRepository.getCategories(markers)
                if (result.isFailure) {
                    fileRepository.clearFolderUri()
                    _events.emit(Event.ShowMessage(R.string.error_folder_invalid))
                    _events.emit(Event.RequireOnboarding)
                    _uiState.value = HomeUiState(isLoading = false)
                    return@launch
                }

                val categories = result.getOrThrow()
                var launchCategoryUri: Uri? = null
                if (isLaunchRefresh) {
                    val configuredUri = preferencesManager.getDefaultLaunchCategoryUri()
                    if (configuredUri != null) {
                        if (categories.any { it.uri == configuredUri }) {
                            launchCategoryUri = configuredUri
                        } else {
                            preferencesManager.clearDefaultLaunchCategoryUri()
                        }
                    }
                }

                val refreshedState = _uiState.value.copy(
                    isLoading = false,
                    categories = categories,
                )
                _uiState.value = if (isLaunchRefresh) {
                    refreshedState.copy(launchCategoryUri = launchCategoryUri)
                } else {
                    refreshedState
                }
            } finally {
                if (showRefreshing) {
                    awaitMinimumPullRefreshDuration(startedAt)
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                }
            }
        }
    }

    fun consumeLaunchCategory() {
        if (_uiState.value.launchCategoryUri != null) {
            _uiState.value = _uiState.value.copy(launchCategoryUri = null)
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

    fun addQuickTodo(categoryUri: Uri, text: String) {
        val cleanedText = text.trim()
        if (cleanedText.isEmpty()) return

        viewModelScope.launch {
            val settings = appSettingsRepository.settings.first()
            val result = fileRepository.updateLines(categoryUri) { lines ->
                MarkdownParser.addTodo(
                    lines = lines,
                    text = cleanedText,
                    enableTasksPlugin = settings.enableTasksPluginSupport,
                    addAtStart = settings.newTodoFilePosition == NewTodoFilePosition.TOP,
                    markers = settings.toTaskMarkers(),
                )
            }
            if (result.isFailure) {
                _events.emit(Event.QuickTodoFailed)
                _events.emit(Event.ShowMessage(R.string.error_write_failed))
                return@launch
            }

            _events.emit(Event.QuickTodoAdded)
            refresh(showLoading = false)
        }
    }

    sealed interface Event {
        data class ShowMessage(@param:StringRes val messageResId: Int) : Event
        data object QuickTodoAdded : Event
        data object QuickTodoFailed : Event
        data object RequireOnboarding : Event
    }
}
