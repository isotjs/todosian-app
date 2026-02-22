package com.isotjs.todosian.ui.onboarding

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val fileRepository: FileRepository,
) : ViewModel() {
    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    fun onFolderChosen(uri: Uri) {
        viewModelScope.launch {
            val persisted = fileRepository.persistFolderUri(uri)
            if (persisted.isFailure) {
                fileRepository.clearFolderUri()
                _events.emit(Event.ShowMessage(R.string.error_folder_invalid))
                return@launch
            }

            val categories = fileRepository.getCategories()
            if (categories.isFailure) {
                fileRepository.clearFolderUri()
                _events.emit(Event.ShowMessage(R.string.error_folder_invalid))
                return@launch
            }

            if (categories.getOrThrow().isEmpty()) {
                fileRepository.clearFolderUri()
                _events.emit(Event.ShowMessage(R.string.error_folder_empty))
                return@launch
            }

            _events.emit(Event.NavigateHome)
        }
    }

    sealed interface Event {
        data class ShowMessage(@param:StringRes val messageResId: Int) : Event

        data object NavigateHome : Event
    }
}
