package com.isotjs.todosian.ui.onboarding

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.settings.AppSettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isValidatingFolder: Boolean = false,
    @param:StringRes val inlineMessageResId: Int? = null,
    val inlineMessageIsError: Boolean = false,
)

class OnboardingViewModel(
    private val fileRepository: FileRepository,
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    fun onFolderChosen(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState(isValidatingFolder = true)

            val persisted = fileRepository.persistFolderUri(uri)
            if (persisted.isFailure) {
                fileRepository.clearFolderUri()
                _uiState.value = OnboardingUiState(
                    isValidatingFolder = false,
                    inlineMessageResId = R.string.error_folder_invalid,
                    inlineMessageIsError = true,
                )
                return@launch
            }

            val categories = fileRepository.getCategories()
            if (categories.isFailure) {
                fileRepository.clearFolderUri()
                _uiState.value = OnboardingUiState(
                    isValidatingFolder = false,
                    inlineMessageResId = R.string.error_folder_invalid,
                    inlineMessageIsError = true,
                )
                return@launch
            }

            // With subfolders enabled, an empty top-level folder is acceptable: the user can
            // create notes anywhere in the tree later, so the ".md file required" rule is lifted.
            val includeSubfolders = appSettingsRepository.settings.first().includeSubfolders
            if (!includeSubfolders && categories.getOrThrow().isEmpty()) {
                fileRepository.clearFolderUri()
                _uiState.value = OnboardingUiState(
                    isValidatingFolder = false,
                    inlineMessageResId = R.string.error_folder_empty,
                    inlineMessageIsError = true,
                )
                return@launch
            }

            _uiState.value = OnboardingUiState()
            _events.emit(Event.NavigateHome)
        }
    }

    sealed interface Event {
        data object NavigateHome : Event
    }
}
