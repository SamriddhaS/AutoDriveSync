package com.example.autosyncdrive.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.autosyncdrive.repositories.MainRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: MainRepository
):ViewModel() {

    private val _uiState = MutableStateFlow(GoogleDriveUiState())
    val uiState: StateFlow<GoogleDriveUiState> = _uiState.asStateFlow()

    init {
        checkSignInStatus()
    }

    private fun checkSignInStatus() {
        val lastSignedInAccount = repository.getLastSignedInAccount()
        if (lastSignedInAccount != null) {
            _uiState.update { currentState ->
                currentState.copy(
                    isSignedIn = true,
                    account = lastSignedInAccount
                )
            }
        }
    }

    fun getSignInIntent(): Intent {
        return repository.getSignInIntent()
    }

    fun handleSignInResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = repository.handleSignInResult(task)

            if (account != null) {
                _uiState.update {
                    it.copy(
                        isSignedIn = true,
                        account = account,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Sign-in failed"
                    )
                }
            }
        } else {
            _uiState.update { it.copy(isLoading = false, error = "Sign-in cancelled") }
        }
    }

    fun uploadTestFile() {
        val currentAccount = _uiState.value.account ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, uploadStatus = "Uploading...") }

            repository.uploadFileToDrive(currentAccount)
                .onSuccess { fileId ->
                    _uiState.update {
                        it.copy(
                            uploadStatus = "File uploaded successfully! ID: $fileId",
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            uploadStatus = "Upload failed",
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun signOut() {
        repository.signOut()
        _uiState.update {
            GoogleDriveUiState() // Reset to default state
        }
    }

}

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
