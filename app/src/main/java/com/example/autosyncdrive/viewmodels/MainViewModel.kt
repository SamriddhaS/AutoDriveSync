package com.example.autosyncdrive.viewmodels

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.autosyncdrive.data.MainRepository
import com.example.autosyncdrive.data.localdb.FileInfo
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: MainRepository
):ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val TAG = "MainViewModel"

    init {
        checkSignInStatus()
        checkStorageDirectoryStatus()

        viewModelScope.launch {
            repository.observeFiles().collect { files ->
                Log.d(TAG, "Found ${files.size} files in the directory")

                // Log all files for debugging
                files.forEach { fileInfo ->
                    Log.d(TAG, "File: ${fileInfo.name}, Type: ${fileInfo.mimeType}, Size: ${fileInfo.size}")
                }

                _uiState.update {
                    it.copy(
                        storageState = it.storageState.copy(
                            files = files,
                            isLoading = false,
                            lastScanTime = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        // If a directory is already selected, scan it on startup
        if (repository.hasSelectedDirectory()) {
            scanSelectedDirectory()
        }
    }

    private fun checkSignInStatus() {
        val lastSignedInAccount = repository.getLastSignedInAccount()
        if (lastSignedInAccount != null) {
            _uiState.update { currentState ->
                currentState.copy(
                    googleDriveState = currentState.googleDriveState.copy(
                        isSignedIn = true,
                        account = lastSignedInAccount
                    )
                )
            }
        }
    }

    private fun checkStorageDirectoryStatus() {
        val hasDirectory = repository.hasSelectedDirectory()
        val directoryUri = repository.getSelectedDirectoryUri()

        _uiState.update { currentState ->
            currentState.copy(
                storageState = currentState.storageState.copy(
                    hasSelectedDirectory = hasDirectory,
                    directoryUri = directoryUri
                )
            )
        }
    }

    // GOOGLE DRIVE FUNCTIONS

    fun getSignInIntent(): Intent {
        return repository.getSignInIntent()
    }

    fun handleSignInResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            _uiState.update {
                it.copy(
                    googleDriveState = it.googleDriveState.copy(
                        isLoading = true,
                        error = null
                    )
                )
            }

            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = repository.handleSignInResult(task)

            if (account != null) {
                _uiState.update {
                    it.copy(
                        googleDriveState = it.googleDriveState.copy(
                            isSignedIn = true,
                            account = account,
                            isLoading = false
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        googleDriveState = it.googleDriveState.copy(
                            isLoading = false,
                            error = "Sign-in failed"
                        )
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    googleDriveState = it.googleDriveState.copy(
                        isLoading = false,
                        error = "Sign-in cancelled"
                    )
                )
            }
        }
    }

    fun uploadTestFile() {
        val currentAccount = _uiState.value.googleDriveState.account ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    googleDriveState = it.googleDriveState.copy(
                        isLoading = true,
                        uploadStatus = "Uploading..."
                    )
                )
            }

            repository.uploadFileToDrive(currentAccount)
                .onSuccess { fileId ->
                    _uiState.update {
                        it.copy(
                            googleDriveState = it.googleDriveState.copy(
                                uploadStatus = "File uploaded successfully! ID: $fileId",
                                isLoading = false,
                                error = null
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            googleDriveState = it.googleDriveState.copy(
                                uploadStatus = "Upload failed",
                                isLoading = false,
                                error = error.message
                            )
                        )
                    }
                }
        }
    }

    fun signOut() {
        repository.signOut()
        _uiState.update {
            it.copy(
                googleDriveState = GoogleDriveUiState() // Reset to default state
            )
        }
    }

    // NEW STORAGE DIRECTORY FUNCTIONS

    fun getDirectoryPickerIntent(): Intent {
        return repository.getDirectoryPickerIntent()
    }

    fun handleDirectoryPickerResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data

            _uiState.update {
                it.copy(
                    storageState = it.storageState.copy(
                        isLoading = true
                    )
                )
            }

            val success = repository.processDirectorySelection(uri)

            if (success) {
                _uiState.update {
                    it.copy(
                        storageState = it.storageState.copy(
                            hasSelectedDirectory = true,
                            directoryUri = uri,
                            isLoading = false,
                            error = null
                        )
                    )
                }

                // Scan the newly selected directory
                scanSelectedDirectory()
            } else {
                _uiState.update {
                    it.copy(
                        storageState = it.storageState.copy(
                            isLoading = false,
                            error = "Failed to process directory selection"
                        )
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    storageState = it.storageState.copy(
                        isLoading = false,
                        error = "Directory selection cancelled"
                    )
                )
            }
        }
    }

    /**
     * Scan the selected directory for files
     */
    fun scanSelectedDirectory() {

        if (!repository.hasSelectedDirectory()) {
            _uiState.update {
                it.copy(
                    storageState = it.storageState.copy(
                        error = "No directory selected"
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                storageState = it.storageState.copy(
                    isLoading = true,
                    error = null
                )
            )
        }

        viewModelScope.launch {
            repository.scanDirectory();
        }

    }
}

/**
 * Main UI State combining Google Drive and Storage states
 */
data class MainUiState(
    val googleDriveState: GoogleDriveUiState = GoogleDriveUiState(),
    val storageState: StorageUiState = StorageUiState()
)

/**
 * UI State for Google Drive operations
 */
data class GoogleDriveUiState(
    val isSignedIn: Boolean = false,
    val account: GoogleSignInAccount? = null,
    val uploadStatus: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * UI State for Storage operations
 */
data class StorageUiState(
    val hasSelectedDirectory: Boolean = false,
    val directoryUri: Uri? = null,
    val files: List<FileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastScanTime: Long? = null
)

class MainViewModelFactory(
    private val repository: MainRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}