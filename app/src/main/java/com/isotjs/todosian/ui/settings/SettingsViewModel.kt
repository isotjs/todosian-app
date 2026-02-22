package com.isotjs.todosian.ui.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StorageUiState(
    val folderUri: Uri? = null,
    val folderDisplayName: String? = null,
    val hasPersistedPermission: Boolean = false,
    val markdownFileCount: Int? = null,
    val isChecking: Boolean = false,
)

class SettingsViewModel(
    private val fileRepository: FileRepository,
) : ViewModel() {
    private val _storageState = MutableStateFlow(StorageUiState(isChecking = true))
    val storageState: StateFlow<StorageUiState> = _storageState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    init {
        refreshStorageStatus()
    }

    fun refreshStorageStatus() {
        viewModelScope.launch {
            val current = fileRepository.getFolderUri()
            if (current == null) {
                _storageState.value = StorageUiState(
                    folderUri = null,
                    folderDisplayName = null,
                    hasPersistedPermission = false,
                    markdownFileCount = null,
                    isChecking = false,
                )
                return@launch
            }

            val folderName = fileRepository.getFolderDisplayName(current).getOrNull()
            _storageState.value = _storageState.value.copy(
                folderUri = current,
                folderDisplayName = folderName,
                hasPersistedPermission = fileRepository.hasPersistedReadWritePermission(current),
                isChecking = true,
            )

            val count = fileRepository.countMarkdownFiles(current)
            _storageState.value = _storageState.value.copy(
                markdownFileCount = count.getOrNull(),
                isChecking = false,
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

            val count = fileRepository.countMarkdownFiles(uri)
            if (count.isFailure) {
                fileRepository.clearFolderUri()
                _events.emit(Event.ShowMessage(R.string.error_folder_invalid))
                _events.emit(Event.RequireOnboarding)
                return@launch
            }

            if (count.getOrThrow() <= 0) {
                fileRepository.clearFolderUri()
                _events.emit(Event.ShowMessage(R.string.error_folder_empty))
                _events.emit(Event.RequireOnboarding)
                return@launch
            }

            _events.emit(Event.ShowMessage(R.string.settings_folder_updated))
            refreshStorageStatus()
        }
    }

    fun resetFolder() {
        viewModelScope.launch {
            fileRepository.clearFolderUri()
            _events.emit(Event.RequireOnboarding)
        }
    }

    sealed interface Event {
        data class ShowMessage(@param:StringRes val messageResId: Int) : Event
        data object RequireOnboarding : Event
    }
}
